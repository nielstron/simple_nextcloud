package de.nielstron.simplenextcloud

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.nielstron.simplenextcloud.data.Account
import de.nielstron.simplenextcloud.data.AccountStore
import de.nielstron.simplenextcloud.data.CloudFile
import de.nielstron.simplenextcloud.data.ExistingShare
import de.nielstron.simplenextcloud.data.FolderListingCache
import de.nielstron.simplenextcloud.data.LinkShareOptions
import de.nielstron.simplenextcloud.data.ImagePreviewCache
import de.nielstron.simplenextcloud.data.NextcloudClient
import de.nielstron.simplenextcloud.data.NextcloudException
import de.nielstron.simplenextcloud.data.NextcloudPath
import de.nielstron.simplenextcloud.data.ShareUser
import de.nielstron.simplenextcloud.data.ShareHistoryStore
import de.nielstron.simplenextcloud.data.persistentFolderListingCache
import de.nielstron.simplenextcloud.data.ShareUpdate
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class FileUiState(
    val account: Account? = null,
    val path: String = "",
    val files: List<CloudFile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val shareUrl: String? = null,
    val shareUsers: List<ShareUser> = emptyList(),
    val shareUsersLoading: Boolean = false,
    val shareUsersError: String? = null,
    val frequentShareUsers: List<ShareUser> = emptyList(),
    val shares: List<ExistingShare> = emptyList(),
    val sharesLoading: Boolean = false,
    val sharesError: String? = null,
    val shareOperationLoading: Boolean = false,
    val shareOperationError: String? = null,
    val previewFile: CloudFile? = null,
    val previewBytes: ByteArray? = null,
    val previewLoading: Boolean = false,
    val previewError: String? = null,
    val downloadedUri: Uri? = null,
    val downloadedMimeType: String? = null,
    val localOpenUri: Uri? = null,
    val localOpenMimeType: String? = null,
    val clipboardFiles: List<CloudFile> = emptyList(),
    val clipboardMode: ClipboardMode? = null,
    val uploadQueue: List<UploadQueueItem> = emptyList(),
    val highlightedPath: String? = null,
    val loginUrl: String? = null,
    val loginWaiting: Boolean = false,
)

enum class ClipboardMode { COPY, MOVE }
enum class UploadStatus { QUEUED, UPLOADING, COMPLETED, FAILED }
data class UploadQueueItem(
    val id: Long,
    val name: String,
    val targetPath: String,
    val isFolder: Boolean,
    val status: UploadStatus = UploadStatus.QUEUED,
    val error: String? = null,
)

