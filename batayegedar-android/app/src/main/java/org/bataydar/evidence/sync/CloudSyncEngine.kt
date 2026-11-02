package org.bataydar.evidence.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.bataydar.evidence.data.local.CropRecordDao
import org.bataydar.evidence.data.local.DeletionTombstoneDao
import org.bataydar.evidence.data.local.GeotaggedEvidenceDao
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.DeletionTombstoneEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.SyncStatus
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.ServerConfig
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.*
import org.bataydar.evidence.export.ChecksumUtils
import org.bataydar.evidence.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class CloudSyncState {
    OFFLINE,
    ONLINE_NOT_SYNCED,
    SYNCING,
    SYNCED,
    SYNC_FAILED
}

data class SyncStatusInfo(
    val state: CloudSyncState,
    val pendingUploads: Int = 0,
    val pendingDownloads: Int = 0,
    val lastSyncTimestamp: Long = 0L,
    val message: String? = null
)

data class UploadProgressInfo(
    val isUploading: Boolean = false,
    val totalRecords: Int = 0,
    val uploadedRecords: Int = 0,
    val totalPhotos: Int = 0,
    val uploadedPhotos: Int = 0,
    val failedPhotos: Int = 0,
    val currentStepMessage: String? = null,
    val lastUploadTimestamp: Long = 0L,
    val lastSummary: String? = null,
    val lastError: String? = null
)

data class UploadResult(
    val success: Boolean,
    val recordsUploaded: Int,
    val photosUploaded: Int,
    val photosFailed: Int,
    val message: String
)

