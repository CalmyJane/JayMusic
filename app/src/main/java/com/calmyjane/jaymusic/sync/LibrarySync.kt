package com.calmyjane.jaymusic.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.calmyjane.jaymusic.AppContainer
import com.calmyjane.jaymusic.core.cache.CacheFiller
import com.calmyjane.jaymusic.core.library.LibraryScanner
import com.calmyjane.jaymusic.core.synology.SynologyException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** What the sync is doing right now, shown in Settings. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Scanning(val folders: Int, val tracks: Int) : SyncStatus
    data class Downloading(val done: Int) : SyncStatus
    data class Finished(val message: String) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/**
 * Library sync: scans the music folders on the NAS and fills the phone with the best rated songs
 * that fit into the storage limit. Runs in the background via WorkManager.
 */
class LibrarySync(private val context: Context, private val container: AppContainer) {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val workManager get() = WorkManager.getInstance(context)

    val isRunning: Flow<Boolean>
        get() = workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    fun schedulePeriodic(wifiOnly: Boolean = container.settings.state.value.syncOnWifiOnly) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints(wifiOnly))
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Starts a sync now. A user-started sync also runs on mobile data. */
    fun syncNow(userInitiated: Boolean = true) {
        val wifiOnly = !userInitiated && container.settings.state.value.syncOnWifiOnly
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(wifiOnly))
            .setInputData(workDataOf(SyncWorker.KEY_FORCE_SCAN to userInitiated))
            .build()
        workManager.enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Continues filling the cache in a new work request (a single one may run for 10 minutes). */
    fun continueLater() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints(container.settings.state.value.syncOnWifiOnly))
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(CONTINUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun constraints(wifiOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresStorageNotLow(true)
        .build()

    /**
     * Runs one sync pass. Returns true if there is more to download.
     *
     * @param forceScan rescan even if the last scan is recent.
     * @param deadline stop downloading at this time (epoch millis).
     */
    suspend fun run(forceScan: Boolean, deadline: Long, isWifiAllowed: () -> Boolean): Boolean {
        val connection = container.connection
        val session = connection.awaitSession(30_000)
        if (session == null) {
            _status.value = SyncStatus.Failed("Server not reachable")
            return false
        }
        val settings = container.settings.flow.first()
        try {
            val scanDue = System.currentTimeMillis() - settings.lastScanAt > SCAN_INTERVAL_MS
            if (forceScan || scanDue || container.library.allTracks.value.isEmpty()) {
                _status.value = SyncStatus.Scanning(0, 0)
                val scanner = LibraryScanner({ folder -> connection.client().listFolder(session, folder) })
                val result = scanner.scan(settings.musicFolders) { folders, tracks ->
                    _status.value = SyncStatus.Scanning(folders, tracks)
                }
                val changes = container.library.applyScan(result.tracks)
                container.mediaCache.removePaths(changes.removed + changes.changed)
                container.settings.setLastScanAt(System.currentTimeMillis())
                Log.i(TAG, "Scan: ${result.tracks.size} songs, +${changes.added} -${changes.removed.size}")
                // Wait for the new library to reach the in-memory index used by the filler.
                withTimeoutOrNull(10_000) { container.library.allTracks.first { it.size == result.tracks.size } }
            }

            val cache = container.mediaCache
            val filler = CacheFiller(cache)
            val candidates = filler.candidates(container.library.librarySongs(), container.library.folderRules.value)
            _status.value = SyncStatus.Downloading(0)
            val result = filler.fill(
                candidates,
                shouldContinue = { System.currentTimeMillis() < deadline && connection.isOnline && isWifiAllowed() },
                onDownloaded = { count -> _status.value = SyncStatus.Downloading(count) },
            )
            _status.value = SyncStatus.Finished(
                "Downloaded ${result.downloaded} songs" + if (result.evicted > 0) ", replaced ${result.evicted}" else "",
            )
            return result.stoppedEarly && result.failed < CacheFiller.MAX_FAILURES
        } catch (e: SynologyException) {
            if (e.isSessionError) connection.invalidate(session)
            _status.value = SyncStatus.Failed(e.message ?: "Server error")
            throw e
        } catch (e: java.io.IOException) {
            _status.value = SyncStatus.Failed(e.message ?: "Network error")
            throw e
        }
    }

    companion object {
        private const val TAG = "LibrarySync"
        private const val PERIODIC_WORK = "sync-periodic"
        private const val ONE_TIME_WORK = "sync-now"
        private const val CONTINUE_WORK = "sync-continue"
        private const val SCAN_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
