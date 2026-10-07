package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.NextcloudException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UploadRetryTest {
    @Test
    fun `dns connection timeout rate limit and server failures are transient`() {
        assertTrue(UnknownHostException("cloud.example.com").isTransientUploadFailure())
        assertTrue(SocketTimeoutException().isTransientUploadFailure())
        assertTrue(IOException(UnknownHostException()).isTransientUploadFailure())
        assertTrue(NextcloudException(429, "Too many requests").isTransientUploadFailure())
        assertTrue(NextcloudException(503, "Unavailable").isTransientUploadFailure())
        assertFalse(NextcloudException(403, "Forbidden").isTransientUploadFailure())
        assertFalse(IllegalArgumentException("bad source").isTransientUploadFailure())
        assertEquals(1, nextAutomaticUploadRetry(0, UnknownHostException()))
        assertNull(nextAutomaticUploadRetry(1, UnknownHostException()))
        assertNull(nextAutomaticUploadRetry(0, NextcloudException(403, "Forbidden")))
    }

    @Test
    fun `retry preserves completed folder files and resets unfinished progress`() {
        val item = UploadQueueItem(
            id = 7,
            name = "Photos",
            targetPath = "Uploads",
            isFolder = true,
            status = UploadStatus.FAILED,
            error = "Unable to resolve host",
            files = listOf(
                UploadFileProgress("done.jpg", "Uploads/Photos/done.jpg", 100, 100, UploadStatus.COMPLETED),
                UploadFileProgress("failed.jpg", "Uploads/Photos/failed.jpg", 200, 80, UploadStatus.FAILED, "DNS"),
                UploadFileProgress("waiting.jpg", "Uploads/Photos/waiting.jpg", 300),
            ),
        )

        val retry = item.readyForRetry()
        val files = requireNotNull(retry.files)

        assertEquals(UploadStatus.QUEUED, retry.status)
        assertNull(retry.error)
        assertEquals(UploadStatus.COMPLETED, files[0].status)
        assertEquals(100, files[0].bytesSent)
        assertEquals(UploadStatus.QUEUED, files[1].status)
        assertEquals(0, files[1].bytesSent)
        assertNull(files[1].error)
        assertEquals(UploadStatus.QUEUED, files[2].status)
    }
}
