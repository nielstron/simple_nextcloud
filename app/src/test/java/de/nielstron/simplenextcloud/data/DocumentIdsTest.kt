package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentIdsTest {
    @Test
    fun `document IDs round trip paths with unicode and slashes`() {
        val path = "Photos/Zürich/夏.jpg"
        assertEquals(path, DocumentIds.toPath(DocumentIds.fromPath(path)))
        assertEquals(DocumentIds.ROOT, DocumentIds.fromPath("/"))
        assertEquals("", DocumentIds.toPath(DocumentIds.ROOT))
    }

    @Test
    fun `child checks respect path segment boundaries`() {
        assertTrue(DocumentIds.isChild(DocumentIds.ROOT, DocumentIds.fromPath("Photos/a.jpg")))
        assertTrue(DocumentIds.isChild(DocumentIds.fromPath("Photos"), DocumentIds.fromPath("Photos/Trips/a.jpg")))
        assertFalse(DocumentIds.isChild(DocumentIds.fromPath("Photo"), DocumentIds.fromPath("Photos/a.jpg")))
        assertFalse(DocumentIds.isChild(DocumentIds.fromPath("Photos"), DocumentIds.fromPath("Photos")))
    }
}
