package com.calmyjane.jaymusic.core.ratings

import com.calmyjane.jaymusic.core.synology.ServerRating
import org.junit.Assert.assertEquals
import org.junit.Test

class RatingSyncTest {

    @Test
    fun `takes over ratings from the NAS when unchanged on the phone`() {
        val plan = RatingSync.plan(
            local = listOf(LocalRating("/music/a.mp3", 0, dirty = false), LocalRating("/music/b.mp3", 3, dirty = false)),
            server = listOf(ServerRating("music_1", "/music/a.mp3", 5), ServerRating("music_2", "/music/b.mp3", 3)),
        )
        assertEquals(mapOf("/music/a.mp3" to 5), plan.pull)
        assertEquals(emptyList<RatingPush>(), plan.push)
        assertEquals(mapOf("/music/a.mp3" to "music_1", "/music/b.mp3" to "music_2"), plan.songIds)
    }

    @Test
    fun `phone changes win and are pushed`() {
        val plan = RatingSync.plan(
            local = listOf(LocalRating("/music/a.mp3", 2, dirty = true), LocalRating("/music/b.mp3", 4, dirty = true)),
            server = listOf(ServerRating("music_1", "/music/a.mp3", 5), ServerRating("music_2", "/music/b.mp3", 4)),
        )
        assertEquals(emptyMap<String, Int>(), plan.pull)
        assertEquals(listOf(RatingPush("/music/a.mp3", "music_1", 2)), plan.push)
        assertEquals(listOf("/music/b.mp3"), plan.alreadyInSync.map { it.path })
    }

    @Test
    fun `matches volume paths and ignores unknown songs`() {
        val plan = RatingSync.plan(
            local = listOf(LocalRating("/music/Rock/a.flac", 0, dirty = false)),
            server = listOf(ServerRating("music_9", "/volume1/music/Rock/a.flac", 4), ServerRating("music_7", "/music/other.mp3", 1)),
        )
        assertEquals(mapOf("/music/Rock/a.flac" to 4), plan.pull)
        assertEquals(1, plan.songIds.size)
        assertEquals("/music/x", RatingSync.normalize("/volumeUSB1/music/x"))
    }
}
