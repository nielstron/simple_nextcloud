package ch.niels.goodnextcloud.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.niels.goodnextcloud.FileUiState
import ch.niels.goodnextcloud.FileViewModel
import ch.niels.goodnextcloud.ClipboardMode
import ch.niels.goodnextcloud.UploadQueueItem
import ch.niels.goodnextcloud.UploadStatus
import ch.niels.goodnextcloud.previewableFiles
import ch.niels.goodnextcloud.data.Account
import ch.niels.goodnextcloud.data.CloudFile
import ch.niels.goodnextcloud.data.ExistingShare
import ch.niels.goodnextcloud.data.LinkShareOptions
import ch.niels.goodnextcloud.data.NextcloudPath
import ch.niels.goodnextcloud.data.ShareUser
import ch.niels.goodnextcloud.data.ShareUpdate
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.delay
import okhttp3.Credentials

@Composable
fun SimpleNextcloudApp(
    state: FileUiState,
    model: FileViewModel,
    sharedUris: List<android.net.Uri> = emptyList(),
    onSharedUrisConsumed: () -> Unit = {},
) {
    if (state.account == null) {
        LoginScreen(
            loading = state.loading,
            waiting = state.loginWaiting,
            error = state.error,
            loginUrl = state.loginUrl,
            onConnect = model::startBrowserLogin,
            onLoginUrlOpened = model::consumeLoginUrl,
            onCancel = model::cancelBrowserLogin,
            onClearError = model::clearNotice,
        )
    } else {
        FilesScreen(state, model, sharedUris, onSharedUrisConsumed)
    }
}

