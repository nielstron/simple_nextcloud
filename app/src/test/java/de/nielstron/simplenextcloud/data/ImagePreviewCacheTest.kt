package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagePreviewCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var now = 1_000L

    @Test
    fun `keeps the most recently viewed images`() {
        val cache = ImagePreviewCache(temporaryFolder.root, "account", maxEntries = 2, clock = { now++ })
        val first = image("first.jpg", "one")
        val second = image("second.jpg", "two")
        val third = image("third.jpg", "three")
        cache.put(first, byteArrayOf(1))
        cache.put(second, byteArrayOf(2))
        assertArrayEquals(byteArrayOf(1), cache.get(first))
        cache.put(third, byteArrayOf(3))

        assertArrayEquals(byteArrayOf(1), cache.get(first))
        assertNull(cache.get(second))
        assertArrayEquals(byteArrayOf(3), cache.get(third))
    }

    @Test
    fun `etag changes invalidate an older preview`() {
        val cache = ImagePreviewCache(temporaryFolder.root, "account", clock = { now++ })
        cache.put(image("photo.jpg", "old"), byteArrayOf(1))

        assertNull(cache.get(image("photo.jpg", "new")))
    }

    private fun image(name: String, etag: String) =
        CloudFile(name, name, false, 10, "image/jpeg", null, etag)
}
