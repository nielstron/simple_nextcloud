package de.nielstron.simplenextcloud.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class SqliteFolderListingStore(
    private val context: Context,
    account: Account,
) : FolderListingStore {
    private val identity = account.cacheIdentity()
    private val database = FolderListingDatabase(context, "folder-listings-$identity.db").writableDatabase

    init {
        migrateSharedPreferences()
    }

    override fun load(): Map<String, FolderListingCache.Entry> {
        val entries = LinkedHashMap<String, FolderListingCache.Entry>()
        database.query(
            TABLE,
            arrayOf(PATH, LISTING, FETCHED_AT),
            null,
            null,
            null,
            null,
            "$LAST_ACCESSED_AT ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries[cursor.getString(0)] = FolderListingCache.Entry(
                    files = decodeFolderListing(cursor.getBlob(1)),
                    fetchedAt = cursor.getLong(2),
                )
            }
        }
        return entries
    }

    override fun loadUsage(): Map<String, FolderListingCache.Usage> {
        val usage = mutableMapOf<String, FolderListingCache.Usage>()
        database.query(
            TABLE,
            arrayOf(PATH, VISIT_COUNT, LAST_ACCESSED_AT),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                usage[cursor.getString(0)] = FolderListingCache.Usage(cursor.getInt(1), cursor.getLong(2))
            }
        }
        return usage
    }

    override fun put(path: String, entry: FolderListingCache.Entry, usage: FolderListingCache.Usage) {
        database.insertWithOnConflict(
            TABLE,
            null,
            ContentValues().apply {
                put(PATH, path)
                put(LISTING, encodeFolderListing(entry.files))
                put(FETCHED_AT, entry.fetchedAt)
                put(VISIT_COUNT, usage.count)
                put(LAST_ACCESSED_AT, usage.lastVisitedAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override fun recordVisit(path: String, usage: FolderListingCache.Usage) {
        database.update(
            TABLE,
            ContentValues().apply {
                put(VISIT_COUNT, usage.count)
                put(LAST_ACCESSED_AT, usage.lastVisitedAt)
            },
            "$PATH = ?",
            arrayOf(path),
        )
    }

    override fun remove(path: String) {
        database.delete(TABLE, "$PATH = ?", arrayOf(path))
    }

    override fun clear() {
        database.delete(TABLE, null, null)
        legacyPreferences().edit().clear().apply()
    }

    fun sizeBytes(
        path: String,
        entry: FolderListingCache.Entry,
        usage: FolderListingCache.Usage,
    ): Long = path.toByteArray().size.toLong() + encodeFolderListing(entry.files).size

    private fun migrateSharedPreferences() {
        val preferences = legacyPreferences()
        if (preferences.all.isEmpty()) return
        database.beginTransaction()
        try {
            preferences.all.forEach { (path, value) ->
                val json = JSONObject(value as String)
                put(
                    path,
                    FolderListingCache.Entry(
                        decodeFiles(json.getJSONArray("files")),
                        json.getLong("fetchedAt"),
                    ),
                    FolderListingCache.Usage(
                        json.optInt("visitCount", 1),
                        json.optLong("lastAccessedAt", json.getLong("fetchedAt")),
                    ),
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        preferences.edit().clear().commit()
    }

    private fun legacyPreferences() = context.getSharedPreferences("folder-listings-$identity", Context.MODE_PRIVATE)

    private companion object {
        const val TABLE = "directory_listings"
        const val PATH = "path"
        const val LISTING = "listing"
        const val FETCHED_AT = "fetched_at"
        const val VISIT_COUNT = "visit_count"
        const val LAST_ACCESSED_AT = "last_accessed_at"
    }
}

private class FolderListingDatabase(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE directory_listings (
                path TEXT PRIMARY KEY NOT NULL,
                listing BLOB NOT NULL,
                fetched_at INTEGER NOT NULL,
                visit_count INTEGER NOT NULL,
                last_accessed_at INTEGER NOT NULL
            )""".trimIndent(),
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

fun persistentFolderListingCache(context: Context, account: Account): FolderListingCache {
    val store = SqliteFolderListingStore(context, account)
    return FolderListingCache(store = store, entrySize = store::sizeBytes, usageAffectsSize = false)
}

internal fun encodeFolderListing(files: List<CloudFile>): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).bufferedWriter().use { writer ->
        writer.write(JSONArray(files.map(::encodeFile)).toString())
    }
    return output.toByteArray()
}

internal fun decodeFolderListing(bytes: ByteArray): List<CloudFile> = GZIPInputStream(bytes.inputStream())
    .bufferedReader()
    .use { reader -> decodeFiles(JSONArray(reader.readText())) }

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

private fun decodeFiles(files: JSONArray): List<CloudFile> = files.objects().map { json ->
    CloudFile(
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
}

internal fun Account.cacheIdentity(): String = MessageDigest.getInstance("SHA-256")
    .digest("${NextcloudPath.normalizeServerUrl(serverUrl)}\u0000$username".toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun JSONObject.putNullable(name: String, value: String?) = put(name, value ?: JSONObject.NULL)
private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else getString(name)
private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
private fun JSONArray.ints() = (0 until length()).map(::getInt)
