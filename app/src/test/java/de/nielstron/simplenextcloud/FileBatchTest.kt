package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.CloudFile
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBatchTest {
    private fun file(path: String, folder: Boolean = false) = CloudFile(
        path.substringAfterLast('/'), path, folder, 0, null, null, null,
    )

    @Test
    fun `failed file does not prevent remaining files from completing`() {
        val files = listOf(file("a"), file("b"), file("c"))
        val attempted = mutableListOf<CloudFile>()
        val failure = IllegalStateException("Locked")
        val result = performFileBatch(files) {
            attempted += it
            if (it.name == "b") throw failure
        }
        assertEquals(files, attempted)
        assertEquals(listOf(files[0], files[2]), result.completed)
        assertEquals(listOf(files[1] to failure), result.failures)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation stops the batch`() {
        performFileBatch(listOf(file("a"))) { throw CancellationException() }
    }

    @Test
    fun `paste rejects source parents and descendants of any selected folder`() {
        val files = listOf(file("Photos", true), file("Documents/readme.txt"))
        assertFalse(canPasteFiles(files, ""))
        assertFalse(canPasteFiles(files, "Documents"))
        assertFalse(canPasteFiles(files, "Photos"))
        assertFalse(canPasteFiles(files, "Photos/Trip"))
        assertTrue(canPasteFiles(files, "PhotosBackup"))
        assertFalse(canPasteFiles(emptyList(), "Backup"))
    }
}
