package ch.niels.goodnextcloud

import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import ch.niels.goodnextcloud.data.Account
import ch.niels.goodnextcloud.data.AccountStore
import ch.niels.goodnextcloud.data.CloudFile
import ch.niels.goodnextcloud.data.DocumentIds
import ch.niels.goodnextcloud.data.NextcloudClient
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class NextcloudDocumentsProvider : DocumentsProvider() {
    private val client = NextcloudClient()

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: ROOT_COLUMNS
        val cursor = MatrixCursor(columns)
        val account = context?.let(::AccountStore)?.load() ?: return cursor
        cursor.newRow().apply {
            addColumn(columns, Root.COLUMN_ROOT_ID, ROOT_ID)
            addColumn(columns, Root.COLUMN_DOCUMENT_ID, DocumentIds.ROOT)
            addColumn(columns, Root.COLUMN_TITLE, "Nextcloud")
            addColumn(columns, Root.COLUMN_SUMMARY, account.serverUrl)
            addColumn(columns, Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_SUPPORTS_CREATE)
            addColumn(columns, Root.COLUMN_MIME_TYPES, "*/*")
            addColumn(columns, Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        return MatrixCursor(columns).also { cursor -> addDocument(cursor, columns, document(documentId)) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: DOCUMENT_COLUMNS
        val cursor = MatrixCursor(columns)
        val account = account()
        remote { client.list(account, DocumentIds.toPath(parentDocumentId)) }
            .forEach { addDocument(cursor, columns, it) }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = document(documentId)
        if (file.isFolder) throw FileNotFoundException("Cannot open a folder")
        val cached = cacheFile(file)
        val truncates = mode == "w" || mode == "wt"
        if (!truncates && (!cached.exists() || cached.length() != file.size)) {
            signal?.throwIfCanceled()
            val temporary = File.createTempFile("download-", ".tmp", cached.parentFile)
            try {
                temporary.outputStream().use { output -> remote { client.download(account(), file, output) } }
                signal?.throwIfCanceled()
                if (!temporary.renameTo(cached)) {
                    temporary.copyTo(cached, overwrite = true)
                    temporary.delete()
                }
            } finally {
                temporary.delete()
            }
        } else if (truncates) {
            cached.parentFile?.mkdirs()
            cached.writeBytes(byteArrayOf())
        }
        val parsedMode = ParcelFileDescriptor.parseMode(mode)
        if (!mode.contains('w')) return ParcelFileDescriptor.open(cached, parsedMode)
        return ParcelFileDescriptor.open(cached, parsedMode, Handler(Looper.getMainLooper())) {
            uploads.execute {
                try {
                    client.upload(account(), file.path, cached, mimeType(file))
                    context?.contentResolver?.notifyChange(
                        android.provider.DocumentsContract.buildDocumentUri(AUTHORITY, documentId),
                        null,
                    )
                } catch (failure: Exception) {
                    Log.e("NextcloudDocuments", "Could not upload ${file.path} after editing", failure)
                }
            }
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parentPath = DocumentIds.toPath(parentDocumentId)
        val path = ch.niels.goodnextcloud.data.NextcloudPath.child(parentPath, displayName)
        remote {
            if (mimeType == Document.MIME_TYPE_DIR) {
                client.createFolder(account(), path)
            } else {
                val empty = File.createTempFile("new-document-", ".tmp", requireNotNull(context).cacheDir)
                try {
                    client.upload(account(), path, empty, mimeType)
                } finally {
                    empty.delete()
                }
            }
        }
        notifyChildren(parentDocumentId)
        return DocumentIds.fromPath(path)
    }

    override fun deleteDocument(documentId: String) {
        val file = document(documentId)
        remote { client.delete(account(), file) }
        notifyChildren(DocumentIds.fromPath(file.path.substringBeforeLast('/', "")))
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val file = document(documentId)
        remote { client.rename(account(), file, displayName) }
        val newPath = ch.niels.goodnextcloud.data.NextcloudPath.child(file.path.substringBeforeLast('/', ""), displayName)
        notifyChildren(DocumentIds.fromPath(file.path.substringBeforeLast('/', "")))
        return DocumentIds.fromPath(newPath)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): android.content.res.AssetFileDescriptor {
        val file = document(documentId)
        if (!mimeType(file).startsWith("image/")) throw FileNotFoundException("No thumbnail")
        signal?.throwIfCanceled()
        val thumbnail = File(requireNotNull(context).cacheDir, "documents/thumbnails/${cacheKey(file)}.preview")
        if (!thumbnail.exists()) {
            thumbnail.parentFile?.mkdirs()
            thumbnail.writeBytes(remote { client.preview(account(), file) })
        }
        val descriptor = ParcelFileDescriptor.open(thumbnail, ParcelFileDescriptor.MODE_READ_ONLY)
        return android.content.res.AssetFileDescriptor(descriptor, 0, thumbnail.length())
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        DocumentIds.isChild(parentDocumentId, documentId)

    private fun document(documentId: String): CloudFile {
        val path = DocumentIds.toPath(documentId)
        if (path.isEmpty()) return CloudFile("Nextcloud", "", true, 0, null, null, null)
        val parent = path.substringBeforeLast('/', "")
        return remote { client.list(account(), parent) }.firstOrNull { it.path == path }
            ?: throw FileNotFoundException(path)
    }

    private fun addDocument(cursor: MatrixCursor, columns: Array<out String>, file: CloudFile) {
        cursor.newRow().apply {
            addColumn(columns, Document.COLUMN_DOCUMENT_ID, DocumentIds.fromPath(file.path))
            addColumn(columns, Document.COLUMN_DISPLAY_NAME, file.name)
            addColumn(columns, Document.COLUMN_MIME_TYPE, mimeType(file))
            addColumn(columns, Document.COLUMN_SIZE, if (file.isFolder) null else file.size)
            addColumn(columns, Document.COLUMN_LAST_MODIFIED, modifiedMillis(file.modifiedAt))
            addColumn(
                columns,
                Document.COLUMN_FLAGS,
                if (file.isFolder) {
                    Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
                } else {
                    Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
                        if (mimeType(file).startsWith("image/")) Document.FLAG_SUPPORTS_THUMBNAIL else 0
                },
            )
        }
    }

    private fun account(): Account = context?.let(::AccountStore)?.load()
        ?: throw FileNotFoundException("Connect a Nextcloud account in the app first")

    private fun cacheFile(file: CloudFile): File {
        val directory = File(requireNotNull(context).cacheDir, "documents/files").apply { mkdirs() }
        val extension = file.name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        return File(directory, cacheKey(file) + extension)
    }

    private fun cacheKey(file: CloudFile): String = MessageDigest.getInstance("SHA-256")
        .digest("${file.path}\u0000${file.etag}".toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun mimeType(file: CloudFile): String {
        if (file.isFolder) return Document.MIME_TYPE_DIR
        return file.mimeType ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
    }

    private fun modifiedMillis(value: String?): Long? = value?.let {
        runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun <T> remote(block: () -> T): T = try {
        block()
    } catch (failure: FileNotFoundException) {
        throw failure
    } catch (failure: Exception) {
        throw FileNotFoundException(failure.message).apply { initCause(failure) }
    }

    private fun notifyChildren(parentDocumentId: String) {
        context?.contentResolver?.notifyChange(
            android.provider.DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId),
            null,
        )
    }

    private fun MatrixCursor.RowBuilder.addColumn(columns: Array<out String>, column: String, value: Any?) {
        if (column in columns) add(column, value)
    }

    private companion object {
        const val ROOT_ID = "nextcloud"
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".documents"
        val uploads = Executors.newSingleThreadExecutor()
        val ROOT_COLUMNS = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_ICON,
        )
        val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
        )
    }
}
