package com.calmyjane.jaymusic.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePolicyTest {

    private fun song(id: String, bytes: Long, rating: Int, lastPlayed: Long = 0, complete: Boolean = true) =
        CachedSong(id, bytes, rating, lastPlayed, complete)

    @Test
    fun `nothing is evicted while there is room`() {
        val plan = StoragePolicy.planEviction(listOf(song("a", 10, 1)), budgetBytes = 100, incomingBytes = 50)
        assertTrue(plan.fits)
        assertTrue(plan.evict.isEmpty())
    }

    @Test
    fun `worst rated songs are evicted first, unrated between 2 and 3 stars`() {
        val cached = listOf(
            song("five", 10, 5),
            song("unrated", 10, 0),
            song("one", 10, 1),
            song("three", 10, 3),
            song("two", 10, 2),
        )
        val order = cached.sortedWith(StoragePolicy.evictionOrder).map { it.id }
        assertEquals(listOf("one", "two", "unrated", "three", "five"), order)

        val plan = StoragePolicy.planEviction(cached, budgetBytes = 50, incomingBytes = 25)
        assertTrue(plan.fits)
        assertEquals(listOf("one", "two", "unrated"), plan.evict)
    }

    @Test
    fun `same rating prefers partial downloads, then least recently played`() {
        val cached = listOf(
            song("recent", 10, 3, lastPlayed = 300),
            song("old", 10, 3, lastPlayed = 100),
            song("partial", 10, 3, lastPlayed = 999, complete = false),
        )
        assertEquals(
            listOf("partial", "old", "recent"),
            cached.sortedWith(StoragePolicy.evictionOrder).map { it.id },
        )
    }

    @Test
    fun `protected songs are never evicted`() {
        val cached = listOf(song("playing", 40, 1), song("good", 40, 5))
        val plan = StoragePolicy.planEviction(cached, 80, 40, protectedIds = setOf("playing"))
        assertEquals(listOf("good"), plan.evict)
    }

    @Test
    fun `background sync only replaces strictly worse songs`() {
        val cached = listOf(song("a", 50, 3), song("b", 50, 2))
        val forThree = StoragePolicy.planEviction(cached, 100, 50, onlyBelowScore = 3.0)
        assertEquals(listOf("b"), forThree.evict)

        val forTwo = StoragePolicy.planEviction(cached, 100, 50, onlyBelowScore = 2.0)
        assertFalse(forTwo.fits)
        assertTrue(forTwo.evict.isEmpty())
    }

    @Test
    fun `file larger than the whole budget never fits`() {
        assertFalse(StoragePolicy.planEviction(emptyList(), 10, 11).fits)
    }

    @Test
    fun `shrink removes worst songs until under budget`() {
        val cached = listOf(song("a", 30, 5), song("b", 30, 1), song("c", 30, 2))
        assertEquals(listOf("b"), StoragePolicy.shrinkTo(cached, 60))
        assertEquals(listOf("b", "c", "a"), StoragePolicy.shrinkTo(cached, 0))
        assertEquals(listOf("c"), StoragePolicy.shrinkTo(cached, 60, protectedIds = setOf("b")))
    }
}
