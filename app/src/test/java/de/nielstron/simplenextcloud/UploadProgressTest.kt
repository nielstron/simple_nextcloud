package de.nielstron.simplenextcloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadProgressTest {
    @Test
    fun `folder progress includes completed files and bytes from the active nested file`() {
        val files = listOf(
            UploadFileProgress("small.txt", "Folder/small.txt", 100, status = UploadStatus.COMPLETED),
            UploadFileProgress("Nested/large.bin", "Folder/Nested/large.bin", 900, 400, UploadStatus.UPLOADING),
        )
        assertEquals(0.5f, files.uploadFraction()!!, 0.0001f)
        assertEquals(1f, files.map { it.copy(status = UploadStatus.COMPLETED) }.uploadFraction()!!, 0.0001f)
    }

    @Test
    fun `unknown sizes and zero byte files use confirmed file counts`() {
        val files = listOf(
            UploadFileProgress("empty", "Folder/empty", 0, status = UploadStatus.COMPLETED),
            UploadFileProgress("unknown", "Folder/unknown", -1, 2_000, UploadStatus.UPLOADING),
        )
        assertEquals(0.5f, files.uploadFraction()!!, 0.0001f)
        assertEquals(0.5f, files.map { it.copy(size = 0) }.uploadFraction()!!, 0.0001f)
        assertNull(emptyList<UploadFileProgress>().uploadFraction())
    }

    @Test
    fun `failure retains partial byte progress without marking files completed`() {
        val files = listOf(
            UploadFileProgress("first", "Folder/first", 100, 100, UploadStatus.COMPLETED),
            UploadFileProgress("second", "Folder/second", 100, 50, UploadStatus.FAILED),
            UploadFileProgress("third", "Folder/third", 100),
        )
        assertEquals(0.5f, files.uploadFraction()!!, 0.0001f)
        assertEquals(1, files.count { it.status == UploadStatus.COMPLETED })
    }
}
