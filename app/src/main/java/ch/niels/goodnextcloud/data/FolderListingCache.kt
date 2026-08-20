package ch.niels.goodnextcloud.data

/**
 * A cache of directory listings, optionally backed by persistent storage.
 *
 * It deliberately stores only [CloudFile] metadata. File bodies are never passed to or retained by
 * this class. By default, entries with the lowest equal-weight frequency/recency score are evicted
 * above 5 MiB; account disconnection explicitly clears the remaining entries.
 */
class FolderListingCache(
    private val maxEntries: Int = Int.MAX_VALUE,
    private val maxStorageBytes: Long = DEFAULT_MAX_STORAGE_BYTES,
    private val freshForMillis: Long = 2 * 60 * 1_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val store: FolderListingStore = FolderListingStore.None,
    private val entrySize: (String, Entry, Usage) -> Long = { path, entry, usage ->
        estimatedSize(path, entry, usage)
    },
    private val usageAffectsSize: Boolean = true,
) {
    data class Entry(val files: List<CloudFile>, val fetchedAt: Long)

    data class Usage(val count: Int = 0, val lastVisitedAt: Long = 0)

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true).apply {
        putAll(store.load())
    }
    private val visits = store.loadUsage().toMutableMap()
    private val entrySizes = entries.mapValuesTo(mutableMapOf()) { (path, entry) ->
        entrySize(path, entry, visits[path] ?: Usage())
    }
    private var storageBytes = entrySizes.values.sum()

    init {
        trimToLimits()
    }

    @Synchronized
    fun get(path: String): Entry? {
        val normalizedPath = normalize(path)
        return entries[normalizedPath]?.also { recordVisit(normalizedPath) }
    }

    @Synchronized
    fun put(path: String, files: List<CloudFile>) {
        val normalizedPath = normalize(path)
        val entry = Entry(files.toList(), clock())
        storageBytes -= entrySizes.remove(normalizedPath) ?: 0
        entries[normalizedPath] = entry
        val usage = visits.getOrPut(normalizedPath) { Usage(lastVisitedAt = clock()) }
        val size = entrySize(normalizedPath, entry, usage)
        entrySizes[normalizedPath] = size
        storageBytes += size
        store.put(normalizedPath, entry, usage)
        trimToLimits()
    }

    @Synchronized
    fun isFresh(path: String): Boolean = entries.entries
        .firstOrNull { it.key == normalize(path) }
        ?.value
        ?.let { clock() - it.fetchedAt < freshForMillis } == true

    @Synchronized
    fun paths(): Set<String> = entries.keys.toSet()

    @Synchronized
    fun remove(path: String) {
        val normalizedPath = normalize(path)
        entries.remove(normalizedPath)
        storageBytes -= entrySizes.remove(normalizedPath) ?: 0
        visits.remove(normalizedPath)
        store.remove(normalizedPath)
    }

    @Synchronized
    fun recordVisit(path: String) {
        val normalizedPath = normalize(path)
        val previous = visits[normalizedPath] ?: Usage()
        val usage = previous.copy(count = previous.count + 1, lastVisitedAt = clock())
        visits[normalizedPath] = usage
        store.recordVisit(normalizedPath, usage)
        if (usageAffectsSize) entries.entries.firstOrNull { it.key == normalizedPath }?.value?.let { entry ->
            storageBytes -= entrySizes.remove(normalizedPath) ?: 0
            val size = entrySize(normalizedPath, entry, usage)
            entrySizes[normalizedPath] = size
            storageBytes += size
            trimToLimits()
        }
    }

    /** Ranks known folders by visit frequency, breaking ties by recency. */
    @Synchronized
    fun preferred(paths: Collection<String>, limit: Int): List<String> = paths
        .map(::normalize)
        .distinct()
        .filterNot(::isFresh)
        .sortedWith(
            compareByDescending<String> { visits[it]?.count ?: 0 }
                .thenByDescending { visits[it]?.lastVisitedAt ?: 0 },
        )
        .take(limit)

    @Synchronized
    fun clear() {
        entries.clear()
        entrySizes.clear()
        storageBytes = 0
        visits.clear()
        store.clear()
    }

    private fun trimToLimits() {
        while (entries.size > maxEntries || storageBytes > maxStorageBytes) {
            val evictedPath = evictionCandidate()
            entries.remove(evictedPath)
            storageBytes -= entrySizes.remove(evictedPath) ?: 0
            visits.remove(evictedPath)
            store.remove(evictedPath)
        }
    }

    private fun evictionCandidate(): String {
        val paths = entries.keys.toList()
        val maximumFrequency = paths.maxOf { visits[it]?.count ?: 0 }.coerceAtLeast(1)
        val recencyOrder = paths
            .sortedBy { visits[it]?.lastVisitedAt ?: 0 }
            .withIndex()
            .associate { (rank, path) -> path to rank.toDouble() / (paths.size - 1).coerceAtLeast(1) }
        return paths.minBy { path ->
            val frequencyScore = (visits[path]?.count ?: 0).toDouble() / maximumFrequency
            0.5 * frequencyScore + 0.5 * requireNotNull(recencyOrder[path])
        }
    }

    private fun normalize(path: String) = path.trim('/')

    companion object {
        const val DEFAULT_MAX_STORAGE_BYTES = 5L * 1024 * 1024

        private fun estimatedSize(path: String, entry: Entry, usage: Usage): Long =
            path.toByteArray().size.toLong() + entry.files.sumOf { file ->
                listOfNotNull(
                    file.name,
                    file.path,
                    file.mimeType,
                    file.modifiedAt,
                    file.etag,
                    file.fileId,
                    file.ownerId,
                    file.ownerDisplayName,
                    file.mountType,
                ).sumOf { it.toByteArray().size.toLong() } + 64 + file.shareTypes.size * 4L
            } + usage.count.toString().length + usage.lastVisitedAt.toString().length
    }
}

interface FolderListingStore {
    fun load(): Map<String, FolderListingCache.Entry>
    fun loadUsage(): Map<String, FolderListingCache.Usage>
    fun put(path: String, entry: FolderListingCache.Entry, usage: FolderListingCache.Usage)
    fun recordVisit(path: String, usage: FolderListingCache.Usage)
    fun remove(path: String)
    fun clear()

    object None : FolderListingStore {
        override fun load() = emptyMap<String, FolderListingCache.Entry>()
        override fun loadUsage() = emptyMap<String, FolderListingCache.Usage>()
        override fun put(path: String, entry: FolderListingCache.Entry, usage: FolderListingCache.Usage) = Unit
        override fun recordVisit(path: String, usage: FolderListingCache.Usage) = Unit
        override fun remove(path: String) = Unit
        override fun clear() = Unit
    }
}
