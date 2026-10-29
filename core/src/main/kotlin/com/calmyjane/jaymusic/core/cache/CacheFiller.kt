package com.calmyjane.jaymusic.core.cache

import com.calmyjane.jaymusic.core.library.FolderRules
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** A song from the library that could be downloaded. */
data class LibrarySong(
    val id: String,
    val sizeBytes: Long,
    val rating: Int,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
)

/** Storage operations needed by [CacheFiller]; implemented by the app on top of the media cache. */
interface SongStore {
    /** Current content of the cache. */
    fun cachedSongs(): List<CachedSong>

    /** Maximum number of bytes the cache may use right now. */
    fun budgetBytes(): Long

    /** Songs that must not be deleted right now. */
    fun protectedIds(): Set<String>

    fun delete(id: String)

    /** Downloads the song completely. Returns false if it failed. */
    suspend fun download(id: String): Boolean
}

data class FillResult(val downloaded: Int, val evicted: Int, val failed: Int, val stoppedEarly: Boolean)

/**
 * Background sync: fills the phone with the best songs that fit into the storage budget.
 *
 * Songs are taken best-rated first (unrated count as 2.5 stars), then most played. When the cache
 * is full, a song is only downloaded if worse rated songs can be deleted to make room.
 */
class CacheFiller(private val store: SongStore) {

    fun candidates(library: List<LibrarySong>, rules: FolderRules): List<LibrarySong> {
        val cached = store.cachedSongs().filter { it.complete }.mapTo(HashSet()) { it.id }
        return library
            .filter { it.id !in cached && it.sizeBytes > 0 && !rules.isExcluded(it.id) }
            .sortedWith(fillOrder)
    }

    /**
     * Downloads [candidates] (see [candidates]) in order until the budget is used up, [maxSongs]
     * have been downloaded or [shouldContinue] returns false.
     */
    suspend fun fill(
        candidates: List<LibrarySong>,
        maxSongs: Int = Int.MAX_VALUE,
        shouldContinue: () -> Boolean = { true },
        onDownloaded: (count: Int) -> Unit = {},
    ): FillResult {
        var downloaded = 0
        var evicted = 0
        var failed = 0
        var consecutiveMisses = 0
        for (song in candidates) {
            coroutineContext.ensureActive()
            if (!shouldContinue() || downloaded >= maxSongs) {
                return FillResult(downloaded, evicted, failed, stoppedEarly = true)
            }
            val cached = store.cachedSongs()
            // A partial copy of this very song is replaced by the full download.
            val others = cached.filter { it.id != song.id }
            val plan = StoragePolicy.planEviction(
                cached = others,
                budgetBytes = store.budgetBytes(),
                incomingBytes = song.sizeBytes,
                protectedIds = store.protectedIds(),
                onlyBelowScore = StoragePolicy.score(song.rating),
            )
            if (!plan.fits) {
                // Candidates are sorted best first, so once several songs in a row do not fit,
                // the rest will not either (only smaller files might, which is not worth the scan).
                if (++consecutiveMisses >= MAX_CONSECUTIVE_MISSES) break
                continue
            }
            consecutiveMisses = 0
            plan.evict.forEach(store::delete)
            evicted += plan.evict.size
            if (store.download(song.id)) onDownloaded(++downloaded) else failed++
            if (failed >= MAX_FAILURES) return FillResult(downloaded, evicted, failed, stoppedEarly = true)
        }
        return FillResult(downloaded, evicted, failed, stoppedEarly = false)
    }

    companion object {
        const val MAX_CONSECUTIVE_MISSES = 20
        const val MAX_FAILURES = 5

        val fillOrder: Comparator<LibrarySong> =
            compareByDescending<LibrarySong> { StoragePolicy.score(it.rating) }
                .thenByDescending { it.playCount }
                .thenByDescending { it.lastPlayedAt }
                // Stable pseudo-random order for the rest so the cache is not filled alphabetically.
                .thenBy { it.id.hashCode() }
    }
}
