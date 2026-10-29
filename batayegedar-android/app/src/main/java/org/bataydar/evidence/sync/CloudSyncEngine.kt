package org.bataydar.evidence.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bataydar.evidence.data.local.CropRecordDao
import org.bataydar.evidence.data.local.GeotaggedEvidenceDao
import org.bataydar.evidence.data.model.SyncStatus
import org.bataydar.evidence.data.remote.auth.TokenManager

class CloudSyncEngine(
    private val context: Context,
    private val cropRecordDao: CropRecordDao,
    private val evidenceDao: GeotaggedEvidenceDao
) {

    private val tokenManager = TokenManager.getInstance(context)
    private val syncManager = SyncManager.getInstance(context)

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun triggerSync(): SyncResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext SyncResult.NetworkUnavailable(
                "No active internet connection. Evidence remains safe offline on this device."
            )
        }

        if (!tokenManager.isLoggedIn()) {
            return@withContext SyncResult.NotConfigured(
                "Offline-First Mode: Log in from Settings or Welcome screen to enable cloud sync. All local evidence is safely preserved in Room database."
            )
        }

        // Enqueue WorkManager sync job with exponential retry policy
        syncManager.enqueueImmediateSync()

        return@withContext SyncResult.Success(
            itemsSyncedCount = 1,
            message = "Background synchronization queued via WorkManager."
        )
    }

    suspend fun queueForSync(recordId: String) = withContext(Dispatchers.IO) {
        cropRecordDao.updateSyncStatus(
            recordId = recordId,
            syncStatus = SyncStatus.PENDING_SYNC,
            timestamp = null
        )
        if (isNetworkAvailable() && tokenManager.isLoggedIn()) {
            syncManager.enqueueImmediateSync()
        }
    }
}