class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AccountStore(application)
    private val client = NextcloudClient()
    private val shareHistory = ShareHistoryStore(application)
    private val initialAccount = store.load()
    private var folderCache = initialAccount?.let {
        persistentFolderListingCache(application, it)
    } ?: FolderListingCache()
    private var imagePreviewCache = initialAccount?.let { ImagePreviewCache.forAccount(application, it) }
    private var navigationJob: Job? = null
    private var prefetchJob: Job? = null
    private var shareUserSearchJob: Job? = null
    private var previewJob: Job? = null
    private var imagePrefetches: List<ImagePrefetch> = emptyList()
    private var uploadWorkerJob: Job? = null
    private var loginJob: Job? = null
    private var nextUploadId = 1L
    private val mutationMutex = Mutex()
    private var pendingMutationCount = 0
    private val uploadJobs = ArrayDeque<UploadJob>()
    private val _state = MutableStateFlow(FileUiState(account = initialAccount))
    val state = _state.asStateFlow()

    init {
        if (_state.value.account != null) {
            refresh()
        } else {
            store.loadPendingLogin()?.let { pendingLogin ->
                _state.update { it.copy(loginWaiting = true) }
                pollLogin(pendingLogin)
            }
        }
    }

    fun startBrowserLogin(server: String) {
        beginBrowserLogin(server)
    }

    fun localNetworkPermissionDenied() {
        _state.update { it.copy(error = "Allow local-network access to connect to this Nextcloud server") }
    }

    fun restartBrowserLogin() {
        val pendingLogin = store.loadPendingLogin()
        if (pendingLogin == null) {
            _state.update {
                it.copy(
                    loginWaiting = false,
                    error = "That login request expired. This is normal after an interruption. " +
                        "Enter your Nextcloud address and try again.",
                )
            }
            return
        }
        beginBrowserLogin(NextcloudPath.serverRoot(pendingLogin.session.loginUrl))
    }

    private fun beginBrowserLogin(server: String) {
        val serverUrl = NextcloudPath.normalizeServerUrl(server)
        if (!serverUrl.startsWith("https://")) {
            _state.update { it.copy(error = "Use an HTTPS Nextcloud address") }
            return
        }
        loginJob?.cancel()
        store.clearPendingLogin()
        _state.update { it.copy(loading = true, loginWaiting = false, error = null) }
        loginJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.initiateLogin(serverUrl) } }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, loginWaiting = false, error = failure.userMessage()) }
                }
                .onSuccess { session ->
                    val pendingLogin = store.savePendingLogin(session)
                    _state.update { it.copy(loading = false, loginWaiting = true, loginUrl = session.loginUrl) }
                    pollLoginLoop(pendingLogin)
                }
        }
    }

    private fun pollLogin(pendingLogin: de.nielstron.simplenextcloud.data.PendingLogin) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch { pollLoginLoop(pendingLogin) }
    }

    private suspend fun pollLoginLoop(pendingLogin: de.nielstron.simplenextcloud.data.PendingLogin) {
        while (System.currentTimeMillis() < pendingLogin.expiresAtMillis) {
            delay(1_000)
            if (System.currentTimeMillis() >= pendingLogin.expiresAtMillis) break
            val result = runCatching {
                withContext(Dispatchers.IO) { client.pollLogin(pendingLogin.session) }
            }
            val account = result.getOrNull()
            if (account != null) {
                store.save(account)
                store.clearPendingLogin()
                notifyDocumentRoots()
                _state.update { it.copy(account = account, loginWaiting = false, loginUrl = null) }
                finishLogin(account)
                return
            }
            val failure = result.exceptionOrNull()
            if (failure != null) {
                _state.update {
                    it.copy(
                        error = "Browser approval is still pending. Temporary failures can be normal; " +
                            "tap Restart login to retry. Details: ${failure.userMessage()}",
                    )
                }
                delay(2_000)
            }
        }
        store.clearPendingLogin()
        _state.update {
            it.copy(
                loginWaiting = false,
                error = "Login approval expired. This is normal after an interruption. " +
                    "Enter your Nextcloud address and try again.",
            )
        }
    }

    private suspend fun finishLogin(account: Account) {
        folderCache = persistentFolderListingCache(getApplication(), account)
        imagePreviewCache = ImagePreviewCache.forAccount(getApplication(), account)
        _state.update { it.copy(loading = true, loginWaiting = false, error = null) }
        runCatching { withContext(Dispatchers.IO) { client.list(account, "") } }
                .onSuccess { files ->
                    val sorted = files.sortedFiles()
                    folderCache.put("", sorted)
                    folderCache.recordVisit("")
                    _state.update { it.copy(files = sorted, loading = false) }
                    schedulePrefetch(account, sorted)
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.userMessage()) }
                }
    }

    fun consumeLoginUrl() = _state.update { it.copy(loginUrl = null) }

    fun cancelBrowserLogin() {
        loginJob?.cancel()
        store.clearPendingLogin()
        _state.update { it.copy(loading = false, loginWaiting = false, loginUrl = null) }
    }

    fun disconnect() {
        loginJob?.cancel()
        navigationJob?.cancel()
        prefetchJob?.cancel()
        imagePrefetches.forEach { it.bytes.cancel() }
        uploadWorkerJob?.cancel()
        uploadJobs.clear()
        folderCache.clear()
        imagePreviewCache?.clear()
        imagePreviewCache = null
        store.clear()
        notifyDocumentRoots()
        _state.value = FileUiState()
    }

    private fun notifyDocumentRoots() {
        getApplication<Application>().contentResolver.notifyChange(
            DocumentsContract.buildRootsUri("${BuildConfig.APPLICATION_ID}.documents"),
            null,
        )
    }

    fun open(folder: CloudFile) {
        clearHighlight()
        loadPath(folder.path)
    }

    fun up() {
        val child = _state.value.path
        val parent = child.substringBeforeLast('/', "")
        loadPath(parent)
        _state.update { it.copy(highlightedPath = child) }
    }

    fun loadPath(path: String) {
        val account = _state.value.account ?: return
        val normalizedPath = path.trim('/')
        prefetchJob?.cancel()
        val previousPath = _state.value.path
        val previousFiles = _state.value.files
        val cached = folderCache.get(normalizedPath)
        if (cached == null) folderCache.recordVisit(normalizedPath)
        _state.update {
            it.copy(
                path = normalizedPath,
                files = cached?.files ?: emptyList(),
                loading = cached == null,
                error = null,
            )
        }
        cached?.let { schedulePrefetch(account, it.files) }
        navigationJob?.cancel()
        navigationJob = viewModelScope.launch {
            runCatching { client.listCancellable(account, normalizedPath) }
                .onSuccess { files ->
                    val sorted = files.sortedFiles()
                    folderCache.put(normalizedPath, sorted)
                    if (_state.value.path == normalizedPath) {
                        _state.update { it.copy(files = sorted, loading = false) }
                        if (cached?.files != sorted) schedulePrefetch(account, sorted)
                    }
                }
                .onFailure { failure ->
                    if (_state.value.path == normalizedPath) {
                        _state.update {
                            it.copy(
                                path = if (cached == null) previousPath else normalizedPath,
                                files = cached?.files ?: previousFiles,
                                loading = false,
                                error = failure.userMessage(),
                            )
                        }
                    }
                }
        }
    }

    fun refresh() = loadPath(_state.value.path)

    fun enqueueFiles(resolver: ContentResolver, uris: List<Uri>) {
        val targetFolder = _state.value.path
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val details = resolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getString(0) to cursor.getLong(1)
                    } ?: error("The selected document has no metadata")
                    details.first to UploadSource.File(uri, details.second, resolver.getType(uri))
                }
            }
            val jobs = sources.map { (name, source) ->
                UploadJob(UploadQueueItem(nextUploadId++, name, targetFolder, false), source)
            }
            addUploadJobs(jobs)
        }
    }

    fun enqueueFolder(resolver: ContentResolver, treeUri: Uri) {
        val targetFolder = _state.value.path
        val folder = requireNotNull(DocumentFile.fromTreeUri(getApplication(), treeUri))
        val name = requireNotNull(folder.name) { "The selected folder has no name" }
        addUploadJobs(
            listOf(
                UploadJob(
                    item = UploadQueueItem(nextUploadId++, name, targetFolder, true),
                    source = UploadSource.Folder(treeUri),
                ),
            ),
        )
    }

    fun clearFinishedUploads() {
        _state.update {
            it.copy(uploadQueue = it.uploadQueue.filter { item -> item.status in setOf(UploadStatus.QUEUED, UploadStatus.UPLOADING) })
        }
    }

    fun navigateToUpload(item: UploadQueueItem) {
        val highlightedPath = NextcloudPath.child(item.targetPath, item.name)
        loadPath(item.targetPath)
        _state.update { it.copy(highlightedPath = highlightedPath) }
    }

    fun clearHighlight() = _state.update { it.copy(highlightedPath = null) }

    fun download(resolver: ContentResolver, file: CloudFile, uri: Uri) {
        val account = _state.value.account ?: return
        _state.update { it.copy(loading = true, error = null, downloadedUri = null, downloadedMimeType = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.download(account, file, resolver, uri) } }
                .onSuccess {
                    _state.update {
                        it.copy(
                            loading = false,
                            message = "${file.name} downloaded",
                            downloadedUri = uri,
                            downloadedMimeType = if (file.isFolder) "application/zip" else file.mimeType,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.userMessage()) }
                }
        }
    }

    fun openLocally(file: CloudFile) {
        val account = _state.value.account ?: return
        val application = getApplication<Application>()
        _state.update { it.copy(loading = true, error = null, localOpenUri = null, localOpenMimeType = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val directory = File(application.cacheDir, "opened-files").apply { mkdirs() }
                    val localFile = File(directory, file.name)
                    localFile.outputStream().use { output -> client.download(account, file, output) }
                    FileProvider.getUriForFile(application, "${BuildConfig.APPLICATION_ID}.files", localFile)
                }
            }.onSuccess { uri ->
                _state.update {
                    it.copy(
                        loading = false,
                        localOpenUri = uri,
                        localOpenMimeType = file.mimeType ?: "application/octet-stream",
                    )
                }
            }.onFailure { failure ->
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    fun clearLocalOpen() = _state.update { it.copy(localOpenUri = null, localOpenMimeType = null) }

    fun share(file: CloudFile, user: ShareUser, permissions: Int) {
        _state.update { it.copy(shareOperationLoading = true, shareOperationError = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.share(requireNotNull(_state.value.account), file.path, user.id, permissions)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        shareOperationLoading = false,
                        message = "Shared with ${user.displayName}",
                        shareUrl = result.url,
                    )
                }
                val account = requireNotNull(_state.value.account)
                shareHistory.record(account, user)
                loadFrequentShareUsers(file.isFolder)
                loadShares(file)
            }.onFailure { failure ->
                _state.update { it.copy(shareOperationLoading = false, shareOperationError = failure.userMessage()) }
            }
        }
    }

    fun createLink(file: CloudFile, options: LinkShareOptions) {
        _state.update { it.copy(shareOperationLoading = true, shareOperationError = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.createLink(requireNotNull(_state.value.account), file.path, options)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(shareOperationLoading = false, message = "Public link copied", shareUrl = result.url)
                }
                loadShares(file)
            }.onFailure { failure ->
                _state.update { it.copy(shareOperationLoading = false, shareOperationError = failure.userMessage()) }
            }
        }
    }

    fun loadShares(file: CloudFile) {
        val account = _state.value.account ?: return
        _state.update { it.copy(sharesLoading = true, sharesError = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.listShares(account, file.path) } }
                .onSuccess { shares -> _state.update { it.copy(shares = shares, sharesLoading = false) } }
                .onFailure { failure ->
                    _state.update { it.copy(shares = emptyList(), sharesLoading = false, sharesError = failure.userMessage()) }
                }
        }
    }

    fun updateShare(file: CloudFile, share: ExistingShare, update: ShareUpdate) {
        val account = _state.value.account ?: return
        _state.update { it.copy(shareOperationLoading = true, shareOperationError = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.updateShare(account, share.id, update) } }
                .onSuccess {
                    _state.update { it.copy(shareOperationLoading = false, message = "Share updated") }
                    loadShares(file)
                }
                .onFailure { failure ->
                    _state.update { it.copy(shareOperationLoading = false, shareOperationError = failure.userMessage()) }
                }
        }
    }

    fun deleteShare(file: CloudFile, share: ExistingShare) {
        val account = _state.value.account ?: return
        _state.update { it.copy(shareOperationLoading = true, shareOperationError = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.deleteShare(account, share.id) } }
                .onSuccess {
                    _state.update { it.copy(shareOperationLoading = false, message = "Share removed") }
                    loadShares(file)
                }
                .onFailure { failure ->
                    _state.update { it.copy(shareOperationLoading = false, shareOperationError = failure.userMessage()) }
                }
        }
    }

    fun searchShareUsers(query: String, isFolder: Boolean) {
        val account = _state.value.account ?: return
        shareUserSearchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(shareUsers = emptyList(), shareUsersLoading = false, shareUsersError = null) }
            return
        }
        _state.update { it.copy(shareUsersLoading = true, shareUsersError = null) }
        shareUserSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(USER_SEARCH_DEBOUNCE_MILLIS)
            runCatching {
                withContext(Dispatchers.IO) {
                    client.searchUsers(account, query.trim(), if (isFolder) "folder" else "file")
                }
            }.onSuccess { users ->
                _state.update { it.copy(shareUsers = users, shareUsersLoading = false) }
            }.onFailure { failure ->
                _state.update {
                    it.copy(shareUsers = emptyList(), shareUsersLoading = false, shareUsersError = failure.userMessage())
                }
            }
        }
    }

    fun loadFrequentShareUsers(isFolder: Boolean) {
        val account = _state.value.account ?: return
        _state.update { it.copy(frequentShareUsers = shareHistory.frequent(account)) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val recommended = client.recommendedUsers(account, if (isFolder) "folder" else "file")
                    shareHistory.seed(account, recommended)
                    shareHistory.frequent(account)
                }
            }.onSuccess { users -> _state.update { it.copy(frequentShareUsers = users) } }
        }
    }

    fun clearShareUsers() {
        shareUserSearchJob?.cancel()
        _state.update { it.copy(shareUsers = emptyList(), shareUsersLoading = false, shareUsersError = null) }
    }

    fun clearShareState() {
        clearShareUsers()
        _state.update {
            it.copy(
                shares = emptyList(),
                sharesLoading = false,
                sharesError = null,
                shareOperationLoading = false,
                shareOperationError = null,
                frequentShareUsers = emptyList(),
            )
        }
    }

    fun showPreview(file: CloudFile) {
        val account = _state.value.account ?: return
        previewJob?.cancel()
        if (file.mimeType?.startsWith("video/") == true) {
            _state.update {
                it.copy(previewFile = file, previewBytes = null, previewLoading = false, previewError = null)
            }
            prefetchAdjacentImages(account, file)
            return
        }
        val adoptedPrefetch = imagePrefetches
            .firstOrNull { it.file.samePreviewVersion(file) }
            ?.bytes
        if (adoptedPrefetch == null) {
            imagePrefetches.forEach { it.bytes.cancel() }
            imagePrefetches = emptyList()
        }
        _state.update {
            it.copy(previewFile = file, previewBytes = null, previewLoading = true, previewError = null)
        }
        previewJob = viewModelScope.launch {
            runCatching {
                if (adoptedPrefetch != null) {
                    val bytes = adoptedPrefetch.await()
                    withContext(Dispatchers.IO) {
                        imagePreviewCache?.get(file) ?: bytes.also { imagePreviewCache?.put(file, it) }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        imagePreviewCache?.get(file) ?: client.preview(account, file).also { bytes ->
                            imagePreviewCache?.put(file, bytes)
                        }
                    }
                }
            }
                .onSuccess { bytes ->
                    if (_state.value.previewFile?.path == file.path) {
                        _state.update { it.copy(previewBytes = bytes, previewLoading = false) }
                        prefetchAdjacentImages(account, file)
                    }
                }
                .onFailure { failure ->
                    if (_state.value.previewFile?.path == file.path) {
                        _state.update { it.copy(previewLoading = false, previewError = failure.userMessage()) }
                    }
                }
        }
    }

    fun closePreview() {
        previewJob?.cancel()
        imagePrefetches.forEach { it.bytes.cancel() }
        imagePrefetches = emptyList()
        _state.update {
            it.copy(previewFile = null, previewBytes = null, previewLoading = false, previewError = null)
        }
    }

    private fun prefetchAdjacentImages(account: Account, current: CloudFile) {
        val targets = adjacentPreviewFiles(_state.value.files, current)
            .filter { it.mimeType?.startsWith("image/") == true }
        imagePrefetches
            .filterNot { existing -> targets.any(existing.file::samePreviewVersion) }
            .forEach { it.bytes.cancel() }
        imagePrefetches = targets.map { target ->
            imagePrefetches.firstOrNull { it.file.samePreviewVersion(target) }
                ?: ImagePrefetch(
                    target,
                    viewModelScope.async(Dispatchers.IO) {
                        imagePreviewCache?.peek(target) ?: client.preview(account, target)
                    },
                )
        }
    }

    fun delete(file: CloudFile) = mutateAndRefresh(
        message = "${file.name} deleted",
        stateOnSuccess = { state ->
            if (state.previewFile?.path == file.path) state.withoutPreview() else state
        },
    ) { account -> client.delete(account, file) }

    fun delete(files: List<CloudFile>) = mutateFiles(files, "deleted") { account, file ->
        client.delete(account, file)
    }

    fun rename(file: CloudFile, newName: String) = mutateAndRefresh(
        message = "Renamed to $newName",
        failureMessage = { renameFailureMessage(newName, it) },
        stateOnSuccess = { state ->
            if (state.previewFile?.path == file.path) {
                state.copy(previewFile = file.renamedTo(newName))
            } else {
                state
            }
        },
    ) { account -> client.rename(account, file, newName) }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && '/' !in trimmed) { "Enter a folder name without slashes" }
        val target = NextcloudPath.child(_state.value.path, trimmed)
        mutateAndRefresh("$trimmed created") { account -> client.createFolder(account, target) }
    }

    fun stageTransfer(file: CloudFile, mode: ClipboardMode) = stageTransfer(listOf(file), mode)

    fun stageTransfer(files: List<CloudFile>, mode: ClipboardMode) {
        if (files.isEmpty()) return
        _state.update {
            it.copy(
                clipboardFiles = files.distinctBy(CloudFile::path),
                clipboardMode = mode,
                message = "${files.size} item(s) ready to ${if (mode == ClipboardMode.COPY) "copy" else "move"}",
            )
        }
    }

    fun clearClipboard() = _state.update { it.copy(clipboardFiles = emptyList(), clipboardMode = null) }

    fun paste() {
        if (_state.value.loading) return
        val files = _state.value.clipboardFiles
        if (files.isEmpty()) return
        val mode = _state.value.clipboardMode ?: return
        val destination = _state.value.path
        if (!canPasteFiles(files, destination)) return
        mutateFiles(
            files = files,
            verb = if (mode == ClipboardMode.COPY) "copied" else "moved",
            onCompleted = { result ->
                _state.update { state ->
                    if (state.clipboardFiles == files && state.clipboardMode == mode) {
                        val remaining = result.failures.map { it.first }
                        state.copy(clipboardFiles = remaining, clipboardMode = mode.takeIf { remaining.isNotEmpty() })
                    } else state
                }
            },
        ) { account, file ->
            if (mode == ClipboardMode.COPY) client.copy(account, file, destination)
            else client.move(account, file, destination)
        }
    }

    private fun mutateFiles(
        files: List<CloudFile>,
        verb: String,
        onCompleted: (FileBatchResult) -> Unit = {},
        mutation: (Account, CloudFile) -> Unit,
    ) {
        if (files.isEmpty()) return
        val currentPath = _state.value.path
        var result = FileBatchResult(emptyList(), emptyList())
        mutateAndRefresh(
            message = "${files.size} item(s) $verb",
            stateOnSuccess = { state ->
                val updated = if (result.completed.any { it.path == state.previewFile?.path }) state.withoutPreview() else state
                if (result.failures.isEmpty()) updated else updated.copy(
                    message = null,
                    error = "${result.completed.size} of ${files.size} item(s) $verb.\n\n" +
                        result.failures.joinToString("\n") { (file, failure) -> "${file.name}: ${failure.userMessage()}" },
                )
            },
        ) { account ->
            folderCache.remove(currentPath)
            result = performFileBatch(files) { mutation(account, it) }
            result.completed.forEach { file ->
                folderCache.remove(file.path.substringBeforeLast('/', ""))
                if (file.isFolder) folderCache.remove(file.path)
            }
            onCompleted(result)
        }
    }

    fun clearNotice() = _state.update {
        it.copy(
            error = null,
            message = null,
            shareUrl = null,
            downloadedUri = null,
            downloadedMimeType = null,
        )
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearSuccessNotice() = _state.update {
        it.copy(
            message = null,
            shareUrl = null,
            downloadedUri = null,
            downloadedMimeType = null,
        )
    }

    private fun mutateAndRefresh(
        message: String,
        onSuccess: () -> Unit = {},
        failureMessage: (Throwable) -> String = Throwable::userMessage,
        stateOnSuccess: (FileUiState) -> FileUiState = { it },
        mutation: (Account) -> Unit,
    ) {
        val account = _state.value.account ?: return
        val currentPath = _state.value.path
        pendingMutationCount += 1
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            mutationMutex.withLock {
                runCatching {
                    withContext(Dispatchers.IO) {
                        mutation(account)
                        client.list(account, currentPath)
                    }
                }.onSuccess { files ->
                    val sorted = files.sortedFiles()
                    folderCache.put(currentPath, sorted)
                    _state.update { state ->
                        stateOnSuccess(
                            state.copy(
                                files = if (state.path == currentPath) sorted else state.files,
                                message = message,
                            ),
                        )
                    }
                    onSuccess()
                    schedulePrefetch(account, sorted)
                }.onFailure { failure ->
                    _state.update { it.copy(error = failureMessage(failure)) }
                }
            }
            pendingMutationCount -= 1
            _state.update { it.copy(loading = pendingMutationCount > 0) }
        }
    }

    private fun addUploadJobs(jobs: List<UploadJob>) {
        uploadJobs.addAll(jobs)
        _state.update { it.copy(uploadQueue = it.uploadQueue + jobs.map(UploadJob::item)) }
        startUploadWorker()
    }

    private fun startUploadWorker() {
        if (uploadWorkerJob?.isActive == true) return
        val account = _state.value.account ?: return
        uploadWorkerJob = viewModelScope.launch {
            while (uploadJobs.isNotEmpty()) {
                val job = uploadJobs.removeFirst()
                updateUpload(job.item.id, UploadStatus.UPLOADING)
                runCatching {
                    withContext(Dispatchers.IO) {
                        when (val source = job.source) {
                            is UploadSource.File -> client.upload(
                                account,
                                NextcloudPath.child(job.item.targetPath, job.item.name),
                                getApplication<Application>().contentResolver,
                                source.uri,
                                source.size,
                                source.mimeType,
                            )
                            is UploadSource.Folder -> uploadFolder(
                                account,
                                getApplication<Application>().contentResolver,
                                requireNotNull(DocumentFile.fromTreeUri(getApplication(), source.uri)),
                                NextcloudPath.child(job.item.targetPath, job.item.name),
                            )
                        }
                    }
                }.onSuccess {
                    updateUpload(job.item.id, UploadStatus.COMPLETED)
                    if (_state.value.path == job.item.targetPath) refresh()
                }.onFailure { failure ->
                    updateUpload(job.item.id, UploadStatus.FAILED, failure.userMessage())
                }
            }
        }
    }

    private fun uploadFolder(
        account: Account,
        resolver: ContentResolver,
        folder: DocumentFile,
        targetPath: String,
    ) {
        client.createFolder(account, targetPath)
        folder.listFiles().forEach { child ->
            val name = requireNotNull(child.name) { "An upload item has no name" }
            val childTarget = NextcloudPath.child(targetPath, name)
            if (child.isDirectory) uploadFolder(account, resolver, child, childTarget)
            else client.upload(account, childTarget, resolver, child.uri, child.length(), child.type)
        }
    }

    private fun updateUpload(id: Long, status: UploadStatus, error: String? = null) {
        _state.update {
            it.copy(
                uploadQueue = it.uploadQueue.map { item ->
                    if (item.id == id) item.copy(status = status, error = error) else item
                },
            )
        }
    }

    /** Fetches every child directory listing concurrently. Only PROPFIND metadata is requested. */
    private fun schedulePrefetch(account: Account, visibleFiles: List<CloudFile>) {
        prefetchJob?.cancel()
        val folders = foldersToPrefetch(visibleFiles)
        prefetchJob = viewModelScope.launch {
            coroutineScope {
                folders.forEach { folderPath ->
                    launch {
                        runCatching { client.listCancellable(account, folderPath) }
                            .onSuccess { files -> folderCache.put(folderPath, files.sortedFiles()) }
                    }
                }
            }
        }
    }

    private companion object {
        const val USER_SEARCH_DEBOUNCE_MILLIS = 250L
    }
}

