package org.bataydar.evidence.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.bataydar.evidence.data.local.AppDatabase
import org.bataydar.evidence.data.model.SyncStatus
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = AppDatabase.getDatabase(appContext)
    private val cropRecordDao = db.cropRecordDao()
    private val evidenceDao = db.geotaggedEvidenceDao()
    private val tokenManager = TokenManager.getInstance(appContext)
    private val apiClient = ApiClient.getInstance(appContext)

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun doWork(): Result {
        // If farmer has not logged in to cloud backend, local-only mode remains active
        if (!tokenManager.isLoggedIn()) {
            return Result.success()
        }

        return try {
            syncPendingCropRecords()
            syncPendingEvidencePhotos()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun syncPendingCropRecords() {
        val pendingRecords = cropRecordDao.getPendingRecords()
        if (pendingRecords.isEmpty()) return

        val syncItems = pendingRecords.map { record ->
            SyncCropRecordItem(
                recordId = record.recordId,
                fieldId = record.recordId,
                cropType = record.cropType,
                cultivationStartDate = record.sowingDate,
                notes = record.fieldDescription ?: "",
                landlordName = record.landlordName ?: "",
                landlordPhone = record.landlordPhone ?: ""
            )
        }

        val pushRequest = SyncPushRequest(
            idempotencyKey = "sync-batch-${UUID.randomUUID()}",
            cropRecords = syncItems
        )

        val response = apiClient.syncApi.pushSync(pushRequest)
        if (response.isSuccessful && response.body()?.success == true) {
            val syncedIds = response.body()?.data?.syncedRecordIds ?: emptyList()
            for (id in syncedIds) {
                cropRecordDao.updateSyncStatus(
                    recordId = id,
                    syncStatus = SyncStatus.SYNCED,
                    timestamp = System.currentTimeMillis()
                )
            }
        } else {
            for (record in pendingRecords) {
                cropRecordDao.updateSyncStatus(
                    recordId = record.recordId,
                    syncStatus = SyncStatus.SYNC_FAILED,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun syncPendingEvidencePhotos() {
        val pendingEvidenceList = evidenceDao.getPendingEvidence()
        for (evidence in pendingEvidenceList) {
            val photoFile = File(evidence.originalPhotoPath)
            if (!photoFile.exists() || photoFile.length() == 0L) {
                evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
                continue
            }

            try {
                // Step 1: Request upload URL with GPS telemetry
                val urlRequest = RequestUploadUrlRequest(
                    recordId = evidence.recordId,
                    evidenceId = evidence.evidenceId,
                    latitude = evidence.latitude,
                    longitude = evidence.longitude,
                    altitude = evidence.altitude ?: 0.0,
                    reportedGpsAccuracyMeters = evidence.accuracyMeters,
                    clientFixAgeSeconds = evidence.gpsFixAgeMs / 1000L,
                    clientCapturedAtUtc = isoFormat.format(Date(evidence.capturedAtUtc)),
                    deviceModel = evidence.deviceModel,
                    notes = evidence.notes ?: ""
                )

                val urlResponse = apiClient.evidenceApi.requestUploadUrl(urlRequest)
                if (!urlResponse.isSuccessful || urlResponse.body()?.success != true) {
                    evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
                    continue
                }

                val uploadData = urlResponse.body()?.data ?: continue
                val requestBody = photoFile.asRequestBody("image/jpeg".toMediaType())

                // Step 2: Upload photograph binary
                val uploadResponse = if (uploadData.storageProvider == "s3") {
                    apiClient.evidenceApi.uploadBinaryDirect(uploadData.uploadUrl, "image/jpeg", requestBody)
                } else {
                    apiClient.evidenceApi.uploadBinaryServerFallback(uploadData.uploadUrl, "image/jpeg", requestBody)
                }

                if (!uploadResponse.isSuccessful) {
                    evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
                    continue
                }

                // Step 3: Complete upload and trigger server-side verification
                val completeResponse = apiClient.evidenceApi.completeUpload(
                    evidenceId = evidence.evidenceId,
                    request = CompleteUploadRequest(
                        clientReportedChecksum = evidence.photoSha256
                    )
                )

                if (completeResponse.isSuccessful && completeResponse.body()?.success == true) {
                    evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNCED)
                } else {
                    evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
                }
            } catch (e: Exception) {
                evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
            }
        }
    }
}
