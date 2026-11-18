package de.nielstron.simplenextcloud.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class RecentAction { OPENED, UPLOADED }

/**
 * A folder or file used on this device. [action] and [at] describe the latest use. [uploadedHere]
 * stays set once anything was uploaded into a folder, so browsing through it later does not drop it.
 */
data class RecentEntry(
    val file: CloudFile,
    val action: RecentAction,
    val at: Long,
    val uploadedHere: Boolean = action == RecentAction.UPLOADED,
)

/** Device-local history of opened and upload-target folders and opened files, newest first. */
class RecentStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun list(account: Account): List<RecentEntry> =
        decodeRecents(preferences.getString(account.cacheIdentity(), null) ?: return emptyList())

    fun record(account: Account, file: CloudFile, action: RecentAction) =
        update(account) { it.recorded(file, action, clock()) }

    fun rebase(account: Account, oldPath: String, newPath: String) = update(account) { it.rebased(oldPath, newPath) }

    fun removeTree(account: Account, path: String) = update(account) { it.withoutTree(path) }

    fun clear(account: Account) = preferences.edit().remove(account.cacheIdentity()).apply()

    @Synchronized
    private fun update(account: Account, transform: (List<RecentEntry>) -> List<RecentEntry>) {
        preferences.edit().putString(account.cacheIdentity(), encodeRecents(transform(list(account)))).apply()
    }
}

/** The last starred list fetched from the server, so Starred, launcher shortcuts and widgets work offline. */
class FavoritesStore(context: Context) {
    private val preferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun get(account: Account): List<CloudFile> =
        decodeCloudFiles(preferences.getString(account.cacheIdentity(), null) ?: return emptyList())

    fun put(account: Account, files: List<CloudFile>) =
        preferences.edit().putString(account.cacheIdentity(), encodeCloudFiles(files)).apply()

    fun clear(account: Account) = preferences.edit().remove(account.cacheIdentity()).apply()
}

internal const val MAX_RECENT_FOLDERS = 15
internal const val MAX_RECENT_FILES = 20

/**
 * Adds a use of [file] at the front. Opening a folder drops folders above it that were only opened,
 * since those were most likely passed through on the way down and stay one ".." away.
 */
internal fun List<RecentEntry>.recorded(file: CloudFile, action: RecentAction, at: Long): List<RecentEntry> {
    val previous = firstOrNull { it.file.path == file.path }
    val entry = RecentEntry(file, action, at, uploadedHere = action == RecentAction.UPLOADED || previous?.uploadedHere == true)
    val rest = filterNot { existing ->
        existing.file.path == file.path ||
            (file.isFolder && action == RecentAction.OPENED && existing.file.isFolder &&
                !existing.uploadedHere && isStrictAncestor(existing.file.path, file.path))
    }
    val combined = listOf(entry) + rest
    return combined.filter { it.file.isFolder }.take(MAX_RECENT_FOLDERS)
        .plus(combined.filterNot { it.file.isFolder }.take(MAX_RECENT_FILES))
        .sortedByDescending(RecentEntry::at)
}

internal fun List<RecentEntry>.rebased(oldPath: String, newPath: String): List<RecentEntry> = map { entry ->
    val path = rebasePath(entry.file.path, oldPath, newPath) ?: return@map entry
    entry.copy(file = entry.file.copy(path = path, name = path.substringAfterLast('/')))
}

internal fun List<RecentEntry>.withoutTree(path: String): List<RecentEntry> =
    filterNot { rebasePath(it.file.path, path, path) != null }

/** [path] moved along with [oldPrefix] to [newPrefix], or null if [path] is not inside [oldPrefix]. */
internal fun rebasePath(path: String, oldPrefix: String, newPrefix: String): String? {
    val p = path.trim('/')
    val old = oldPrefix.trim('/')
    return when {
        p == old -> newPrefix.trim('/')
        old.isEmpty() || p.startsWith("$old/") -> NextcloudPath.child(newPrefix, p.removePrefix(old))
        else -> null
    }
}

private fun isStrictAncestor(ancestor: String, path: String): Boolean {
    val a = ancestor.trim('/')
    return a.isNotEmpty() && path.trim('/').startsWith("$a/")
}

internal fun encodeRecents(entries: List<RecentEntry>): String = JSONArray(
    entries.map { entry ->
        JSONObject()
            .put("file", encodeCloudFile(entry.file))
            .put("action", entry.action.name)
            .put("at", entry.at)
            .put("uploadedHere", entry.uploadedHere)
    },
).toString()

internal fun decodeRecents(json: String): List<RecentEntry> {
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        RecentEntry(
            file = decodeCloudFile(item.getJSONObject("file")),
            action = RecentAction.valueOf(item.getString("action")),
            at = item.getLong("at"),
            uploadedHere = item.getBoolean("uploadedHere"),
        )
    }
}
