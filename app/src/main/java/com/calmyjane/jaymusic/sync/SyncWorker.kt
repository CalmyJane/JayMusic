package com.calmyjane.jaymusic.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.calmyjane.jaymusic.appContainer
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        // WorkManager stops workers after 10 minutes; leave some margin and continue in a new one.
        val deadline = System.currentTimeMillis() + 8 * 60 * 1000L
        val wifiOnly = container.settings.state.value.syncOnWifiOnly && !inputData.getBoolean(KEY_FORCE_SCAN, false)
        return try {
            val more = container.sync.run(
                forceScan = inputData.getBoolean(KEY_FORCE_SCAN, false),
                deadline = deadline,
                isWifiAllowed = { !wifiOnly || container.connection.isUnmetered() },
            )
            if (more) container.sync.continueLater()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE_SCAN = "force_scan"
    }
}
