package com.calmyjane.jaymusic.core.cache

import com.calmyjane.jaymusic.core.library.FolderRules
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheFillerTest {

    private class FakeStore(var budget: Long, private val sizes: Map<String, Long>, private val ratings: Map<String, Int>) : SongStore {
        val cache = LinkedHashMap<String, CachedSong>()
        val deleted = mutableListOf<String>()
        var failing = setOf<String>()
        override fun cachedSongs() = cache.values.toList()
        override fun budgetBytes() = budget
        override fun protectedIds() = emptySet<String>()
        override fun delete(id: String) { cache.remove(id); deleted += id }
        override suspend fun download(id: String): Boolean {
            if (id in failing) return false
            cache[id] = CachedSong(id, sizes.getValue(id), ratings.getValue(id))
            return true
        }
    }

    private val library = listOf(
        LibrarySong("/music/a.mp3", 10, rating = 1),
        LibrarySong("/music/b.mp3", 10, rating = 5),
        LibrarySong("/music/c.mp3", 10, rating = 0),
        LibrarySong("/music/skip/d.mp3", 10, rating = 5),
        LibrarySong("/music/e.mp3", 10, rating = 4, playCount = 3),
    )

    private fun store(budget: Long) = FakeStore(
        budget,
        library.associate { it.id to it.sizeBytes },
        library.associate { it.id to it.rating },
    )

    @Test
    fun `fills best rated first, skipping excluded folders`() = runTest {
        val store = store(budget = 30)
        val filler = CacheFiller(store)
        val candidates = filler.candidates(library, FolderRules(listOf("/music/skip")))
        assertEquals(
            listOf("/music/b.mp3", "/music/e.mp3", "/music/c.mp3", "/music/a.mp3"),
            candidates.map { it.id },
        )
        val result = filler.fill(candidates)
        assertEquals(3, result.downloaded)
        assertEquals(setOf("/music/b.mp3", "/music/e.mp3", "/music/c.mp3"), store.cache.keys)
    }

    @Test
    fun `better songs replace worse ones when full`() = runTest {
        val store = store(budget = 20)
        store.cache["/music/a.mp3"] = CachedSong("/music/a.mp3", 10, 1)
        store.cache["/music/c.mp3"] = CachedSong("/music/c.mp3", 10, 0)
        val filler = CacheFiller(store)
        filler.fill(filler.candidates(library, FolderRules.NONE))
        assertEquals(setOf("/music/b.mp3", "/music/skip/d.mp3"), store.cache.keys)
        assertEquals(listOf("/music/a.mp3", "/music/c.mp3"), store.deleted)
    }

    @Test
    fun `already cached songs are not downloaded again`() = runTest {
        val store = store(budget = 100)
        store.cache["/music/b.mp3"] = CachedSong("/music/b.mp3", 10, 5)
        val candidates = CacheFiller(store).candidates(library, FolderRules.NONE)
        assertEquals(false, candidates.any { it.id == "/music/b.mp3" })
    }

    @Test
    fun `stops after too many failures`() = runTest {
        val store = store(budget = 1000)
        store.failing = library.map { it.id }.toSet()
        val filler = CacheFiller(store)
        val result = filler.fill(filler.candidates(library, FolderRules.NONE))
        assertEquals(0, result.downloaded)
        assertEquals(CacheFiller.MAX_FAILURES, result.failed)
    }
}