class CloudSyncEngine(
    private val context: Context,
    private val cropRecordDao: CropRecordDao,
    private val evidenceDao: GeotaggedEvidenceDao,
    private val deletionTombstoneDao: DeletionTombstoneDao
) {
    private val TAG = "CloudSyncEngine"

    private val tokenManager = TokenManager.getInstance(context)
    private val apiClient by lazy { ApiClient.getInstance(context) }
    private val syncManager = SyncManager.getInstance(context)

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val localDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val _syncStatusFlow = MutableStateFlow(
        SyncStatusInfo(
            state = CloudSyncState.OFFLINE,
            pendingUploads = 0,
            pendingDownloads = 0,
            lastSyncTimestamp = tokenManager.getLastSyncTimestamp(),
            message = null
        )
    )
    val syncStatusFlow: StateFlow<SyncStatusInfo> = _syncStatusFlow.asStateFlow()

    private val _uploadProgressFlow = MutableStateFlow(
        UploadProgressInfo(
            isUploading = false,
            lastUploadTimestamp = tokenManager.getLastUploadTimestamp(),
            lastSummary = tokenManager.getLastUploadSummary()
        )
    )
    val uploadProgressFlow: StateFlow<UploadProgressInfo> = _uploadProgressFlow.asStateFlow()

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun refreshSyncState() = withContext(Dispatchers.IO) {
        val isOnline = isNetworkAvailable()
        val isLoggedIn = tokenManager.isLoggedIn()
        val lastTimestamp = tokenManager.getLastSyncTimestamp()
        val lastStatus = tokenManager.getLastSyncStatus()
        val lastMsg = tokenManager.getLastSyncMessage()

        val currentUid = tokenManager.getActiveUid()
        val pendingRecords = cropRecordDao.getPendingSyncCountDirect(currentUid)
        val pendingEvidence = evidenceDao.getUnsyncedEvidenceCountDirect(currentUid)
        val pendingDeletions = deletionTombstoneDao.getPendingDeletionsCount(currentUid)
        val totalPending = pendingRecords + pendingEvidence + pendingDeletions

        val state = when {
            !isOnline -> CloudSyncState.OFFLINE
            !isLoggedIn -> CloudSyncState.OFFLINE
            _syncStatusFlow.value.state == CloudSyncState.SYNCING -> CloudSyncState.SYNCING
            totalPending > 0 -> CloudSyncState.ONLINE_NOT_SYNCED
            lastStatus == "SYNC_FAILED" -> CloudSyncState.SYNC_FAILED
            lastStatus == "SYNCED" -> CloudSyncState.SYNCED
            else -> CloudSyncState.ONLINE_NOT_SYNCED
        }

        _syncStatusFlow.value = SyncStatusInfo(
            state = state,
            pendingUploads = totalPending,
            pendingDownloads = 0,
            lastSyncTimestamp = lastTimestamp,
            message = lastMsg
        )
    }

    private fun resolvePhotoFile(evidence: GeotaggedEvidenceEntity): File? {
        if (!evidence.originalPhotoPath.isNullOrBlank()) {
            val f = File(evidence.originalPhotoPath)
            if (f.exists() && f.length() > 0L) return f
        }
        if (!evidence.watermarkedPhotoPath.isNullOrBlank()) {
            val f = File(evidence.watermarkedPhotoPath)
            if (f.exists() && f.length() > 0L) return f
        }
        val evidenceDir = FileUtils.getEvidencePhotosDirectory(context)
        val candidate = File(evidenceDir, "ORIG_${evidence.recordId}_${evidence.evidenceId}.jpg")
        if (candidate.exists() && candidate.length() > 0L) return candidate

        val candidateAlt = File(evidenceDir, "${evidence.evidenceId}.jpg")
        if (candidateAlt.exists() && candidateAlt.length() > 0L) return candidateAlt

        val matched = evidenceDir.listFiles { file ->
            file.isFile && file.length() > 0L && (file.name.contains(evidence.evidenceId) || file.name.contains(evidence.recordId))
        }
        if (!matched.isNullOrEmpty()) {
            return matched[0]
        }
        return null
    }

    private fun createPartFromString(value: String): RequestBody {
        return value.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    /**
     * Upload single evidence photograph using direct multipart upload or legacy 3-step fallback.
     */
    private suspend fun uploadPhotoBinaryWithFallback(evidence: GeotaggedEvidenceEntity, photoFile: File): Boolean {
        // Compute cryptographic SHA-256 hash if empty
        val sha256 = if (evidence.photoSha256.isNotBlank()) {
            evidence.photoSha256
        } else {
            ChecksumUtils.calculateSha256(photoFile)
        }

        // Method 1: Modern direct multipart upload (POST /sync/upload)
        try {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            val fileBody = photoFile.asRequestBody(mediaType)
            val photoPart = MultipartBody.Part.createFormData("photo", photoFile.name, fileBody)

            val parts = mutableMapOf<String, RequestBody>()
            parts["recordId"] = createPartFromString(evidence.recordId)
            parts["evidenceId"] = createPartFromString(evidence.evidenceId)
            parts["latitude"] = createPartFromString(evidence.latitude.toString())
            parts["longitude"] = createPartFromString(evidence.longitude.toString())
            parts["reportedGpsAccuracyMeters"] = createPartFromString(evidence.accuracyMeters.toString())
            parts["clientFixAgeSeconds"] = createPartFromString((evidence.gpsFixAgeMs / 1000L).toString())
            parts["clientCapturedAtUtc"] = createPartFromString(isoFormat.format(Date(evidence.capturedAtUtc)))
            parts["clientReportedChecksum"] = createPartFromString(sha256)
            parts["deviceModel"] = createPartFromString(evidence.deviceModel)
            parts["deviceManufacturer"] = createPartFromString(Build.MANUFACTURER)
            parts["notes"] = createPartFromString(evidence.notes ?: "")

            val directResponse = apiClient.syncApi.uploadEvidenceDirect(photoPart, parts)
            if (directResponse.isSuccessful && directResponse.body()?.success == true) {
                Log.d(TAG, "Evidence ${evidence.evidenceId} uploaded via POST /sync/upload successfully.")
                return true
            }

            if (directResponse.code() == 410) {
                Log.w(TAG, "Evidence ${evidence.evidenceId} was deleted on the server (410 GONE). Deleting locally.")
                evidenceDao.deleteEvidenceById(evidence.evidenceId)
                try { photoFile.delete() } catch (_: Exception) {}
                deletionTombstoneDao.insertTombstone(
                    DeletionTombstoneEntity(
                        recordUuid = evidence.evidenceId,
                        recordType = "EVIDENCE",
                        recordId = evidence.recordId,
                        firebaseUid = tokenManager.getUserId().orEmpty(),
                        syncStatus = SyncStatus.DELETED
                    )
                )
                return false
            }

            if (directResponse.code() != 404) {
                Log.w(TAG, "Direct upload returned code ${directResponse.code()}, falling back to 3-step upload.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct upload exception for ${evidence.evidenceId}: ${e.message}. Trying 3-step fallback.")
        }

        // Method 2: Legacy 3-step signed upload fallback
        return performLegacy3StepUpload(evidence, photoFile, sha256)
    }

    private suspend fun performLegacy3StepUpload(
        evidence: GeotaggedEvidenceEntity,
        photoFile: File,
        sha256: String
    ): Boolean {
        try {
            // Check if evidenceId conforms to standard UUID format expected by backend validator
            val isUuid = try {
                UUID.fromString(evidence.evidenceId)
                true
            } catch (e: Exception) {
                false
            }
            val serverEvidenceId = if (isUuid) evidence.evidenceId else null

            // Step A: Request upload URL (omit evidenceId if not a standard UUID, let server assign UUID)
            val urlRequest = RequestUploadUrlRequest(
                recordId = evidence.recordId,
                evidenceId = serverEvidenceId,
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
                Log.e(TAG, "Request upload URL failed: ${urlResponse.code()} ${urlResponse.message()}")
                return false
            }

            val uploadData = urlResponse.body()?.data ?: return false
            val assignedEvidenceId = uploadData.evidenceId ?: evidence.evidenceId

            // Step B: Upload photograph binary with resolved base URL (avoiding double /api/v1 prefix)
            val requestBody = photoFile.asRequestBody("image/jpeg".toMediaType())
            val resolvedUrl = if (uploadData.uploadUrl.startsWith("http://") || uploadData.uploadUrl.startsWith("https://")) {
                uploadData.uploadUrl
            } else {
                val fullBase = ServerConfig.getBaseUrl(context)
                val urlObj = java.net.URL(fullBase)
                val hostOrigin = "${urlObj.protocol}://${urlObj.host}${if (urlObj.port != -1 && urlObj.port != 80 && urlObj.port != 443) ":${urlObj.port}" else ""}"
                if (uploadData.uploadUrl.startsWith("/")) {
                    hostOrigin + uploadData.uploadUrl
                } else {
                    fullBase.removeSuffix("/") + "/" + uploadData.uploadUrl
                }
            }

            val uploadResponse = if (uploadData.storageProvider == "s3") {
                apiClient.evidenceApi.uploadBinaryDirect(resolvedUrl, "image/jpeg", requestBody)
            } else {
                apiClient.evidenceApi.uploadBinaryServerFallback(resolvedUrl, "image/jpeg", requestBody)
            }

            if (!uploadResponse.isSuccessful) {
                Log.e(TAG, "Binary upload step failed with code ${uploadResponse.code()}")
                return false
            }

            // Step C: Complete upload & verify SHA-256 integrity on server
            val completeResponse = apiClient.evidenceApi.completeUpload(
                evidenceId = assignedEvidenceId,
                request = CompleteUploadRequest(clientReportedChecksum = sha256)
            )

            val completeSuccess = completeResponse.isSuccessful && completeResponse.body()?.success == true
            if (completeSuccess && assignedEvidenceId != evidence.evidenceId) {
                // If server assigned a UUID, update local Room entity to keep IDs consistent
                evidenceDao.deleteEvidenceById(evidence.evidenceId)
                evidenceDao.insertEvidence(
                    evidence.copy(
                        evidenceId = assignedEvidenceId,
                        syncStatus = SyncStatus.SYNCED
                    )
                )
            }
            return completeSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Legacy 3-step upload exception for ${evidence.evidenceId}", e)
            return false
        }
    }

    /**
     * Upload Data action:
     * - First syncs all pending deletions with the cloud and Cloudinary
     * - Reads locally pending records and fields from Room (filtering out tombstones)
     * - Uploads records and fields to user account
     * - Uploads all pending original photos with byte-level verification
     * - Updates local sync status only when cloud persistence is confirmed
     * - A crop record is NOT marked synced if its evidence photos are pending
     */
    suspend fun performUpload(): UploadResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            val msg = "No active internet connection. Evidence remains safe locally on this device."
            _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                isUploading = false,
                lastError = msg
            )
            refreshSyncState()
            return@withContext UploadResult(false, 0, 0, 0, msg)
        }

        if (!tokenManager.isLoggedIn()) {
            val msg = "Login required to upload records to your account."
            _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                isUploading = false,
                lastError = msg
            )
            refreshSyncState()
            return@withContext UploadResult(false, 0, 0, 0, msg)
        }

        val currentUid = tokenManager.getActiveUid()
        val pendingDeletions = deletionTombstoneDao.getPendingDeletions(currentUid)
        val pendingRecords = cropRecordDao.getPendingRecords(currentUid)
        val pendingEvidenceList = evidenceDao.getPendingEvidence(currentUid)

        val totalRecords = pendingRecords.size
        val totalPhotos = pendingEvidenceList.size
        val totalDeletions = pendingDeletions.size

        if (totalRecords == 0 && totalPhotos == 0 && totalDeletions == 0) {
            val msg = "All records and photos are already up to date in the cloud."
            _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                isUploading = false,
                currentStepMessage = msg,
                lastUploadTimestamp = tokenManager.getLastUploadTimestamp(),
                lastSummary = tokenManager.getLastUploadSummary()
            )
            refreshSyncState()
            return@withContext UploadResult(true, 0, 0, 0, msg)
        }

        _uploadProgressFlow.value = UploadProgressInfo(
            isUploading = true,
            totalRecords = totalRecords,
            uploadedRecords = 0,
            totalPhotos = totalPhotos,
            uploadedPhotos = 0,
            failedPhotos = 0,
            currentStepMessage = if (totalDeletions > 0) "Synchronizing deletions with cloud..." else "Uploading crop records to cloud...",
            lastUploadTimestamp = tokenManager.getLastUploadTimestamp()
        )

        var recordsUploaded = 0
        var photosUploaded = 0
        var photosFailed = 0

        try {
            // 0. Synchronize Pending Deletions FIRST
            if (pendingDeletions.isNotEmpty()) {
                val delEvidenceIds = pendingDeletions.filter { it.recordType == "EVIDENCE" }.map { it.recordUuid }
                val delRecordIds = pendingDeletions.filter { it.recordType == "CROP_RECORD" }.map { it.recordUuid }

                var deletionsSynced = false
                try {
                    val delRequest = SyncDeletionsRequest(
                        deletedEvidenceIds = delEvidenceIds,
                        deletedRecordIds = delRecordIds
                    )

                    val delResponse = apiClient.syncApi.syncDeletions(delRequest)
                    if (delResponse.isSuccessful && delResponse.body()?.success == true) {
                        for (tombstone in pendingDeletions) {
                            deletionTombstoneDao.updateSyncStatus(tombstone.recordUuid, SyncStatus.DELETED)
                        }
                        deletionsSynced = true
                    } else {
                        Log.w(TAG, "Batch deletions returned code ${delResponse.code()}, attempting fallback.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Batch deletions failed: ${e.message}, attempting fallback.")
                }

                if (!deletionsSynced) {
                    // Fallback to individual deletion endpoints
                    var anySuccess = false
                    for (evId in delEvidenceIds) {
                        try {
                            val res = apiClient.evidenceApi.deleteEvidence(evId)
                            if (res.isSuccessful || res.code() == 404) {
                                deletionTombstoneDao.updateSyncStatus(evId, SyncStatus.DELETED)
                                anySuccess = true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Fallback deletion for evidence $evId: ${e.message}")
                        }
                    }
                    for (recId in delRecordIds) {
                        try {
                            val res = apiClient.cropRecordApi.deleteCropRecord(recId)
                            if (res.isSuccessful || res.code() == 404) {
                                deletionTombstoneDao.updateSyncStatus(recId, SyncStatus.DELETED)
                                anySuccess = true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Fallback deletion for record $recId: ${e.message}")
                        }
                    }
                    if (anySuccess || (delEvidenceIds.isEmpty() && delRecordIds.isEmpty())) {
                        deletionsSynced = true
                    }
                }

                if (!deletionsSynced) {
                    val errMsg = "Failed syncing deletions with cloud."
                    Log.e(TAG, errMsg)
                    _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                        isUploading = false,
                        lastError = errMsg
                    )
                    refreshSyncState()
                    return@withContext UploadResult(false, 0, 0, 0, errMsg)
                }

                Log.d(TAG, "Synchronized ${pendingDeletions.size} deletion tombstones with cloud.")
            }

            // Exclude any tombstoned entities from upload to prevent resurrecting deleted records
            val tombstoneUuids = deletionTombstoneDao.getAllTombstoneUuids(currentUid).toSet()
            val activePendingRecords = pendingRecords.filter {
                (it.ownerFirebaseUid.isBlank() || it.ownerFirebaseUid == currentUid) && it.recordId !in tombstoneUuids
            }
            val activePendingEvidenceList = pendingEvidenceList.filter {
                (it.ownerFirebaseUid.isBlank() || it.ownerFirebaseUid == currentUid) &&
                it.evidenceId !in tombstoneUuids &&
                it.recordId !in tombstoneUuids
            }

            // 1. Push Crop Records and Field Metadata
            if (activePendingRecords.isNotEmpty()) {
                val syncItems = activePendingRecords.map { record ->
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

                val syncFields = activePendingRecords.map { record ->
                    SyncFieldItem(
                        fieldId = record.recordId,
                        fieldName = record.fieldName,
                        description = record.fieldDescription ?: "",
                        village = record.village,
                        district = record.district,
                        state = record.state,
                        areaAcres = 1.0
                    )
                }.distinctBy { it.fieldId }

                val pushRequest = SyncPushRequest(
                    idempotencyKey = "sync-batch-${UUID.randomUUID()}",
                    fields = syncFields,
                    cropRecords = syncItems
                )

                val response = apiClient.syncApi.pushSync(pushRequest)
                if (!response.isSuccessful || response.body()?.success != true) {
                    val errMsg = "Failed pushing crop records: (code ${response.code()})"
                    Log.e(TAG, errMsg)
                    _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                        isUploading = false,
                        lastError = errMsg
                    )
                    refreshSyncState()
                    return@withContext UploadResult(false, 0, 0, 0, errMsg)
                }
            }

            // 2. Upload Pending Evidence Photographs (actual binary bytes)
            val totalActivePhotos = activePendingEvidenceList.size
            for ((index, evidence) in activePendingEvidenceList.withIndex()) {
                val stepMsg = "Uploading photo ${index + 1} of $totalActivePhotos..."
                _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                    currentStepMessage = stepMsg,
                    uploadedPhotos = photosUploaded,
                    failedPhotos = photosFailed
                )

                val photoFile = resolvePhotoFile(evidence)
                if (photoFile == null || !photoFile.exists() || photoFile.length() == 0L) {
                    Log.e(TAG, "Photo file missing for evidence ${evidence.evidenceId}")
                    evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
                    photosFailed++
                    continue
                }

                val uploadOk = uploadPhotoBinaryWithFallback(evidence, photoFile)
                if (uploadOk) {
                    evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNCED)
                    photosUploaded++
                } else {
                    if (deletionTombstoneDao.getTombstone(evidence.evidenceId) != null) {
                        // Was tombstoned/deleted
                    } else {
                        evidenceDao.updateSyncStatus(evidence.evidenceId, SyncStatus.SYNC_FAILED)
                        photosFailed++
                    }
                }
            }

            // 3. Mark Crop Records as fully SYNCED only when all required evidence photos are synced!
            val now = System.currentTimeMillis()
            for (record in activePendingRecords) {
                val unsyncedPhotos = evidenceDao.getUnsyncedEvidenceCountForRecord(record.recordId, currentUid)
                if (unsyncedPhotos == 0) {
                    cropRecordDao.updateSyncStatus(record.recordId, SyncStatus.SYNCED, now)
                    recordsUploaded++
                } else {
                    // Crop record must NOT be marked fully synced if its required evidence photos are pending!
                    cropRecordDao.updateSyncStatus(record.recordId, SyncStatus.LOCAL_ONLY, now)
                }
            }

            // 4. Save and report results
            tokenManager.saveLastUploadTimestamp(now)
            val summaryStr = "Records uploaded: $recordsUploaded/$totalRecords  •  Photos uploaded: $photosUploaded/$totalPhotos${if (photosFailed > 0) "  •  Failed: $photosFailed" else ""}${if (totalDeletions > 0) "  •  Deletions: $totalDeletions" else ""}"
            tokenManager.saveLastUploadSummary(summaryStr)
            tokenManager.saveLastSyncTimestamp(now)
            tokenManager.saveLastSyncStatus(if (photosFailed > 0) "PARTIAL" else "SYNCED")

            _uploadProgressFlow.value = UploadProgressInfo(
                isUploading = false,
                totalRecords = totalRecords,
                uploadedRecords = recordsUploaded,
                totalPhotos = totalPhotos,
                uploadedPhotos = photosUploaded,
                failedPhotos = photosFailed,
                currentStepMessage = "Upload completed.",
                lastUploadTimestamp = now,
                lastSummary = summaryStr
            )

            refreshSyncState()

            val success = photosFailed == 0
            val resultMessage = if (success) {
                if (recordsUploaded == 0 && photosUploaded == 0 && totalDeletions > 0) {
                    "Cloud deletion completed: $totalDeletions records/photos removed permanently."
                } else {
                    "Upload completed successfully: $recordsUploaded records, $photosUploaded photos${if (totalDeletions > 0) ", $totalDeletions deletions synchronized" else ""}."
                }
            } else {
                "Upload finished with $photosFailed photo failure(s). Records: $recordsUploaded/$totalRecords, Photos: $photosUploaded/$totalPhotos."
            }

            return@withContext UploadResult(success, recordsUploaded, photosUploaded, photosFailed, resultMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during upload", e)
            val errMsg = e.localizedMessage ?: "Upload error occurred"
            _uploadProgressFlow.value = _uploadProgressFlow.value.copy(
                isUploading = false,
                lastError = errMsg
            )
            refreshSyncState()
            return@withContext UploadResult(false, recordsUploaded, photosUploaded, photosFailed, errMsg)
        }
    }

    /**
     * Background sync worker execution (WorkManager compatible)
     */
    suspend fun performFullSync(): SyncResult = withContext(Dispatchers.IO) {
        val uploadRes = performUpload()
        if (uploadRes.success) {
            SyncResult.Success(uploadRes.recordsUploaded + uploadRes.photosUploaded, uploadRes.message)
        } else if (!isNetworkAvailable()) {
            SyncResult.NetworkUnavailable(uploadRes.message)
        } else {
            SyncResult.Failure(Exception(uploadRes.message), uploadRes.message)
        }
    }

    suspend fun triggerSync(): SyncResult = withContext(Dispatchers.IO) {
        performFullSync()
    }

    suspend fun queueForSync(recordId: String) = withContext(Dispatchers.IO) {
        cropRecordDao.updateSyncStatus(
            recordId = recordId,
            syncStatus = SyncStatus.PENDING_SYNC,
            timestamp = null
        )
        refreshSyncState()
        if (isNetworkAvailable() && tokenManager.isLoggedIn()) {
            syncManager.enqueueImmediateSync()
        }
    }
}