private fun List<CloudFile>.sortedFiles() = sortedWith(compareByDescending<CloudFile> { it.isFolder }.thenBy { it.name.lowercase() })
internal fun Throwable.userMessage(): String = when (this) {
    is NextcloudException -> when (statusCode) {
        400 -> specificServerMessage("Nextcloud rejected the request. Check the entered values and try again.")
        401 -> "Nextcloud rejected the saved login. Disconnect and log in again."
        403 -> specificServerMessage("You do not have permission to do that.")
        404 -> "The file or folder no longer exists. Refresh the folder and try again."
        405 -> "This operation is not supported here. A file or folder with that name may already exist."
        409 -> "The operation conflicts with the current folder state. Refresh it and try again."
        412 -> "A file or folder with that name already exists. Choose a different name."
        413 -> "The file is larger than this Nextcloud server allows."
        423 -> "The file or folder is locked by another operation. Try again shortly."
        429 -> "Nextcloud received too many requests. Wait a moment and try again."
        507 -> "Nextcloud does not have enough free storage for this operation."
        in 500..599 -> "Nextcloud had a server problem (HTTP $statusCode). Try again later."
        else -> message ?: "Nextcloud rejected the request (HTTP $statusCode)."
    }
    else -> message ?: "Something went wrong"
}

