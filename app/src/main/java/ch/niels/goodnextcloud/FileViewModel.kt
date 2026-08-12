package ch.niels.goodnextcloud

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ch.niels.goodnextcloud.data.Account
import ch.niels.goodnextcloud.data.AccountStore
import ch.niels.goodnextcloud.data.CloudFile
import ch.niels.goodnextcloud.data.FolderListingCache
import ch.niels.goodnextcloud.data.LinkShareOptions
import ch.niels.goodnextcloud.data.NextcloudClient
import ch.niels.goodnextcloud.data.NextcloudPath
import ch.niels.goodnextcloud.data.ShareUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

class FileViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AccountStore(application)
    private val client = NextcloudClient()
    private val folderCache = FolderListingCache()
    private var navigationJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchTarget: String? = null
    private var shareUserSearchJob: Job? = null
    private val _state = MutableStateFlow(FileUiState(account = store.load()))
    val state = _state.asStateFlow()

    init {
        if (_state.value.account != null) refresh()
    }

    fun connect(server: String, username: String, appPassword: String) {
        val account = Account(NextcloudPath.normalizeServerUrl(server), username.trim(), appPassword)
        if (!account.serverUrl.startsWith("https://")) {
            _state.update { it.copy(error = "Use an HTTPS Nextcloud address") }
            return
        }
        folderCache.clear()
        _state.update { it.copy(account = account, loading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.list(account, "") } }
                .onSuccess { files ->
                    val sorted = files.sortedFiles()
                    folderCache.put("", sorted)
                    folderCache.recordVisit("")
                    store.save(account)
                    _state.update { it.copy(files = sorted, loading = false) }
                    schedulePrefetch(account, sorted)
                }
                .onFailure { failure ->
                    _state.update { it.copy(account = null, loading = false, error = failure.userMessage()) }
                }
        }
    }

    fun disconnect() {
        navigationJob?.cancel()
        prefetchJob?.cancel()
        prefetchTarget = null
        folderCache.clear()
        store.clear()
        _state.value = FileUiState()
    }

    fun open(folder: CloudFile) = loadPath(folder.path)

    fun up() {
        val parent = _state.value.path.substringBeforeLast('/', "")
        loadPath(parent)
    }

    fun loadPath(path: String, forceRefresh: Boolean = false) {
        val account = _state.value.account ?: return
        val normalizedPath = path.trim('/')
        val previousPath = _state.value.path
        val previousFiles = _state.value.files
        val cached = folderCache.get(normalizedPath)
        folderCache.recordVisit(normalizedPath)
        val adoptsPrefetch = !forceRefresh && prefetchJob?.isActive == true && prefetchTarget == normalizedPath
        if (!adoptsPrefetch) {
            prefetchJob?.cancel()
            prefetchTarget = null
        }
        _state.update {
            it.copy(
                path = normalizedPath,
                files = cached?.files ?: emptyList(),
                loading = cached == null,
                error = null,
            )
        }
        navigationJob?.cancel()
        if (!forceRefresh && folderCache.isFresh(normalizedPath)) {
            schedulePrefetch(account, requireNotNull(cached).files)
            return
        }
        // The speculative request already fetches exactly this listing. Let it become foreground work
        // instead of cancelling it and sending a duplicate PROPFIND.
        if (adoptsPrefetch) return
        navigationJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { client.list(account, normalizedPath) } }
                .onSuccess { files ->
                    val sorted = files.sortedFiles()
                    folderCache.put(normalizedPath, sorted)
                    if (_state.value.path == normalizedPath) {
                        _state.update { it.copy(files = sorted, loading = false) }
                        schedulePrefetch(account, sorted)
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

    fun refresh() = loadPath(_state.value.path, forceRefresh = true)

    fun upload(resolver: ContentResolver, uri: Uri) {
        val account = _state.value.account ?: return
        val details = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                check(cursor.moveToFirst())
                cursor.getString(0) to cursor.getLong(1)
            } ?: error("The selected document has no metadata")
        val target = NextcloudPath.child(_state.value.path, details.first)
        if (_state.value.files.any { it.name == details.first }) {
            _state.update { it.copy(error = "${details.first} already exists; it was not overwritten") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        val uploadPath = _state.value.path
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.upload(account, target, resolver, uri, details.second, resolver.getType(uri))
                    client.list(account, uploadPath)
                }
            }.onSuccess { files ->
                val sorted = files.sortedFiles()
                folderCache.put(uploadPath, sorted)
                if (_state.value.path == uploadPath) {
                    _state.update { it.copy(files = sorted, loading = false, message = "${details.first} uploaded") }
                    schedulePrefetch(account, sorted)
                }
            }.onFailure { failure -> _state.update { it.copy(loading = false, error = failure.userMessage()) } }
        }
    }

    fun download(resolver: ContentResolver, file: CloudFile, uri: Uri) = launchAction("${file.name} downloaded") {
        client.download(requireNotNull(_state.value.account), file, resolver, uri)
    }

    fun share(file: CloudFile, shareWith: String?) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.share(requireNotNull(_state.value.account), file.path, shareWith)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        loading = false,
                        message = if (shareWith.isNullOrBlank()) "Public link copied" else "Shared with ${shareWith.trim()}",
                        shareUrl = result.url,
                    )
                }
            }.onFailure { failure ->
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    fun createLink(file: CloudFile, options: LinkShareOptions) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.createLink(requireNotNull(_state.value.account), file.path, options)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(loading = false, message = "Public link copied", shareUrl = result.url)
                }
            }.onFailure { failure ->
                _state.update { it.copy(loading = false, error = failure.userMessage()) }
            }
        }
    }

    fun searchShareUsers(query: String, isFolder: Boolean) {
        val account = _state.value.account ?: return
        shareUserSearchJob?.cancel()
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

    fun clearShareUsers() {
        shareUserSearchJob?.cancel()
        _state.update { it.copy(shareUsers = emptyList(), shareUsersLoading = false, shareUsersError = null) }
    }

    fun clearNotice() = _state.update { it.copy(error = null, message = null, shareUrl = null) }

    private fun launchAction(successMessage: String, action: () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { _state.update { it.copy(loading = false, message = successMessage) } }
                .onFailure { failure -> _state.update { it.copy(loading = false, error = failure.userMessage()) } }
        }
    }

    /**
     * Immediately prefetches at most two likely directory listings, one at a time. Navigating to the
     * active target adopts that request; navigating anywhere else cancels it. Only PROPFIND metadata
     * is fetched—this code never requests file bodies.
     */
    private fun schedulePrefetch(account: Account, visibleFiles: List<CloudFile>) {
        prefetchJob?.cancel()
        val candidates = folderCache.paths() + visibleFiles.filter(CloudFile::isFolder).map(CloudFile::path)
        prefetchJob = viewModelScope.launch {
            if (_state.value.loading) return@launch
            folderCache.preferred(candidates, PREFETCH_FOLDER_LIMIT).forEach { folderPath ->
                prefetchTarget = folderPath
                runCatching { withContext(Dispatchers.IO) { client.list(account, folderPath) } }
                    .onSuccess { files ->
                        val sorted = files.sortedFiles()
                        folderCache.put(folderPath, sorted)
                        if (_state.value.path == folderPath) {
                            _state.update { it.copy(files = sorted, loading = false) }
                            prefetchTarget = null
                            schedulePrefetch(account, sorted)
                            return@launch
                        }
                    }
                    .onFailure { failure ->
                        if (_state.value.path == folderPath) {
                            prefetchTarget = null
                            _state.update { it.copy(loading = false, error = failure.userMessage()) }
                            return@launch
                        }
                    }
            }
            prefetchTarget = null
        }
    }

    private companion object {
        const val PREFETCH_FOLDER_LIMIT = 2
        const val USER_SEARCH_DEBOUNCE_MILLIS = 250L
    }
}

private fun List<CloudFile>.sortedFiles() = sortedWith(compareByDescending<CloudFile> { it.isFolder }.thenBy { it.name.lowercase() })
private fun Throwable.userMessage() = message ?: "Something went wrong"
