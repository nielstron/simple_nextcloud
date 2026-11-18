package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentStoreTest {
    private fun folder(path: String) = CloudFile(path.substringAfterLast('/'), path, true, 0, null, null, null)
    private fun file(path: String) = CloudFile(path.substringAfterLast('/'), path, false, 1, "text/plain", null, null)
    private fun List<RecentEntry>.paths() = map { it.file.path }

    @Test
    fun `browsing down drops folders that were only passed through`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Documents"), RecentAction.OPENED, 1)
            .recorded(folder("Documents/Taxes"), RecentAction.OPENED, 2)

        assertEquals(listOf("Documents/Taxes"), recents.paths())
    }

    @Test
    fun `going up and into a sibling keeps both siblings`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Documents/Taxes"), RecentAction.OPENED, 1)
            .recorded(folder("Documents"), RecentAction.OPENED, 2)
            .recorded(folder("Documents/Housing"), RecentAction.OPENED, 3)

        assertEquals(listOf("Documents/Housing", "Documents/Taxes"), recents.paths())
    }

    @Test
    fun `an upload target survives browsing through it later`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Documents"), RecentAction.UPLOADED, 1)
            .recorded(folder("Documents"), RecentAction.OPENED, 2)
            .recorded(folder("Documents/Taxes"), RecentAction.OPENED, 3)

        assertEquals(listOf("Documents/Taxes", "Documents"), recents.paths())
        val documents = recents.single { it.file.path == "Documents" }
        assertEquals(RecentAction.OPENED, documents.action)
        assertEquals(true, documents.uploadedHere)
    }

    @Test
    fun `a folder whose name starts like another is not its ancestor`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Doc"), RecentAction.OPENED, 1)
            .recorded(folder("Documents/Taxes"), RecentAction.OPENED, 2)

        assertEquals(listOf("Documents/Taxes", "Doc"), recents.paths())
    }

    @Test
    fun `repeat use moves an entry to the front and folders and files are capped separately`() {
        var recents = emptyList<RecentEntry>()
        repeat(MAX_RECENT_FILES + 5) { recents = recents.recorded(file("f$it.txt"), RecentAction.OPENED, it.toLong()) }
        repeat(MAX_RECENT_FOLDERS + 5) { recents = recents.recorded(folder("d$it"), RecentAction.OPENED, 100L + it) }
        recents = recents.recorded(file("f24.txt"), RecentAction.OPENED, 1_000)

        assertEquals("f24.txt", recents.first().file.path)
        assertEquals(MAX_RECENT_FILES, recents.count { !it.file.isFolder })
        assertEquals(MAX_RECENT_FOLDERS, recents.count { it.file.isFolder })
        assertEquals(recents.sortedByDescending { it.at }, recents)
    }

    @Test
    fun `renaming a folder moves it and everything below`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Work"), RecentAction.UPLOADED, 1)
            .recorded(file("Work/Plan.pdf"), RecentAction.OPENED, 2)
            .recorded(folder("Workshop"), RecentAction.OPENED, 3)
            .rebased("Work", "Archive/Work 2025")

        assertEquals(setOf("Archive/Work 2025", "Archive/Work 2025/Plan.pdf", "Workshop"), recents.paths().toSet())
        assertEquals("Work 2025", recents.single { it.file.path == "Archive/Work 2025" }.file.name)
    }

    @Test
    fun `deleting a folder forgets it and everything below`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Work"), RecentAction.UPLOADED, 1)
            .recorded(file("Work/Plan.pdf"), RecentAction.OPENED, 2)
            .recorded(folder("Workshop"), RecentAction.OPENED, 3)
            .withoutTree("Work")

        assertEquals(listOf("Workshop"), recents.paths())
    }

    @Test
    fun `rebasePath only matches the folder itself and its descendants`() {
        assertEquals("B", rebasePath("A", "A", "B"))
        assertEquals("B/x/y", rebasePath("A/x/y", "A", "B"))
        assertEquals("C/B/x", rebasePath("/A/x/", "/A/", "C/B"))
        assertNull(rebasePath("AB/x", "A", "B"))
        assertNull(rebasePath("Z", "A", "B"))
    }

    @Test
    fun `recents round trip through json`() {
        val recents = emptyList<RecentEntry>()
            .recorded(folder("Work").copy(fileId = "12", favorite = true), RecentAction.UPLOADED, 1)
            .recorded(file("Work/Plan.pdf"), RecentAction.OPENED, 2)

        assertEquals(recents, decodeRecents(encodeRecents(recents)))
    }
}