private fun NextcloudException.specificServerMessage(fallback: String): String =
    serverMessage.takeUnless {
        it.equals("Bad Request", ignoreCase = true) ||
            it.equals("Forbidden", ignoreCase = true)
    } ?: fallback

internal fun renameFailureMessage(newName: String, failure: Throwable): String =
    if (failure is NextcloudException && failure.statusCode == 412) {
        "A file or folder named “$newName” already exists here. Choose a different name."
    } else {
        failure.userMessage()
    }

internal fun CloudFile.renamedTo(newName: String): CloudFile = copy(
    name = newName,
    path = NextcloudPath.child(path.substringBeforeLast('/', ""), newName),
)

private fun FileUiState.withoutPreview() = copy(
    previewFile = null,
    previewBytes = null,
    previewLoading = false,
    previewError = null,
)

private data class UploadJob(val item: UploadQueueItem, val source: UploadSource)
private data class ImagePrefetch(val file: CloudFile, val bytes: Deferred<ByteArray>)
private sealed interface UploadSource {
    data class File(val uri: Uri, val size: Long, val mimeType: String?) : UploadSource
    data class Folder(val uri: Uri) : UploadSource
}

internal fun previewableFiles(files: List<CloudFile>): List<CloudFile> = files.filter { file ->
    file.mimeType?.let { it.startsWith("image/") || it.startsWith("video/") } == true
}

internal fun adjacentPreviewFiles(files: List<CloudFile>, current: CloudFile): List<CloudFile> {
    val previewable = previewableFiles(files)
    val currentIndex = previewable.indexOfFirst { it.path == current.path }
    if (currentIndex < 0) return emptyList()
    return listOfNotNull(previewable.getOrNull(currentIndex - 1), previewable.getOrNull(currentIndex + 1))
}

internal fun foldersToPrefetch(files: List<CloudFile>): List<String> = files
    .filter(CloudFile::isFolder)
    .map(CloudFile::path)
    .distinct()

private fun CloudFile.samePreviewVersion(other: CloudFile) = path == other.path && etag == other.etag
