package ch.niels.goodnextcloud.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class SharedPreferencesFolderListingStore(context: Context, account: Account) : FolderListingStore {
    private val preferences = context.getSharedPreferences(
        "folder-listings-${account.cacheIdentity()}",
        Context.MODE_PRIVATE,
    )

    override fun load(): Map<String, FolderListingCache.Entry> = preferences.all
        .map { (path, value) ->
            val json = JSONObject(value as String)
            Triple(path, decodeEntry(json), json.optLong("lastAccessedAt", json.getLong("fetchedAt")))
        }
        .sortedBy(Triple<String, FolderListingCache.Entry, Long>::third)
        .associateTo(LinkedHashMap()) { (path, entry) -> path to entry }

    override fun loadUsage(): Map<String, FolderListingCache.Usage> = preferences.all.mapValues { (_, value) ->
        val json = JSONObject(value as String)
        FolderListingCache.Usage(
            count = json.optInt("visitCount", 1),
            lastVisitedAt = json.optLong("lastAccessedAt", json.getLong("fetchedAt")),
        )
    }

    override fun put(path: String, entry: FolderListingCache.Entry, usage: FolderListingCache.Usage) {
        preferences.edit().putString(path, encodeEntry(entry, usage).toString()).apply()
    }

    override fun recordVisit(path: String, usage: FolderListingCache.Usage) {
        val value = preferences.getString(path, null) ?: return
        val json = JSONObject(value)
            .put("visitCount", usage.count)
            .put("lastAccessedAt", usage.lastVisitedAt)
        preferences.edit().putString(path, json.toString()).apply()
    }

    override fun remove(path: String) {
        preferences.edit().remove(path).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encodeEntry(entry: FolderListingCache.Entry, usage: FolderListingCache.Usage) = JSONObject()
        .put("fetchedAt", entry.fetchedAt)
        .put("visitCount", usage.count)
        .put("lastAccessedAt", usage.lastVisitedAt)
        .put("files", JSONArray(entry.files.map(::encodeFile)))

    private fun encodeFile(file: CloudFile) = JSONObject()
        .put("name", file.name)
        .put("path", file.path)
        .put("folder", file.isFolder)
        .put("size", file.size)
        .putNullable("mimeType", file.mimeType)
        .putNullable("modifiedAt", file.modifiedAt)
        .putNullable("etag", file.etag)
        .putNullable("fileId", file.fileId)
        .putNullable("ownerId", file.ownerId)
        .putNullable("ownerDisplayName", file.ownerDisplayName)
        .putNullable("mountType", file.mountType)
        .put("shareTypes", JSONArray(file.shareTypes.toList()))

    fun sizeBytes(
        path: String,
        entry: FolderListingCache.Entry,
        usage: FolderListingCache.Usage,
    ): Long {
        val json = encodeEntry(entry, usage).toString()
        return XML_ENTRY_OVERHEAD_BYTES + path.xmlEncodedBytes() + json.xmlEncodedBytes()
    }

    private fun decodeEntry(json: JSONObject): FolderListingCache.Entry {
        val files = json.getJSONArray("files").objects().map(::decodeFile)
        return FolderListingCache.Entry(files, json.getLong("fetchedAt"))
    }

    private fun decodeFile(json: JSONObject) = CloudFile(
        name = json.getString("name"),
        path = json.getString("path"),
        isFolder = json.getBoolean("folder"),
        size = json.getLong("size"),
        mimeType = json.nullableString("mimeType"),
        modifiedAt = json.nullableString("modifiedAt"),
        etag = json.nullableString("etag"),
        fileId = json.nullableString("fileId"),
        ownerId = json.nullableString("ownerId"),
        ownerDisplayName = json.nullableString("ownerDisplayName"),
        mountType = json.nullableString("mountType"),
        shareTypes = json.getJSONArray("shareTypes").ints().toSet(),
    )

    private companion object {
        const val XML_ENTRY_OVERHEAD_BYTES = 64L
    }
}

fun persistentFolderListingCache(context: Context, account: Account): FolderListingCache {
    val store = SharedPreferencesFolderListingStore(context, account)
    return FolderListingCache(store = store, entrySize = store::sizeBytes)
}

private fun Account.cacheIdentity(): String = MessageDigest.getInstance("SHA-256")
    .digest("${NextcloudPath.normalizeServerUrl(serverUrl)}\u0000$username".toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun JSONObject.putNullable(name: String, value: String?) = put(name, value ?: JSONObject.NULL)
private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)
private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
private fun JSONArray.ints() = (0 until length()).map(::getInt)
private fun String.xmlEncodedBytes(): Long = sumOf { character ->
    when (character) {
        '&' -> 5L
        '<', '>' -> 4L
        '"', '\'' -> 6L
        else -> character.toString().toByteArray().size.toLong()
    }
}
