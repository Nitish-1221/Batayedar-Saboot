package org.bataydar.evidence.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.bataydar.evidence.BataydarApp

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val cloudSyncEngine = (appContext.applicationContext as BataydarApp).cloudSyncEngine

    override suspend fun doWork(): Result {
        return try {
            val result = cloudSyncEngine.performFullSync()
            when (result) {
                is SyncResult.Success -> Result.success()
                is SyncResult.NetworkUnavailable -> Result.retry()
                is SyncResult.NotConfigured -> Result.success()
                is SyncResult.Failure -> {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
