package com.calmyjane.jaymusic.sync

import android.util.Log
import com.calmyjane.jaymusic.CrashLog
import com.calmyjane.jaymusic.core.ratings.RatingSync
import com.calmyjane.jaymusic.core.synology.SynologyException
import com.calmyjane.jaymusic.data.LibraryRepository
import com.calmyjane.jaymusic.remote.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps ratings in sync with Audio Station, so DS audio and JayMusic show the same stars.
 * The latest change wins (see [RatingSync]). Without Audio Station this does nothing.
 */
class RatingSyncer(
    private val connection: ServerConnection,
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var pushJob: Job? = null
    private var lastFullSync = 0L

    private val _status = MutableStateFlow<String?>(null)

    /** Short description of the last sync, shown in Settings; null if never synced. */
    val status: StateFlow<String?> = _status

    /** Full two-way sync. Returns a short summary, or null if Audio Station is not available. */
    suspend fun syncAll(): String? = mutex.withLock {
        val session = connection.audioStationSession() ?: return@withLock null
        try {
            val server = connection.client().listRatings(session)
            val plan = RatingSync.plan(library.localRatings(), server)
            library.applyRatingPlan(plan)
            var sent = 0
            for (push in plan.push) {
                connection.client().setRating(session, push.songId, push.rating)
                library.markRatingSynced(push.path, push.rating)
                sent++
            }
            lastFullSync = System.currentTimeMillis()
            val summary = "Ratings synced with DS audio: ${plan.pull.size} updated, $sent sent"
            _status.value = summary
            Log.i(TAG, summary)
            summary
        } catch (e: SynologyException) {
            if (e.isSessionError) connection.invalidateAudioStation()
            throw e
        }
    }

    /** Full sync if the last one is older than [maxAgeMs]; errors are logged, not thrown. */
    fun syncIfStale(maxAgeMs: Long = 30 * 60_000L) {
        if (System.currentTimeMillis() - lastFullSync < maxAgeMs) return
        scope.launch {
            runCatching { syncAll() }.onFailure { CrashLog.nonFatal("Rating sync failed", it) }
        }
    }

    /** Sends ratings changed on the phone shortly after the change (batched). */
    fun pushSoon() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(2_000)
            runCatching { pushPending() }.onFailure { CrashLog.nonFatal("Sending ratings failed", it) }
        }
    }

    private suspend fun pushPending() = mutex.withLock {
        val pending = library.pendingRatings()
        if (pending.isEmpty()) return@withLock
        val session = connection.audioStationSession() ?: return@withLock
        // Songs without a known Audio Station id need a full sync first.
        if (pending.any { it.serverSongId == null }) lastFullSync = 0
        for (track in pending) {
            val id = track.serverSongId ?: continue
            try {
                connection.client().setRating(session, id, track.rating)
                library.markRatingSynced(track.path, track.rating)
            } catch (e: SynologyException) {
                if (e.isSessionError) connection.invalidateAudioStation()
                throw e
            }
        }
    }.also { if (lastFullSync == 0L) syncIfStale(0) }

    private companion object {
        const val TAG = "RatingSyncer"
    }
}
