package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.CloudFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPrefetchTest {
    @Test
    fun `prefetches every distinct visible folder`() {
        val folderA = CloudFile("A", "A", true, 0, null, null, null)
        val folderB = CloudFile("B", "B", true, 0, null, null, null)
        val file = CloudFile("notes.txt", "notes.txt", false, 10, "text/plain", null, null)

        assertEquals(listOf("A", "B"), foldersToPrefetch(listOf(folderA, file, folderB, folderA)))
    }
}
