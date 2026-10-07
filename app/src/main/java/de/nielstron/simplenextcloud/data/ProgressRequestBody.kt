package de.nielstron.simplenextcloud.data

import okhttp3.RequestBody
import okio.BufferedSink
import okio.Buffer
import okio.ForwardingSink
import okio.buffer

internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Long) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun isOneShot() = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        var sent = 0L
        var lastReportedAt = System.nanoTime()
        onProgress(0)
        val countingSink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                sent += byteCount
                val now = System.nanoTime()
                if (now - lastReportedAt >= 100_000_000L) {
                    onProgress(sent)
                    lastReportedAt = now
                }
            }
        }.buffer()
        delegate.writeTo(countingSink)
        countingSink.flush()
        onProgress(sent)
    }
}
