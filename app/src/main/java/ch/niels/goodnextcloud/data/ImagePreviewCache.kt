package ch.niels.goodnextcloud.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

class ImagePreviewCache(
    rootDirectory: File,
    accountIdentity: String,
    private val maxEntries: Int = 5,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val directory = File(rootDirectory, accountIdentity).apply { mkdirs() }

    init {
        trim()
    }

    @Synchronized
    fun get(file: CloudFile): ByteArray? {
        val cached = cacheFile(file)
        if (!cached.exists()) return null
        check(cached.setLastModified(clock()))
        return cached.readBytes()
    }

    @Synchronized
    fun peek(file: CloudFile): ByteArray? = cacheFile(file).takeIf(File::exists)?.readBytes()

    @Synchronized
    fun put(file: CloudFile, bytes: ByteArray) {
        require(file.mimeType?.startsWith("image/") == true)
        val pathPrefix = "${digest(file.path)}-"
        requireNotNull(directory.listFiles())
            .filter { it.name.startsWith(pathPrefix) }
            .filterNot { it.name == cacheFile(file).name }
            .forEach { check(it.delete()) }
        val target = cacheFile(file)
        val temporary = File.createTempFile("preview-", ".tmp", directory)
        temporary.writeBytes(bytes)
        check(temporary.renameTo(target))
        check(target.setLastModified(clock()))
        trim()
    }

    @Synchronized
    fun clear() {
        requireNotNull(directory.listFiles()).forEach { check(it.delete()) }
        check(directory.delete())
    }

    private fun cacheFile(file: CloudFile): File {
        val version = file.etag ?: "${file.modifiedAt}\u0000${file.size}"
        return File(directory, "${digest(file.path)}-${digest(version)}.preview")
    }

    private fun trim() {
        requireNotNull(directory.listFiles())
            .filter { it.extension == "preview" }
            .sortedWith(compareBy<File>(File::lastModified).thenBy(File::getName))
            .dropLast(maxEntries)
            .forEach { check(it.delete()) }
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        fun forAccount(context: Context, account: Account) = ImagePreviewCache(
            File(context.cacheDir, "image-previews"),
            account.cacheIdentity(),
        )
    }
}
