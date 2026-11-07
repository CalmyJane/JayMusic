package com.calmyjane.jaymusic.core.ratings

import com.calmyjane.jaymusic.core.synology.ServerRating

/** A song's rating on the phone. [dirty] = changed on the phone and not yet sent to the NAS. */
data class LocalRating(val path: String, val rating: Int, val dirty: Boolean)

data class RatingPush(val path: String, val songId: String, val rating: Int)

data class RatingSyncPlan(
    /** Ratings to take over from the NAS (path -> rating). */
    val pull: Map<String, Int>,
    /** Ratings changed on the phone to send to the NAS. */
    val push: List<RatingPush>,
    /** Changed on the phone but the NAS already has the same value: just mark as synced. */
    val alreadyInSync: List<LocalRating>,
    /** Audio Station song id per path, to push later changes without listing again. */
    val songIds: Map<String, String>,
)

/**
 * Two-way rating sync with Audio Station (DS audio). The latest change wins: a rating changed on
 * the phone is sent to the NAS; otherwise the NAS's rating (possibly changed in DS audio) is
 * taken over.
 */
object RatingSync {

    private val VOLUME_PREFIX = Regex("^/volume[^/]*")

    /**
     * Audio Station may report paths with the volume (`/volume1/music/a.mp3`) while File Station
     * uses the shared folder (`/music/a.mp3`).
     */
    fun normalize(path: String): String = path.replaceFirst(VOLUME_PREFIX, "")

    fun plan(local: Collection<LocalRating>, server: Collection<ServerRating>): RatingSyncPlan {
        val byPath = local.associateBy { it.path }
        val pull = HashMap<String, Int>()
        val push = ArrayList<RatingPush>()
        val inSync = ArrayList<LocalRating>()
        val ids = HashMap<String, String>()
        for (song in server) {
            val mine = byPath[song.path] ?: byPath[normalize(song.path)] ?: continue
            ids[mine.path] = song.songId
            when {
                mine.dirty && mine.rating != song.rating -> push += RatingPush(mine.path, song.songId, mine.rating)
                mine.dirty -> inSync += mine
                mine.rating != song.rating -> pull[mine.path] = song.rating
            }
        }
        return RatingSyncPlan(pull, push, inSync, ids)
    }
}
