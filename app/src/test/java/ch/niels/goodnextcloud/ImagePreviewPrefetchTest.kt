package ch.niels.goodnextcloud

import ch.niels.goodnextcloud.data.CloudFile
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewPrefetchTest {
    private val first = image("one.jpg")
    private val second = image("two.jpg")
    private val third = image("three.jpg")

    @Test
    fun `selects both adjacent images without wrapping`() {
        val files = listOf(first, CloudFile("notes.txt", "notes.txt", false, 1, "text/plain", null, null), second)

        assertEquals(listOf(second), adjacentImages(files, first))
        assertEquals(listOf(first), adjacentImages(files, second))
    }

    @Test
    fun `uses the viewer order`() {
        assertEquals(listOf(third, second), adjacentImages(listOf(third, first, second), first))
    }

    private fun image(name: String) = CloudFile(name, name, false, 1, "image/jpeg", null, name)
}
