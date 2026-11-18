package de.nielstron.simplenextcloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import de.nielstron.simplenextcloud.data.Account
import de.nielstron.simplenextcloud.data.CloudFile
import de.nielstron.simplenextcloud.data.NextcloudPath
import de.nielstron.simplenextcloud.data.cacheIdentity
import de.nielstron.simplenextcloud.data.decodeCloudFile
import de.nielstron.simplenextcloud.data.encodeCloudFile
import de.nielstron.simplenextcloud.data.rebasePath
import org.json.JSONObject

/** Something a launcher shortcut or widget asks the app to show. */
sealed interface QuickAccessRequest {
    val serverUrl: String
    val username: String

    /** [fileId] identifies the folder; [path] is where it was last seen and is tried first. */
    data class OpenFolder(
        override val serverUrl: String,
        override val username: String,
        val path: String,
        val fileId: String?,
    ) : QuickAccessRequest

    data class OpenFile(
        override val serverUrl: String,
        override val username: String,
        val file: CloudFile,
    ) : QuickAccessRequest

    data class ShowView(
        override val serverUrl: String,
        override val username: String,
        val view: BrowseView,
    ) : QuickAccessRequest

    fun belongsTo(account: Account) =
        NextcloudPath.normalizeServerUrl(account.serverUrl) == NextcloudPath.normalizeServerUrl(serverUrl) &&
            account.username == username
}

object QuickAccessIntents {
    private const val ACTION_OPEN_FOLDER = "de.nielstron.simplenextcloud.action.OPEN_FOLDER"
    private const val ACTION_OPEN_FILE = "de.nielstron.simplenextcloud.action.OPEN_FILE"
    private const val ACTION_SHOW_VIEW = "de.nielstron.simplenextcloud.action.SHOW_VIEW"
    private const val EXTRA_SERVER = "server"
    private const val EXTRA_USERNAME = "username"
    private const val EXTRA_PATH = "path"
    private const val EXTRA_FILE_ID = "fileId"
    private const val EXTRA_FILE = "file"
    private const val EXTRA_VIEW = "view"

    fun openFolder(context: Context, account: Account, path: String, fileId: String?): Intent =
        base(context, account, ACTION_OPEN_FOLDER, "folder", fileId ?: path)
            .putExtra(EXTRA_PATH, path)
            .putExtra(EXTRA_FILE_ID, fileId)

    fun openFile(context: Context, account: Account, file: CloudFile): Intent =
        base(context, account, ACTION_OPEN_FILE, "file", file.fileId ?: file.path)
            .putExtra(EXTRA_FILE, encodeCloudFile(file).toString())

    fun showView(context: Context, account: Account, view: BrowseView): Intent =
        base(context, account, ACTION_SHOW_VIEW, "view", view.name)
            .putExtra(EXTRA_VIEW, view.name)

    fun parse(intent: Intent): QuickAccessRequest? {
        val server = intent.getStringExtra(EXTRA_SERVER)
        val username = intent.getStringExtra(EXTRA_USERNAME)
        return when (intent.action) {
            ACTION_OPEN_FOLDER -> QuickAccessRequest.OpenFolder(
                requireNotNull(server),
                requireNotNull(username),
                requireNotNull(intent.getStringExtra(EXTRA_PATH)),
                intent.getStringExtra(EXTRA_FILE_ID),
            )
            ACTION_OPEN_FILE -> QuickAccessRequest.OpenFile(
                requireNotNull(server),
                requireNotNull(username),
                decodeCloudFile(JSONObject(requireNotNull(intent.getStringExtra(EXTRA_FILE)))),
            )
            ACTION_SHOW_VIEW -> QuickAccessRequest.ShowView(
                requireNotNull(server),
                requireNotNull(username),
                BrowseView.valueOf(requireNotNull(intent.getStringExtra(EXTRA_VIEW))),
            )
            else -> null
        }
    }

    /**
     * A unique data URI keeps PendingIntents for different targets apart: Android ignores extras when
     * deciding whether two PendingIntents are the same.
     */
    private fun base(context: Context, account: Account, action: String, kind: String, key: String) =
        Intent(action)
            .setClass(context, MainActivity::class.java)
            .setData(Uri.Builder().scheme("simplenextcloud").authority(kind).appendPath(key).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SERVER, account.serverUrl)
            .putExtra(EXTRA_USERNAME, account.username)

    internal fun pathOf(intent: Intent): String? = intent.getStringExtra(EXTRA_PATH)
    internal fun fileIdOf(intent: Intent): String? = intent.getStringExtra(EXTRA_FILE_ID)
}

/**
 * Home-screen shortcuts to folders. Each is keyed by account and file ID, so a folder keeps its
 * shortcut across renames and moves, and pinning it twice updates one shortcut instead of adding two.
 */
object FolderShortcuts {
    private const val MAX_STARRED_SHORTCUTS = 4

    private fun prefix(account: Account) = "folder-${account.cacheIdentity().take(16)}-"

    private fun id(account: Account, fileId: String) = prefix(account) + fileId

    private fun info(context: Context, account: Account, folder: CloudFile, rank: Int = 0): ShortcutInfoCompat {
        val fileId = requireNotNull(folder.fileId) { "Nextcloud did not provide a file ID for ${folder.name}" }
        return ShortcutInfoCompat.Builder(context, id(account, fileId))
            .setShortLabel(folder.name)
            .setLongLabel("/${folder.path}")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut_folder))
            .setIntent(QuickAccessIntents.openFolder(context, account, folder.path, fileId))
            .setRank(rank)
            .build()
    }

    /** Asks the launcher to pin [folder]. False if the launcher cannot pin shortcuts. */
    fun pin(context: Context, account: Account, folder: CloudFile): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = info(context, account, folder)
        val existing = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .filter { it.id == shortcut.id }
        if (existing.isNotEmpty()) {
            // A shortcut disabled because its folder vanished may be pinned again once the folder is back.
            ShortcutManagerCompat.enableShortcuts(context, listOf(shortcut))
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        }
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /** Offers the first starred folders when the app icon is long-pressed. */
    fun publishStarred(context: Context, account: Account, favorites: List<CloudFile>) {
        val shortcuts = favorites
            .filter { it.isFolder && it.fileId != null }
            .take(MAX_STARRED_SHORTCUTS)
            .mapIndexed { rank, folder -> info(context, account, folder, rank) }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /** Follows a rename or move of [oldPath] (or a folder above it) to [newPath]. */
    fun rebase(context: Context, account: Account, oldPath: String, newPath: String) {
        val updated = ownShortcuts(context, account).mapNotNull { shortcut ->
            val path = rebasePath(QuickAccessIntents.pathOf(shortcut.intent) ?: return@mapNotNull null, oldPath, newPath)
                ?: return@mapNotNull null
            val fileId = requireNotNull(QuickAccessIntents.fileIdOf(shortcut.intent))
            info(context, account, CloudFile(path.substringAfterLast('/'), path, true, 0, null, null, null, fileId))
        }
        if (updated.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, updated)
    }

    fun disable(context: Context, account: Account, fileId: String, name: String) {
        ShortcutManagerCompat.disableShortcuts(context, listOf(id(account, fileId)), "$name no longer exists")
    }

    fun clearDynamic(context: Context) = ShortcutManagerCompat.removeAllDynamicShortcuts(context)

    private fun ownShortcuts(context: Context, account: Account) = ShortcutManagerCompat
        .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED or ShortcutManagerCompat.FLAG_MATCH_DYNAMIC)
        .filter { it.id.startsWith(prefix(account)) }
        .distinctBy { it.id }
}
