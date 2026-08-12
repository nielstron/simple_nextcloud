package ch.niels.goodnextcloud.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.niels.goodnextcloud.FileUiState
import ch.niels.goodnextcloud.FileViewModel
import ch.niels.goodnextcloud.data.CloudFile
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow

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
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resolver = model.getApplication<Application>().contentResolver
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.upload(resolver, it) }
    }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = pendingDownload
        if (uri != null && file != null) model.download(resolver, file, uri)
        pendingDownload = null
    }

    BackHandler(enabled = state.path.isNotEmpty(), onBack = model::up)
    LaunchedEffect(state.error, state.message, state.shareUrl) {
        state.shareUrl?.let { url ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Nextcloud share link", url))
        }
        (state.error ?: state.message)?.let {
            snackbar.showSnackbar(it)
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
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
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
            if (!state.loading && visibleFiles.isEmpty()) {
                EmptyFolder(if (search.isBlank()) "This folder is empty" else "No matching files")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(visibleFiles, key = { it.path }) { file ->
                        val download = if (file.isFolder) null else fun() {
                            pendingDownload = file
                            downloadLauncher.launch(file.name)
                        }
                        FileRow(
                            file = file,
                            onOpen = { if (file.isFolder) model.open(file) else download?.invoke() },
                            onShare = { sharing = file },
                            onDownload = download,
                        )
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    sharing?.let { file ->
        ShareDialog(file, { sharing = null }) { recipient ->
            sharing = null
            model.share(file, recipient)
        }
    }
}

@Composable
private fun FileRow(file: CloudFile, onOpen: () -> Unit, onShare: () -> Unit, onDownload: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
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
        IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Share ${file.name}") }
        if (onDownload != null) IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download ${file.name}") }
    }
}

@Composable
private fun ShareDialog(file: CloudFile, onDismiss: () -> Unit, onShare: (String?) -> Unit) {
    var recipient by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Share, null) },
        title = { Text("Share ${file.name}") },
        text = {
            Column {
                Text("Enter a Nextcloud username, or create a public link.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    label = { Text("Username (optional)") },
                    singleLine = true,
                )
                OutlinedButton(onClick = { onShare(null) }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Create public link")
                }
            }
        },
        confirmButton = { Button(onClick = { onShare(recipient) }, enabled = recipient.isNotBlank()) { Text("Share") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
