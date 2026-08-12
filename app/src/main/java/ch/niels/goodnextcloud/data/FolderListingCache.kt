package ch.niels.goodnextcloud.data

/**
 * A bounded, memory-only cache of directory listings.
 *
 * It deliberately stores only [CloudFile] metadata. File bodies are never passed to or retained by
 * this class. Entries disappear when the app process ends or the account is disconnected.
 */
class FolderListingCache(
    private val maxEntries: Int = 40,
    private val freshForMillis: Long = 2 * 60 * 1_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Entry(val files: List<CloudFile>, val fetchedAt: Long)

    private data class Visit(var count: Int = 0, var lastVisitedAt: Long = 0)

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val visits = mutableMapOf<String, Visit>()

    @Synchronized
    fun get(path: String): Entry? = entries[normalize(path)]

    @Synchronized
    fun put(path: String, files: List<CloudFile>) {
        entries[normalize(path)] = Entry(files.toList(), clock())
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.first().key)
        }
    }

    @Synchronized
    fun isFresh(path: String): Boolean = get(path)?.let { clock() - it.fetchedAt < freshForMillis } == true

    @Synchronized
    fun paths(): Set<String> = entries.keys.toSet()

    @Synchronized
    fun recordVisit(path: String) {
        val visit = visits.getOrPut(normalize(path)) { Visit() }
        visit.count += 1
        visit.lastVisitedAt = clock()
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
        visits.clear()
    }

    private fun normalize(path: String) = path.trim('/')
}
