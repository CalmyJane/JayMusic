package com.calmyjane.jaymusic.core.cache

/** A song that is (completely or partially) stored on the phone. */
data class CachedSong(
    val id: String,
    val bytes: Long,
    /** 0 = unrated, 1..5 stars. */
    val rating: Int,
    val lastPlayedAt: Long = 0,
    /** False if only part of the file is stored, e.g. a song that was skipped while streaming. */
    val complete: Boolean = true,
)

data class EvictionPlan(
    /** Songs to delete, in order. */
    val evict: List<String>,
    /** Whether deleting [evict] makes enough room. If false nothing should be deleted. */
    val fits: Boolean,
) {
    companion object {
        val NOTHING = EvictionPlan(emptyList(), fits = true)
        val IMPOSSIBLE = EvictionPlan(emptyList(), fits = false)
    }
}

/**
 * Decides which songs to delete when the storage budget is exceeded.
 *
 * Worst rated songs go first. Unrated songs rank between 2 and 3 stars, so songs you marked as
 * bad are removed before songs you have not rated, and songs you like are kept the longest. Within
 * the same rating, partial downloads go first, then the songs not played for the longest time.
 */
object StoragePolicy {

    const val UNRATED_SCORE = 2.5

    fun score(rating: Int): Double = if (rating in 1..5) rating.toDouble() else UNRATED_SCORE

    val evictionOrder: Comparator<CachedSong> =
        compareBy<CachedSong> { score(it.rating) }
            .thenBy { it.complete }
            .thenBy { it.lastPlayedAt }
            .thenByDescending { it.bytes }

    /**
     * Plans how to make room for [incomingBytes] more bytes.
     *
     * @param budgetBytes maximum total size of the cache.
     * @param protectedIds songs that must not be deleted (playing now or queued next).
     * @param onlyBelowScore if set, only songs scoring strictly lower than this may be deleted.
     *   Used by background sync so it never replaces a song with an equally good or worse one,
     *   which would make the cache churn forever.
     */
    fun planEviction(
        cached: Collection<CachedSong>,
        budgetBytes: Long,
        incomingBytes: Long,
        protectedIds: Set<String> = emptySet(),
        onlyBelowScore: Double? = null,
    ): EvictionPlan {
        val used = cached.sumOf { it.bytes }
        var needed = used + incomingBytes - budgetBytes
        if (needed <= 0) return EvictionPlan.NOTHING
        if (incomingBytes > budgetBytes) return EvictionPlan.IMPOSSIBLE

        val evict = mutableListOf<String>()
        for (song in cached.sortedWith(evictionOrder)) {
            if (song.id in protectedIds) continue
            if (onlyBelowScore != null && score(song.rating) >= onlyBelowScore) break
            evict += song.id
            needed -= song.bytes
            if (needed <= 0) return EvictionPlan(evict, fits = true)
        }
        return EvictionPlan.IMPOSSIBLE
    }

    /**
     * Songs to delete so the cache fits into [budgetBytes] again, e.g. after the user lowered the
     * limit. Unlike [planEviction] this returns as much as possible even if the budget cannot be
     * reached because of protected songs.
     */
    fun shrinkTo(
        cached: Collection<CachedSong>,
        budgetBytes: Long,
        protectedIds: Set<String> = emptySet(),
    ): List<String> {
        var excess = cached.sumOf { it.bytes } - budgetBytes
        if (excess <= 0) return emptyList()
        val evict = mutableListOf<String>()
        for (song in cached.sortedWith(evictionOrder)) {
            if (song.id in protectedIds) continue
            evict += song.id
            excess -= song.bytes
            if (excess <= 0) break
        }
        return evict
    }
}
