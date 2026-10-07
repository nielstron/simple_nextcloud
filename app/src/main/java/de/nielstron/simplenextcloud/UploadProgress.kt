package de.nielstron.simplenextcloud

data class UploadFileProgress(
    val relativePath: String,
    val targetPath: String,
    val size: Long,
    val bytesSent: Long = 0,
    val status: UploadStatus = UploadStatus.QUEUED,
    val error: String? = null,
)

/** Unknown sizes and empty files use file counts rather than an invented byte total. */
internal fun List<UploadFileProgress>.uploadFraction(): Float? {
    if (isEmpty()) return null
    val totalBytes = sumOf { it.size.coerceAtLeast(0) }
    return if (all { it.size >= 0 } && totalBytes > 0) {
        (sumOf { if (it.status == UploadStatus.COMPLETED) it.size else it.bytesSent.coerceAtMost(it.size) }
            .toDouble() / totalBytes).toFloat()
    } else {
        count { it.status == UploadStatus.COMPLETED }.toFloat() / size
    }
}
