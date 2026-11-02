package org.bataydar.evidence.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.data.remote.ServerConfig

/**
 * Periodically pings the Render backend health endpoint to prevent the free tier container
 * from sleeping after 15 minutes of inactivity.
 */
class KeepAliveWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? BataydarApp
        val client = app?.apiClient?.okHttpClient ?: return@withContext Result.success()

        val baseUrl = ServerConfig.getBaseUrl(applicationContext).removeSuffix("/")
        val healthUrl = "$baseUrl/health"

        try {
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    Log.i(TAG, "Backend keep-alive ping successful: ${it.code}")
                    Result.success()
                } else {
                    Log.w(TAG, "Backend keep-alive returned status ${it.code}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend keep-alive ping error: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "KeepAliveWorker"
    }
}

