package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.CloudFile
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewRenameTest {
    @Test
    fun `renaming preserves metadata while updating preview identity`() {
        val original = CloudFile(
            name = "old.jpg",
            path = "Photos/old.jpg",
            isFolder = false,
            size = 42,
            mimeType = "image/jpeg",
            modifiedAt = "date",
            etag = "etag",
            fileId = "7",
        )

        val renamed = original.renamedTo("new.jpg")

        assertEquals("new.jpg", renamed.name)
        assertEquals("Photos/new.jpg", renamed.path)
        assertEquals(original.copy(name = "new.jpg", path = "Photos/new.jpg"), renamed)
    }
}
