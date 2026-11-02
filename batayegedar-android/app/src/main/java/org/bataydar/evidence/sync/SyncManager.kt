package org.bataydar.evidence.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Enqueue an immediate one-time synchronization task when user taps Sync Now or adds new evidence
     */
    fun enqueueImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_ONE_TIME_SYNC)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Schedule periodic background synchronization (every 6 hours) when connected to unmetered/any network
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    /**
     * Schedule periodic backend keep-alive ping (every 15 minutes) when connected to any network
     * to keep Render backend container awake.
     */
    fun schedulePeriodicKeepAlive() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val keepAliveRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                5,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME_KEEP_ALIVE,
            ExistingPeriodicWorkPolicy.KEEP,
            keepAliveRequest
        )
    }

    companion object {
        const val TAG_ONE_TIME_SYNC = "tag_batayedar_sync"
        private const val UNIQUE_WORK_NAME_IMMEDIATE = "work_batayedar_immediate_sync"
        private const val UNIQUE_WORK_NAME_PERIODIC = "work_batayedar_periodic_sync"
        private const val UNIQUE_WORK_NAME_KEEP_ALIVE = "work_batayedar_keep_alive"

        @Volatile
        private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager =
            instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
    }
}
