package ch.niels.goodnextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderListingCodecTest {
    @Test
    fun `compressed listing round trips all metadata`() {
        val files = listOf(
            CloudFile(
                name = "Report.pdf",
                path = "Work/Report.pdf",
                isFolder = false,
                size = 1234,
                mimeType = "application/pdf",
                modifiedAt = "Thu, 20 Aug 2026 12:00:00 GMT",
                etag = "abc",
                fileId = "42",
                ownerId = "niels",
                ownerDisplayName = "Niels",
                mountType = "shared",
                shareTypes = setOf(0, 3),
            ),
        )

        assertEquals(files, decodeFolderListing(encodeFolderListing(files)))
    }

    @Test
    fun `gzip removes repeated metadata overhead`() {
        val files = List(100) { index ->
            CloudFile("File $index", "Documents/File $index", false, index.toLong(), "text/plain", null, null)
        }
        val uncompressedBytes = files.sumOf { it.name.length + it.path.length + 100 }

        assertTrue(encodeFolderListing(files).size < uncompressedBytes / 2)
    }
}
