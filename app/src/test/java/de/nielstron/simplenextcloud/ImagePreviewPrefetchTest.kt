package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.CloudFile
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewPrefetchTest {
    private val first = image("one.jpg")
    private val second = image("two.jpg")
    private val third = image("three.jpg")

    @Test
    fun `selects both adjacent images without wrapping`() {
        val files = listOf(first, CloudFile("notes.txt", "notes.txt", false, 1, "text/plain", null, null), second)

        assertEquals(listOf(second), adjacentPreviewFiles(files, first))
        assertEquals(listOf(first), adjacentPreviewFiles(files, second))
    }

    @Test
    fun `uses the viewer order`() {
        assertEquals(listOf(third, second), adjacentPreviewFiles(listOf(third, first, second), first))
    }

    @Test
    fun `videos participate in viewer ordering`() {
        val video = CloudFile("clip.mp4", "clip.mp4", false, 20, "video/mp4", null, "video")
        val text = CloudFile("notes.txt", "notes.txt", false, 10, "text/plain", null, null)

        assertEquals(listOf(first, video, second), previewableFiles(listOf(first, text, video, second)))
        assertEquals(listOf(first, second), adjacentPreviewFiles(listOf(first, video, second), video))
    }

    private fun image(name: String) = CloudFile(name, name, false, 1, "image/jpeg", null, name)
}
