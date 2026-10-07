package de.nielstron.simplenextcloud.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.ForwardingSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProgressRequestBodyTest {
    @Test
    fun `reports actual bytes and preserves body metadata and contents`() {
        val payload = ByteArray(32_001) { (it % 256).toByte() }
        val updates = mutableListOf<Long>()
        val body = ProgressRequestBody(payload.toRequestBody("application/octet-stream".toMediaType()), updates::add)
        val output = Buffer()

        body.writeTo(output)

        assertEquals(payload.size.toLong(), body.contentLength())
        assertEquals("application/octet-stream", body.contentType().toString())
        assertTrue(payload.contentEquals(output.readByteArray()))
        assertEquals(0L, updates.first())
        assertEquals(payload.size.toLong(), updates.last())
        assertTrue(updates.zipWithNext().all { (previous, next) -> previous <= next })
    }

    @Test
    fun `empty uploads still report zero and retries reset their counters`() {
        val updates = mutableListOf<Long>()
        val body = ProgressRequestBody("abc".toRequestBody(), updates::add)
        repeat(2) { body.writeTo(Buffer()) }
        assertEquals(listOf(0L, 3L, 0L, 3L), updates)

        updates.clear()
        ProgressRequestBody(ByteArray(0).toRequestBody(), updates::add).writeTo(Buffer())
        assertEquals(listOf(0L, 0L), updates)
    }

    @Test
    fun `a failed write never reports full upload completion`() {
        val updates = mutableListOf<Long>()
        val body = ProgressRequestBody(ByteArray(20_000).toRequestBody(), updates::add)
        val sink = object : ForwardingSink(Buffer()) {
            override fun write(source: Buffer, byteCount: Long) {
                throw IOException("Connection lost")
            }
        }.buffer()
        try {
            body.writeTo(sink)
            throw AssertionError("Expected a write failure")
        } catch (expected: IOException) {
            assertEquals("Connection lost", expected.message)
        }
        assertEquals(listOf(0L), updates)
    }
}
