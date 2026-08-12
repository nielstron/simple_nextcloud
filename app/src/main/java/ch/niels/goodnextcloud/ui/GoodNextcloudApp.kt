package ch.niels.goodnextcloud.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.niels.goodnextcloud.FileUiState
import ch.niels.goodnextcloud.FileViewModel
import ch.niels.goodnextcloud.ClipboardMode
import ch.niels.goodnextcloud.UploadQueueItem
import ch.niels.goodnextcloud.UploadStatus
import ch.niels.goodnextcloud.data.CloudFile
import ch.niels.goodnextcloud.data.LinkShareOptions
import ch.niels.goodnextcloud.data.ShareUser
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.delay

@Composable
fun GoodNextcloudApp(state: FileUiState, model: FileViewModel) {
    if (state.account == null) {
        LoginScreen(state.loading, state.error, model::connect, model::clearNotice)
    } else {
        FilesScreen(state, model)
    }
}

@Composable
private fun LoginScreen(
    loading: Boolean,
    error: String?,
    onConnect: (String, String, String) -> Unit,
    onClearError: () -> Unit,
) {
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

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
                    "Browse, share and move files without the clutter.",
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
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; onClearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("App password") },
                    supportingText = { Text("Create one in Nextcloud → Personal settings → Security") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Show password")
                        }
                    },
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
                    onClick = { onConnect(server, username, password) },
                    enabled = !loading && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("Connect", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Credentials are encrypted with Android Keystore and stay on this device.",
                    Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesScreen(state: FileUiState, model: FileViewModel) {
    var search by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf<CloudFile?>(null) }
    var pendingDownload by remember { mutableStateOf<CloudFile?>(null) }
    var deleteTarget by remember { mutableStateOf<CloudFile?>(null) }
    var renameTarget by remember { mutableStateOf<CloudFile?>(null) }
    var clipboardMenuOpen by remember { mutableStateOf(false) }
    var uploadSourceOpen by remember { mutableStateOf(false) }
    var uploadQueueOpen by remember { mutableStateOf(false) }
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

    BackHandler(enabled = state.path.isNotEmpty(), onBack = model::up)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (state.path.isEmpty()) "All files" else state.path.substringAfterLast('/'), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (state.path.isEmpty()) state.account?.serverUrl.orEmpty() else "/${state.path}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (state.path.isNotEmpty()) IconButton(onClick = model::up) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Up") }
                    else Icon(Icons.Outlined.Cloud, null, Modifier.padding(start = 18.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
                },
                actions = {
                    IconButton(onClick = model::refresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
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
            ExtendedFloatingActionButton(
                onClick = { uploadSourceOpen = true },
                icon = { Icon(Icons.Outlined.Upload, null) },
                text = { Text("Upload") },
            )
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
            val visibleFiles = state.files.filter { it.name.contains(search, ignoreCase = true) }
            LaunchedEffect(state.highlightedPath) {
                if (state.highlightedPath != null) search = ""
            }
            val highlightedIndex = visibleFiles.indexOfFirst { it.path == state.highlightedPath }
            if (highlightedIndex >= 0) {
                LaunchedEffect(state.highlightedPath, highlightedIndex) {
                    fileListState.animateScrollToItem(highlightedIndex)
                    delay(3_000)
                    model.clearHighlight()
                }
            }
            if (!state.loading && visibleFiles.isEmpty()) {
                EmptyFolder(if (search.isBlank()) "This folder is empty" else "No matching files")
            } else {
                LazyColumn(state = fileListState, contentPadding = PaddingValues(bottom = 96.dp)) {
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
                                    file.mimeType?.startsWith("image/") == true -> model.showPreview(file)
                                    else -> download()
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
            files = state.files.filter { it.mimeType?.startsWith("image/") == true },
            currentFile = file,
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
            usersLoading = state.shareUsersLoading,
            usersError = state.shareUsersError,
            onDismiss = {
                sharing = null
                model.clearShareUsers()
            },
            onSearchUsers = { query -> model.searchShareUsers(query, file.isFolder) },
            onShareWithUser = { recipient ->
                sharing = null
                model.clearShareUsers()
                model.share(file, recipient)
            },
            onCreateLink = { options ->
                sharing = null
                model.clearShareUsers()
                model.createLink(file, options)
            },
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
        Surface(
            Modifier.size(46.dp),
            RoundedCornerShape(13.dp),
            color = if (file.isFolder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(fileIcon(file), null, tint = if (file.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                if (file.isFolder) "Folder" else listOf(formatSize(file.size), formatDate(file.modifiedAt)).filter(String::isNotBlank).joinToString(" · "),
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
    val bitmap = remember(bytes) {
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    LaunchedEffect(pagerState.currentPage) {
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
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (files[page].path == currentFile.path) {
                        when {
                            loading -> CircularProgressIndicator(color = Color.White)
                            error != null -> Text(
                                error,
                                modifier = Modifier.padding(32.dp),
                                color = Color(0xFFFFB4AB),
                            )
                            bitmap != null -> Image(
                                bitmap = bitmap,
                                contentDescription = "Preview of ${currentFile.name}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
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
private fun ShareDialog(
    file: CloudFile,
    users: List<ShareUser>,
    usersLoading: Boolean,
    usersError: String?,
    onDismiss: () -> Unit,
    onSearchUsers: (String) -> Unit,
    onShareWithUser: (String) -> Unit,
    onCreateLink: (LinkShareOptions) -> Unit,
) {
    var userQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<ShareUser?>(null) }
    var password by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var allowUpload by remember { mutableStateOf(false) }
    var showLinkOptions by remember { mutableStateOf(false) }
    val validExpiry = expiry.isBlank() || runCatching { LocalDate.parse(expiry) >= LocalDate.now() }.getOrDefault(false)
    LaunchedEffect(file.path) { onSearchUsers("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Share, null) },
        title = { Text("Share ${file.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
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
                if (usersLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (usersError != null) {
                    Text(
                        usersError,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (users.isEmpty()) {
                    Text(
                        if (userQuery.isBlank()) "No suggested users" else "No users found",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(Modifier.padding(top = 6.dp)) {
                        users.take(5).forEach { user ->
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
                Button(
                    onClick = { onShareWithUser(requireNotNull(selectedUser).id) },
                    enabled = selectedUser != null,
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
                        if (file.isFolder) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = allowUpload, onCheckedChange = { allowUpload = it })
                                Text("Allow recipients to upload")
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onCreateLink(LinkShareOptions(password, expiry, allowUpload)) },
                    enabled = validExpiry,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Create and copy link")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

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