@Composable
private fun LoginScreen(
    loading: Boolean,
    waiting: Boolean,
    error: String?,
    loginUrl: String?,
    onConnect: (String) -> Unit,
    onLoginUrlOpened: () -> Unit,
    onCancel: () -> Unit,
    onClearError: () -> Unit,
) {
    var server by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(loginUrl) {
        loginUrl?.let { url ->
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, android.net.Uri.parse(url))
            onLoginUrlOpened()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(72.dp), RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Cloud, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Your cloud. Simply.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Browse, download, and share files.",
                    Modifier.padding(top = 8.dp, bottom = 28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nextcloud address") },
                    placeholder = { Text("https://cloud.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(14.dp),
                )
                AnimatedVisibility(error != null) {
                    Text(
                        error.orEmpty(),
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { onConnect(server) },
                    enabled = !loading && (waiting || server.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text(if (waiting) "Open browser again" else "Log in with browser", fontWeight = FontWeight.SemiBold)
                }
                AnimatedVisibility(waiting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Waiting for approval. Return to this app after granting access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onCancel) { Text("Cancel login") }
                    }
                }
                Text(
                    "Nextcloud creates a revocable app password after you approve access in the browser.",
                    Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class FileSort(val label: String) {
    NAME("Name"),
    MODIFIED("Last modified"),
    SIZE("Size"),
    TYPE("Type");

    fun comparator(ascending: Boolean): Comparator<CloudFile> {
        val valueComparator = when (this) {
            NAME -> compareBy(String.CASE_INSENSITIVE_ORDER, CloudFile::name)
            MODIFIED -> compareBy<CloudFile> { it.modifiedAt?.let(::modifiedEpochSeconds) ?: Long.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER, CloudFile::name)
            SIZE -> compareBy(CloudFile::size).thenBy(String.CASE_INSENSITIVE_ORDER, CloudFile::name)
            TYPE -> compareBy(String.CASE_INSENSITIVE_ORDER) { file: CloudFile ->
                file.mimeType ?: file.name.substringAfterLast('.', "")
            }.thenBy(String.CASE_INSENSITIVE_ORDER, CloudFile::name)
        }
        return compareBy<CloudFile> { !it.isFolder }.then(if (ascending) valueComparator else valueComparator.reversed())
    }
}

private fun modifiedEpochSeconds(value: String): Long =
    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesScreen(
    state: FileUiState,
    model: FileViewModel,
    sharedUris: List<android.net.Uri>,
    onSharedUrisConsumed: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var breadcrumbMenuOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<CloudFile?>(null) }
    var pendingDownload by remember { mutableStateOf<CloudFile?>(null) }
    var deleteTarget by remember { mutableStateOf<CloudFile?>(null) }
    var renameTarget by remember { mutableStateOf<CloudFile?>(null) }
    var clipboardMenuOpen by remember { mutableStateOf(false) }
    var uploadSourceOpen by remember { mutableStateOf(false) }
    var uploadQueueOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showHiddenFiles by remember { mutableStateOf(false) }
    var fileSort by remember { mutableStateOf(FileSort.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    val fileListState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val unfinishedUploadCount = state.uploadQueue.count {
        it.status == UploadStatus.QUEUED || it.status == UploadStatus.UPLOADING
    }
    val context = LocalContext.current
    val resolver = model.getApplication<Application>().contentResolver
    val filesUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.enqueueFiles(resolver, uris)
    }
    val folderUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { model.enqueueFolder(resolver, it) }
    }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = pendingDownload
        if (uri != null && file != null) model.download(resolver, file, uri)
        pendingDownload = null
    }

    BackHandler(enabled = sharedUris.isEmpty() && state.path.isNotEmpty(), onBack = model::up)
    LaunchedEffect(state.error, state.message, state.shareUrl, state.downloadedUri) {
        state.shareUrl?.let { url ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Nextcloud share link", url))
        }
        (state.error ?: state.message)?.let { notice ->
            val result = snackbar.showSnackbar(
                message = notice,
                actionLabel = if (state.downloadedUri != null) "Open" else null,
            )
            if (result == SnackbarResult.ActionPerformed) {
                val uri = requireNotNull(state.downloadedUri)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, state.downloadedMimeType ?: "*/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
            model.clearNotice()
        }
    }
    LaunchedEffect(state.localOpenUri) {
        state.localOpenUri?.let { uri ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, state.localOpenMimeType ?: "application/octet-stream")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            } catch (_: ActivityNotFoundException) {
                snackbar.showSnackbar("No installed app can open this file type")
            } finally {
                model.clearLocalOpen()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Column(Modifier.clickable { breadcrumbMenuOpen = true }) {
                            Text(if (state.path.isEmpty()) "All files" else state.path.substringAfterLast('/'), fontWeight = FontWeight.SemiBold)
                            Text(
                                if (state.path.isEmpty()) state.account?.serverUrl.orEmpty() else "/${state.path}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DropdownMenu(
                            expanded = breadcrumbMenuOpen,
                            onDismissRequest = { breadcrumbMenuOpen = false },
                        ) {
                            NextcloudPath.breadcrumbs(state.path).forEach { (label, path) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                    onClick = {
                                        breadcrumbMenuOpen = false
                                        model.loadPath(path)
                                    },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    Icon(
                        Icons.Outlined.Cloud,
                        null,
                        Modifier.padding(start = 18.dp, end = 10.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = model::refresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort files") }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (sortAscending) "Ascending" else "Descending") },
                                leadingIcon = {
                                    Icon(if (sortAscending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null)
                                },
                                onClick = { sortAscending = !sortAscending },
                            )
                            HorizontalDivider()
                            FileSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = { if (fileSort == option) Icon(Icons.Outlined.Check, "Selected") },
                                    onClick = { fileSort = option; sortMenuOpen = false },
                                )
                            }
                        }
                    }
                    state.clipboardFile?.let { clipboardFile ->
                        val sourceParent = clipboardFile.path.substringBeforeLast('/', "")
                        val insideSource = clipboardFile.isFolder &&
                            (state.path == clipboardFile.path || state.path.startsWith("${clipboardFile.path}/"))
                        val canPaste = state.path != sourceParent && !insideSource
                        Box {
                            IconButton(onClick = { clipboardMenuOpen = true }) {
                                Icon(Icons.Outlined.ContentPaste, "Clipboard")
                            }
                            DropdownMenu(
                                expanded = clipboardMenuOpen,
                                onDismissRequest = { clipboardMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(clipboardFile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                state.clipboardMode?.name?.lowercase().orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                    enabled = false,
                                    onClick = {},
                                )
                                DropdownMenuItem(
                                    text = { Text("Paste here") },
                                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                    enabled = canPaste,
                                    onClick = { clipboardMenuOpen = false; model.paste() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear clipboard") },
                                    leadingIcon = { Icon(Icons.Outlined.Close, null) },
                                    onClick = { clipboardMenuOpen = false; model.clearClipboard() },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            BadgedBox(
                                badge = {
                                    if (unfinishedUploadCount > 0) {
                                        Badge {
                                            Text(if (unfinishedUploadCount > 99) "99+" else unfinishedUploadCount.toString())
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    if (unfinishedUploadCount == 0) "More"
                                    else "More, $unfinishedUploadCount unfinished uploads",
                                )
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Show hidden files") },
                                trailingIcon = {
                                    Checkbox(checked = showHiddenFiles, onCheckedChange = null)
                                },
                                onClick = { showHiddenFiles = !showHiddenFiles },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (unfinishedUploadCount > 0) "Upload queue ($unfinishedUploadCount active)"
                                        else "Upload queue",
                                    )
                                },
                                leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                                onClick = { menuOpen = false; uploadQueueOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Disconnect") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = { menuOpen = false; model.disconnect() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (sharedUris.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        model.enqueueFiles(resolver, sharedUris)
                        onSharedUrisConsumed()
                    },
                    icon = { Icon(Icons.Outlined.ContentPaste, null) },
                    text = {
                        Text(if (sharedUris.size == 1) "Place file here" else "Place ${sharedUris.size} files here")
                    },
                )
            } else {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (actionsExpanded) {
                        ExtendedFloatingActionButton(
                            onClick = { actionsExpanded = false; newFolderOpen = true },
                            icon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                            text = { Text("New folder") },
                        )
                        ExtendedFloatingActionButton(
                            onClick = { actionsExpanded = false; uploadSourceOpen = true },
                            icon = { Icon(Icons.Outlined.Upload, null) },
                            text = { Text("Upload") },
                        )
                    }
                    FloatingActionButton(onClick = { actionsExpanded = !actionsExpanded }) {
                        Icon(if (actionsExpanded) Icons.Outlined.Close else Icons.Outlined.Add, if (actionsExpanded) "Close actions" else "Add")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text("Search this folder") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            val visibleFiles = state.files
                .asSequence()
                .filter { showHiddenFiles || !it.name.startsWith('.') }
                .filter { it.name.contains(search, ignoreCase = true) }
                .sortedWith(fileSort.comparator(sortAscending))
                .toList()
            LaunchedEffect(state.highlightedPath) {
                if (state.highlightedPath != null) search = ""
            }
            val highlightedIndex = visibleFiles.indexOfFirst { it.path == state.highlightedPath }
            if (highlightedIndex >= 0) {
                LaunchedEffect(state.highlightedPath, highlightedIndex) {
                    fileListState.animateScrollToItem(highlightedIndex + if (state.path.isEmpty()) 0 else 1)
                    delay(3_000)
                    model.clearHighlight()
                }
            }
            if (!state.loading && visibleFiles.isEmpty() && state.path.isEmpty()) {
                EmptyFolder(if (search.isBlank()) "This folder is empty" else "No matching files")
            } else {
                LazyColumn(
                    state = fileListState,
                    contentPadding = PaddingValues(bottom = if (actionsExpanded) 224.dp else 96.dp),
                ) {
                    if (state.path.isNotEmpty()) {
                        item(key = "parent-folder") {
                            ParentFolderRow(onOpen = model::up)
                            HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    items(visibleFiles, key = { it.path }) { file ->
                        val download = fun() {
                            pendingDownload = file
                            downloadLauncher.launch(if (file.isFolder) "${file.name}.zip" else file.name)
                        }
                        FileRow(
                            file = file,
                            highlighted = file.path == state.highlightedPath,
                            onOpen = {
                                when {
                                    file.isFolder -> model.open(file)
                                    file.mimeType?.let { it.startsWith("image/") || it.startsWith("video/") } == true ->
                                        model.showPreview(file)
                                    else -> model.openLocally(file)
                                }
                            },
                            onDelete = { deleteTarget = file },
                            onCopy = { model.stageTransfer(file, ClipboardMode.COPY) },
                            onMove = { model.stageTransfer(file, ClipboardMode.MOVE) },
                            onRename = { renameTarget = file },
                            onShare = { sharing = file },
                            onDownload = download,
                        )
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    state.previewFile?.let { file ->
        FullScreenImagePreview(
            files = previewableFiles(state.files),
            currentFile = file,
            account = requireNotNull(state.account),
            bytes = state.previewBytes,
            loading = state.previewLoading,
            error = state.previewError,
            onSelect = model::showPreview,
            onDismiss = model::closePreview,
            onShare = { sharing = file },
            onDownload = {
                model.closePreview()
                pendingDownload = file
                downloadLauncher.launch(file.name)
            },
        )
    }

    // Compose the share dialog after the viewer so it sits above it. The viewer remains mounted,
    // preserving the current image and pager position while sharing is completed or cancelled.
    sharing?.let { file ->
        ShareDialog(
            file = file,
            users = state.shareUsers,
            frequentUsers = state.frequentShareUsers,
            usersLoading = state.shareUsersLoading,
            usersError = state.shareUsersError,
            shares = state.shares,
            sharesLoading = state.sharesLoading,
            sharesError = state.sharesError,
            operationLoading = state.shareOperationLoading,
            operationError = state.shareOperationError,
            currentUsername = requireNotNull(state.account).username,
            onDismiss = {
                sharing = null
                model.clearShareState()
            },
            onLoadShares = { model.loadShares(file) },
            onLoadFrequentUsers = { model.loadFrequentShareUsers(file.isFolder) },
            onSearchUsers = { query -> model.searchShareUsers(query, file.isFolder) },
            onShareWithUser = { user, permissions ->
                model.share(file, user, permissions)
            },
            onCreateLink = { options ->
                model.createLink(file, options)
            },
            onUpdateShare = { share, update -> model.updateShare(file, share, update) },
            onDeleteShare = { share -> model.deleteShare(file, share) },
        )
    }

    deleteTarget?.let { file ->
        DeleteDialog(
            file = file,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                model.delete(file)
            },
        )
    }

    renameTarget?.let { file ->
        RenameDialog(
            file = file,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                model.rename(file, newName)
            },
        )
    }

    if (uploadSourceOpen) {
        UploadSourceDialog(
            onDismiss = { uploadSourceOpen = false },
            onFiles = {
                uploadSourceOpen = false
                filesUploadLauncher.launch(arrayOf("*/*"))
            },
            onFolder = {
                uploadSourceOpen = false
                folderUploadLauncher.launch(null)
            },
        )
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onDismiss = { newFolderOpen = false },
            onConfirm = { name ->
                newFolderOpen = false
                model.createFolder(name)
            },
        )
    }

    if (uploadQueueOpen) {
        UploadQueueDialog(
            items = state.uploadQueue,
            onDismiss = { uploadQueueOpen = false },
            onClearFinished = model::clearFinishedUploads,
            onOpenItem = { item ->
                uploadQueueOpen = false
                model.navigateToUpload(item)
            },
        )
    }
}

@Composable
private fun ParentFolderRow(onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            Modifier.size(46.dp),
            RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("..", fontWeight = FontWeight.Medium)
            Text("Parent folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(
    file: CloudFile,
    highlighted: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Surface(
                Modifier.fillMaxSize(),
                RoundedCornerShape(13.dp),
                color = if (file.isFolder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        fileIcon(file),
                        null,
                        tint = if (file.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 5.dp)) {
                if (file.shareTypes.any { it != 3 }) ShareTypeBadge(Icons.Outlined.Person)
                if (3 in file.shareTypes) ShareTypeBadge(Icons.Outlined.Link)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                when {
                    file.isIncomingShare -> "Shared by ${file.ownerDisplayName ?: file.ownerId ?: "another user"}"
                    file.isFolder -> "Folder"
                    else -> listOf(formatSize(file.size), formatDate(file.modifiedAt)).filter(String::isNotBlank).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Outlined.MoreVert, "Options for ${file.name}")
            }
            DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                FileMenuItem("Delete", Icons.Outlined.Delete) { optionsOpen = false; onDelete() }
                FileMenuItem("Copy", Icons.Outlined.ContentCopy) { optionsOpen = false; onCopy() }
                FileMenuItem("Move", Icons.AutoMirrored.Outlined.DriveFileMove) { optionsOpen = false; onMove() }
                FileMenuItem("Rename", Icons.Outlined.Edit) { optionsOpen = false; onRename() }
                FileMenuItem("Share", Icons.Outlined.Share) { optionsOpen = false; onShare() }
                FileMenuItem("Download", Icons.Outlined.Download) { optionsOpen = false; onDownload() }
            }
        }
    }
}

@Composable
private fun FileMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun ShareTypeBadge(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(18.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(12.dp)) }
    }
}

@Composable
private fun DeleteDialog(file: CloudFile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Move ${file.name} to deleted files?") },
        text = {
            Text(
                if (file.isFolder) "The folder and everything inside it will be moved to Nextcloud’s deleted files."
                else "The file will be moved to Nextcloud’s deleted files.",
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameDialog(file: CloudFile, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(file.path) { mutableStateOf(file.name) }
    val valid = name.isNotBlank() && '/' !in name && name != file.name
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Edit, null) },
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                supportingText = { if ('/' in name) Text("A name cannot contain /") },
                isError = '/' in name,
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name.trim()) }, enabled = valid) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && '/' !in name
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CreateNewFolder, null) },
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                supportingText = { if ('/' in name) Text("A name cannot contain /") },
                isError = '/' in name,
                singleLine = true,
            )
        },
        confirmButton = { Button(onClick = { onConfirm(name.trim()) }, enabled = valid) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UploadSourceDialog(onDismiss: () -> Unit, onFiles: () -> Unit, onFolder: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Upload, null) },
        title = { Text("Upload") },
        text = {
            Column {
                Button(onClick = onFiles, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null)
                    Text("Select files", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onFolder, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Icon(Icons.Outlined.Folder, null)
                    Text("Select a folder", Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UploadQueueDialog(
    items: List<UploadQueueItem>,
    onDismiss: () -> Unit,
    onClearFinished: () -> Unit,
    onOpenItem: (UploadQueueItem) -> Unit,
) {
    val hasFinished = items.any { it.status in setOf(UploadStatus.COMPLETED, UploadStatus.FAILED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Upload, null) },
        title = { Text("Upload queue") },
        text = {
            if (items.isEmpty()) {
                Text("The upload queue is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenItem(item) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (item.isFolder) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when (item.status) {
                                        UploadStatus.QUEUED -> "Queued"
                                        UploadStatus.UPLOADING -> "Uploading…"
                                        UploadStatus.COMPLETED -> "Completed"
                                        UploadStatus.FAILED -> item.error ?: "Failed"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.status == UploadStatus.FAILED) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.status == UploadStatus.UPLOADING) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else if (item.status == UploadStatus.COMPLETED) {
                                Icon(Icons.Outlined.Check, "Completed")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (hasFinished) TextButton(onClick = onClearFinished) { Text("Clear finished") }
        },
    )
}

@Composable
private fun FullScreenImagePreview(
    files: List<CloudFile>,
    currentFile: CloudFile,
    account: Account,
    bytes: ByteArray?,
    loading: Boolean,
    error: String?,
    onSelect: (CloudFile) -> Unit,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    val initialPage = files.indexOfFirst { it.path == currentFile.path }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { files.size })
    var zoomedPath by remember { mutableStateOf<String?>(null) }
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    LaunchedEffect(pagerState.currentPage) {
        zoomedPath = null
        val selected = files[pagerState.currentPage]
        if (selected.path != currentFile.path) onSelect(selected)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { files[it].path },
                userScrollEnabled = zoomedPath == null,
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (files[page].path == currentFile.path) {
                        when {
                            currentFile.mimeType?.startsWith("video/") == true -> AuthenticatedVideoPreview(
                                file = currentFile,
                                account = account,
                            )
                            loading -> CircularProgressIndicator(color = Color.White)
                            error != null -> Text(
                                error,
                                modifier = Modifier.padding(32.dp),
                                color = Color(0xFFFFB4AB),
                            )
                            bitmap != null -> ZoomablePreviewImage(
                                bitmap = bitmap,
                                name = currentFile.name,
                                onZoomChanged = { zoomed ->
                                    zoomedPath = if (zoomed) files[page].path else null
                                },
                            )
                            else -> Text("Preview unavailable", color = Color.White)
                        }
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(start = 8.dp, end = 16.dp, top = 36.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(currentFile.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${pagerState.currentPage + 1} of ${files.size}",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Share, null, tint = Color.White)
                    Text("Share", Modifier.padding(start = 8.dp), color = Color.White)
                }
                Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Download, null)
                    Text("Download", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedVideoPreview(file: CloudFile, account: Account) {
    var loading by remember(file.path, file.etag) { mutableStateOf(true) }
    var error by remember(file.path, file.etag) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    val controls = MediaController(context)
                    controls.setAnchorView(this)
                    setMediaController(controls)
                    setOnPreparedListener {
                        loading = false
                        start()
                    }
                    setOnErrorListener { _, what, extra ->
                        loading = false
                        error = "Video playback failed ($what/$extra)"
                        true
                    }
                    setVideoURI(
                        Uri.parse(NextcloudPath.davUrl(account, file.path)),
                        mapOf(
                            "Authorization" to Credentials.basic(account.username, account.appPassword),
                            "User-Agent" to "SimpleNextcloud/0.1",
                            "android-allow-cross-domain-redirect" to "0",
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = VideoView::stopPlayback,
        )
        if (loading) CircularProgressIndicator(color = Color.White)
        error?.let { Text(it, modifier = Modifier.padding(32.dp), color = Color(0xFFFFB4AB)) }
    }
}

@Composable
private fun ZoomablePreviewImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    name: String,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember(name) { mutableFloatStateOf(1f) }
    var offset by remember(name) { mutableStateOf(Offset.Zero) }
    var viewport by remember(name) { mutableStateOf(IntSize.Zero) }

    fun constrained(candidate: Offset, atScale: Float): Offset {
        val maxX = viewport.width * (atScale - 1f) / 2f
        val maxY = viewport.height * (atScale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun setTransform(newScale: Float, newOffset: Offset) {
        scale = newScale.coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else constrained(newOffset, scale)
        onZoomChanged(scale > 1.01f)
    }

    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        val ratio = nextScale / scale
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val nextOffset = offset * ratio + (centroid - center) * (1f - ratio) + panChange
        setTransform(nextScale, nextOffset)
    }

    Image(
        bitmap = bitmap,
        contentDescription = "Preview of $name",
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(name, viewport) {
                detectTapGestures(
                    onDoubleTap = { position ->
                        if (scale > 1.01f) {
                            setTransform(1f, Offset.Zero)
                        } else {
                            val nextScale = 2.5f
                            val center = Offset(viewport.width / 2f, viewport.height / 2f)
                            setTransform(nextScale, (center - position) * (nextScale - 1f))
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(
                state = transformState,
                canPan = { scale > 1.01f },
                lockRotationOnZoomPan = true,
            ),
        contentScale = ContentScale.Fit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareDialog(
    file: CloudFile,
    users: List<ShareUser>,
    frequentUsers: List<ShareUser>,
    usersLoading: Boolean,
    usersError: String?,
    shares: List<ExistingShare>,
    sharesLoading: Boolean,
    sharesError: String?,
    operationLoading: Boolean,
    operationError: String?,
    currentUsername: String,
    onDismiss: () -> Unit,
    onLoadShares: () -> Unit,
    onLoadFrequentUsers: () -> Unit,
    onSearchUsers: (String) -> Unit,
    onShareWithUser: (ShareUser, Int) -> Unit,
    onCreateLink: (LinkShareOptions) -> Unit,
    onUpdateShare: (ExistingShare, ShareUpdate) -> Unit,
    onDeleteShare: (ExistingShare) -> Unit,
) {
    var userQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<ShareUser?>(null) }
    var password by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var userPermissions by remember(file.path) { mutableStateOf(defaultSharePermissions(file.isFolder, false)) }
    var linkPermissions by remember(file.path) { mutableStateOf(defaultSharePermissions(file.isFolder, true)) }
    var showLinkOptions by remember { mutableStateOf(false) }
    val validExpiry = expiry.isBlank() || runCatching { LocalDate.parse(expiry) >= LocalDate.now() }.getOrDefault(false)
    val alreadySharedUserIds = shares.filter { it.shareType == 0 }.mapNotNull(ExistingShare::shareWith).toSet()
    val candidates = if (sharesLoading) emptyList() else (if (userQuery.isBlank()) frequentUsers else users)
        .filterNot { it.id in alreadySharedUserIds }
    val availableUsers = if (userQuery.isBlank()) candidates.take(3) else candidates
    val effectiveSelection = selectedUser?.takeUnless { sharesLoading || it.id in alreadySharedUserIds }
    val displayedUsers = effectiveSelection?.let(::listOf) ?: availableUsers
    LaunchedEffect(file.path) {
        onLoadShares()
        onLoadFrequentUsers()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopAppBar(
                    title = { Text("Share ${file.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close sharing") }
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                if (operationLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 10.dp))
                if (operationError != null) {
                    Text(
                        operationError,
                        modifier = Modifier.padding(bottom = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Existing shares", fontWeight = FontWeight.SemiBold)
                when {
                    sharesLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    sharesError != null -> Text(
                        sharesError,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    shares.isEmpty() -> Text(
                        "Only you can currently access this ${if (file.isFolder) "folder" else "file"}.",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> shares.forEach { share ->
                        ExistingShareEditor(
                            share = share,
                            isFolder = file.isFolder,
                            editable = share.ownerId == null || share.ownerId == currentUsername,
                            operationLoading = operationLoading,
                            onUpdate = { onUpdateShare(share, it) },
                            onDelete = { onDeleteShare(share) },
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 20.dp))
                Text("Share with a Nextcloud user", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = userQuery,
                    onValueChange = {
                        userQuery = it
                        selectedUser = null
                        onSearchUsers(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    label = { Text("Find a user") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                )
                if (userQuery.isNotBlank() && usersLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (userQuery.isNotBlank() && usersError != null) {
                    Text(
                        usersError,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (displayedUsers.isEmpty()) {
                    Text(
                        if (userQuery.isBlank()) "Type a name to find a user" else "No users found",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(Modifier.padding(top = 6.dp)) {
                        if (userQuery.isBlank() && selectedUser == null) {
                            Text(
                                "Frequently shared with",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        displayedUsers.take(if (userQuery.isBlank()) 3 else 5).forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedUser = user
                                        userQuery = user.displayName
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(user.displayName, fontWeight = FontWeight.Medium)
                                    if (user.id != user.displayName) {
                                        Text(user.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (selectedUser?.id == user.id) Icon(Icons.Outlined.Check, "Selected")
                            }
                        }
                    }
                }
                Text("Permissions", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                SharePermissionControls(
                    permissions = userPermissions,
                    isFolder = file.isFolder,
                    isLink = false,
                    onChange = { userPermissions = it },
                )
                Button(
                    onClick = { onShareWithUser(requireNotNull(effectiveSelection), userPermissions or 1) },
                    enabled = effectiveSelection != null && !operationLoading,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Share with user") }

                HorizontalDivider(Modifier.padding(vertical = 20.dp))
                Text("Public link", fontWeight = FontWeight.SemiBold)
                Text(
                    "Anyone with the link can access this ${if (file.isFolder) "folder" else "file"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showLinkOptions = !showLinkOptions }) {
                    Text(if (showLinkOptions) "Hide link options" else "Link sharing options")
                }
                AnimatedVisibility(showLinkOptions) {
                    Column {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password (optional)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        OutlinedTextField(
                            value = expiry,
                            onValueChange = { expiry = it.trim() },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("Expiration date (optional)") },
                            placeholder = { Text("YYYY-MM-DD") },
                            supportingText = {
                                if (!validExpiry) Text("Use today or a future date in YYYY-MM-DD format")
                            },
                            isError = !validExpiry,
                            singleLine = true,
                        )
                        Text("Permissions", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                        SharePermissionControls(
                            permissions = linkPermissions,
                            isFolder = file.isFolder,
                            isLink = true,
                            onChange = { linkPermissions = it },
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        onCreateLink(
                            LinkShareOptions(
                                password = password,
                                expireDate = expiry,
                                permissions = linkPermissions or 1,
                            ),
                        )
                    },
                    enabled = validExpiry && !operationLoading,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Create and copy link")
                }
                if (operationError != null) {
                    Text(
                        operationError,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun ExistingShareEditor(
    share: ExistingShare,
    isFolder: Boolean,
    editable: Boolean,
    operationLoading: Boolean,
    onUpdate: (ShareUpdate) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var permissions by remember(share.id, share.permissions) { mutableStateOf(share.permissions) }
    var expiry by remember(share.id, share.expireDate) { mutableStateOf(share.expireDate.orEmpty()) }
    var newPassword by remember(share.id) { mutableStateOf("") }
    var removePassword by remember(share.id) { mutableStateOf(false) }
    var confirmRemove by remember(share.id) { mutableStateOf(false) }
    val isLink = share.shareType == 3
    val validExpiry = expiry.isBlank() || runCatching { LocalDate.parse(expiry) >= LocalDate.now() }.getOrDefault(false)
    val title = when (share.shareType) {
        0 -> share.displayName ?: share.shareWith ?: "User"
        1 -> share.displayName ?: share.shareWith ?: "Group"
        3 -> "Public link"
        4 -> share.displayName ?: share.shareWith ?: "Email share"
        6 -> share.displayName ?: share.shareWith ?: "Federated share"
        7 -> share.displayName ?: share.shareWith ?: "Circle"
        10 -> share.displayName ?: share.shareWith ?: "Conversation"
        else -> share.displayName ?: share.shareWith ?: "Share"
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isLink) Icons.Outlined.Share else Icons.Outlined.Person, null)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(title, fontWeight = FontWeight.Medium)
                    if (!editable) {
                        Text(
                            "Shared by ${share.ownerId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isLink && share.url != null) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Nextcloud share link", share.url))
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, "Copy link") }
                }
            }
            if (editable) {
                SharePermissionControls(
                    permissions = permissions,
                    isFolder = isFolder,
                    isLink = isLink,
                    onChange = { permissions = it },
                )
                if (isLink) {
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it.trim() },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        label = { Text("Expiration date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        supportingText = { if (!validExpiry) Text("Use today or a future date") },
                        isError = !validExpiry,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; removePassword = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New password") },
                        supportingText = { Text("Leave blank to keep the current password") },
                        enabled = !removePassword,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = removePassword,
                            onCheckedChange = { removePassword = it; if (it) newPassword = "" },
                        )
                        Text("Remove password")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (confirmRemove) {
                        TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                        TextButton(onClick = onDelete, enabled = !operationLoading) { Text("Confirm remove") }
                    } else {
                        TextButton(onClick = { confirmRemove = true }, enabled = !operationLoading) { Text("Remove") }
                        TextButton(
                            onClick = {
                                onUpdate(
                                    ShareUpdate(
                                        permissions = permissions or 1,
                                        expireDate = if (isLink && expiry != share.expireDate.orEmpty()) expiry else null,
                                        password = if (isLink) {
                                            if (removePassword) "" else newPassword.ifBlank { null }
                                        } else null,
                                    ),
                                )
                            },
                            enabled = validExpiry && !operationLoading,
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckbox(label: String, bit: Int, permissions: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = permissions and bit != 0,
            onCheckedChange = { checked ->
                onChange(if (checked) permissions or bit else permissions and bit.inv())
            },
        )
        Text(label)
    }
}

@Composable
private fun SharePermissionControls(
    permissions: Int,
    isFolder: Boolean,
    isLink: Boolean,
    onChange: (Int) -> Unit,
) {
    PermissionCheckbox("Can edit", 2, permissions, onChange)
    if (isFolder) {
        PermissionCheckbox("Can upload/create", 4, permissions, onChange)
        PermissionCheckbox("Can delete items", 8, permissions, onChange)
    }
    if (!isLink) PermissionCheckbox("Can reshare", 16, permissions, onChange)
}

private fun defaultSharePermissions(isFolder: Boolean, isLink: Boolean): Int =
    if (isLink) 1 else 1 or 2 or (if (isFolder) 4 or 8 else 0) or 16

@Composable
private fun EmptyFolder(message: String) {
    Box(Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outline)
            Text(message, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun fileIcon(file: CloudFile): ImageVector = when {
    file.isIncomingShare -> Icons.Outlined.FolderShared
    file.isFolder -> Icons.Outlined.Folder
    file.mimeType?.startsWith("image/") == true -> Icons.Outlined.Image
    file.mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf
    file.mimeType?.startsWith("text/") == true -> Icons.Outlined.Description
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    val unit = (ln(bytes.toDouble()) / ln(1_000.0)).toInt().coerceAtMost(4)
    return "%.1f %sB".format(bytes / 1_000.0.pow(unit), "kMGT"[unit - 1])
}

private fun formatDate(value: String?): String = runCatching {
    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
}.getOrDefault("")
