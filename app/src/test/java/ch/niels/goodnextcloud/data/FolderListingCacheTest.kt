package ch.niels.goodnextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderListingCacheTest {
    private var now = 1_000L
    private val file = CloudFile("Work", "Work", true, 0, null, null, null)

    @Test
    fun `stores copied metadata and expires freshness`() {
        val source = mutableListOf(file)
        val cache = FolderListingCache(freshForMillis = 100, clock = { now })
        cache.put("/", source)
        source.clear()

        assertEquals(listOf(file), cache.get("")?.files)
        assertTrue(cache.isFresh("/"))
        now += 101
        assertFalse(cache.isFresh(""))
    }

    @Test
    fun `evicts the least recently used listing`() {
        val cache = FolderListingCache(maxEntries = 2, clock = { now })
        cache.put("one", listOf(file))
        cache.put("two", listOf(file))
        cache.get("one")
        cache.put("three", listOf(file))

        assertNull(cache.get("two"))
        assertEquals(listOf(file), cache.get("one")?.files)
    }

    @Test
    fun `prefers frequently visited stale folders`() {
        val cache = FolderListingCache(freshForMillis = 10, clock = { now })
        cache.put("frequent", listOf(file))
        cache.put("recent", listOf(file))
        cache.recordVisit("frequent")
        cache.recordVisit("frequent")
        now += 1
        cache.recordVisit("recent")
        now += 11

        assertEquals(listOf("frequent", "recent"), cache.preferred(listOf("recent", "frequent"), 2))
    }
}
