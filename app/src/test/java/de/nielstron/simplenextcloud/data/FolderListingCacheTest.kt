package de.nielstron.simplenextcloud.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderListingCacheTest {
    private var now = 1_000L
    private val file = CloudFile("Work", "Work", true, 0, null, null, null)

    @Test
    fun `stores copied metadata`() {
        val source = mutableListOf(file)
        val cache = FolderListingCache(clock = { now })
        cache.put("/", source)
        source.clear()

        assertEquals(listOf(file), cache.get("")?.files)
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
    fun `keeps every visited directory by default`() {
        val cache = FolderListingCache(clock = { now })

        repeat(50) { index ->
            val path = "folder-$index"
            cache.recordVisit(path)
            cache.put(path, listOf(file.copy(path = "$path/Work")))
        }

        repeat(50) { index ->
            assertEquals("folder-$index/Work", cache.get("folder-$index")?.files?.single()?.path)
        }
    }

    @Test
    fun `restores listings through a new cache instance`() {
        val entries = mutableMapOf<String, FolderListingCache.Entry>()
        val store = object : FolderListingStore {
            override fun load() = entries.toMap()
            override fun loadUsage() = emptyMap<String, FolderListingCache.Usage>()
            override fun put(
                path: String,
                entry: FolderListingCache.Entry,
                usage: FolderListingCache.Usage,
            ) {
                entries[path] = entry
            }
            override fun recordVisit(path: String, usage: FolderListingCache.Usage) = Unit
            override fun remove(path: String) {
                entries.remove(path)
            }
            override fun clear() {
                entries.clear()
            }
        }

        FolderListingCache(store = store, clock = { now }).put("Projects", listOf(file))
        val recreatedCache = FolderListingCache(store = store, clock = { now })

        assertEquals(listOf(file), recreatedCache.get("Projects")?.files)
    }

    @Test
    fun `evicts least recently used listings when storage budget is exceeded`() {
        val cache = FolderListingCache(
            maxStorageBytes = 100,
            entrySize = { _, _, _ -> 40 },
            clock = { now },
        )
        cache.put("old", listOf(file))
        cache.put("kept", listOf(file))
        cache.get("old")
        cache.put("new", listOf(file))

        assertEquals(listOf(file), cache.get("old")?.files)
        assertNull(cache.get("kept"))
        assertEquals(listOf(file), cache.get("new")?.files)
    }

    @Test
    fun `eviction weighs frequency and recency equally`() {
        val cache = FolderListingCache(
            maxStorageBytes = 100,
            entrySize = { _, _, _ -> 40 },
            clock = { now },
        )
        cache.put("frequent-old", listOf(file))
        repeat(4) { cache.recordVisit("frequent-old") }
        now += 1
        cache.put("rare-recent", listOf(file))
        cache.recordVisit("rare-recent")
        now += 1
        cache.put("new", listOf(file))

        assertEquals(listOf(file), cache.get("frequent-old")?.files)
        assertNull(cache.get("rare-recent"))
        assertEquals(listOf(file), cache.get("new")?.files)
    }
}
