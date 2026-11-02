package org.bataydar.evidence.sync

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.bataydar.evidence.data.local.AppDatabase
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.DeletionTombstoneEntity
import org.bataydar.evidence.data.model.FarmerProfileEntity
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class RestoreState {
    object Idle : RestoreState()
    data class Restoring(val current: Int, val total: Int, val message: String) : RestoreState()
    data class Success(val recordsCount: Int, val photosCount: Int) : RestoreState()
    data class Error(val message: String) : RestoreState()
}

data class RestoreProgressInfo(
    val isRestoring: Boolean = false,
    val totalRecords: Int = 0,
    val restoredRecords: Int = 0,
    val totalPhotos: Int = 0,
    val downloadedPhotos: Int = 0,
    val failedPhotos: Int = 0,
    val currentStepMessage: String? = null,
    val lastRestoreTimestamp: Long = 0L,
    val lastSummary: String? = null,
    val lastError: String? = null
)

class CloudRestoreManager private constructor(private val appContext: Context) {

    private val TAG = "CloudRestoreManager"
    private val db = AppDatabase.getDatabase(appContext)
    private val tombstoneDao = db.deletionTombstoneDao()
    private val apiClient by lazy { ApiClient.getInstance(appContext) }
    private val tokenManager by lazy { TokenManager.getInstance(appContext) }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val isoFormatAlt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val localDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val _restoreState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restoreState: StateFlow<RestoreState> = _restoreState.asStateFlow()

    private val _restoreProgressFlow = MutableStateFlow(
        RestoreProgressInfo(
            isRestoring = false,
            lastRestoreTimestamp = tokenManager.getLastRestoreTimestamp(),
            lastSummary = tokenManager.getLastRestoreSummary()
        )
    )
    val restoreProgressFlow: StateFlow<RestoreProgressInfo> = _restoreProgressFlow.asStateFlow()

    private fun parseDateMillis(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            isoFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                isoFormatAlt.parse(dateStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    suspend fun clearLocalDataIfDifferentAccount(currentFirebaseUid: String) = withContext(Dispatchers.IO) {
        val lastStoredUid = tokenManager.getUserId()
        if (!lastStoredUid.isNullOrBlank() && lastStoredUid != currentFirebaseUid) {
            Log.d(TAG, "Different account detected: previous=$lastStoredUid, current=$currentFirebaseUid. Preserving local data with owner-based isolation.")
        }
    }

    suspend fun restoreUserData(): Result<SyncRestoreCounts> = withContext(Dispatchers.IO) {
        if (!tokenManager.isLoggedIn()) {
            val errorMsg = "Login required to restore cloud backup."
            _restoreState.value = RestoreState.Error(errorMsg)
            _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                isRestoring = false,
                lastError = errorMsg
            )
            return@withContext Result.failure(IllegalStateException(errorMsg))
        }

        try {
            _restoreState.value = RestoreState.Restoring(0, 100, "Connecting to cloud backup...")
            _restoreProgressFlow.value = RestoreProgressInfo(
                isRestoring = true,
                currentStepMessage = "Connecting to cloud backup...",
                lastRestoreTimestamp = tokenManager.getLastRestoreTimestamp()
            )

            // Try primary restore endpoint (/sync/restore)
            val restoreData: SyncRestoreData
            val response = apiClient.syncApi.restoreUserData()
            val primaryData = response.body()?.data

            if (response.isSuccessful && primaryData != null) {
                restoreData = primaryData
            } else if (response.code() == 404) {
                // Backward-compatible fallback: When server is deployed without /sync/restore,
                // fall back to /sync/pull which is supported and contains records, fields & evidence
                Log.w(TAG, "GET /sync/restore returned 404. Falling back to /sync/pull.")
                _restoreState.value = RestoreState.Restoring(5, 100, "Retrieving records from cloud...")
                _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                    currentStepMessage = "Retrieving records from cloud..."
                )
                val pullResponse = apiClient.syncApi.pullSync()
                val pullData = pullResponse.body()?.data
                if (pullResponse.isSuccessful && pullData != null) {
                    val pFields = pullData.fields ?: emptyList()
                    val pCropRecords = pullData.cropRecords ?: emptyList()
                    val pEvidenceList = pullData.evidenceList ?: emptyList()
                    val pDelEv = pullData.deletedEvidenceIds ?: emptyList()
                    val pDelRec = pullData.deletedRecordIds ?: emptyList()
                    restoreData = SyncRestoreData(
                        serverTime = pullData.serverTime,
                        firebaseUid = tokenManager.getUserId(),
                        profile = null,
                        fields = pFields,
                        cropRecords = pCropRecords,
                        evidenceList = pEvidenceList,
                        confirmations = emptyList(),
                        deletedEvidenceIds = pDelEv,
                        deletedRecordIds = pDelRec,
                        totalCounts = SyncRestoreCounts(
                            fields = pFields.size,
                            cropRecords = pCropRecords.size,
                            evidenceList = pEvidenceList.size,
                            confirmations = 0
                        )
                    )
                } else {
                    val errorMsg = "Cloud restore fallback failed: (code ${pullResponse.code()})"
                    _restoreState.value = RestoreState.Error(errorMsg)
                    _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                        isRestoring = false,
                        lastError = errorMsg
                    )
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } else {
                val errorMsg = "Cloud restore failed: ${response.message()} (code ${response.code()})"
                _restoreState.value = RestoreState.Error(errorMsg)
                _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                    isRestoring = false,
                    lastError = errorMsg
                )
                return@withContext Result.failure(Exception(errorMsg))
            }

            val currentUid = tokenManager.getActiveUid()
            // Clean up any records/evidence that were deleted on cloud or tombstoned locally
            val remoteDeletedEvidenceIds = restoreData.deletedEvidenceIds ?: emptyList()
            val remoteDeletedRecordIds = restoreData.deletedRecordIds ?: emptyList()
            val localTombstones = tombstoneDao.getAllTombstoneUuids(currentUid)
            val allTombstones = (remoteDeletedEvidenceIds + remoteDeletedRecordIds + localTombstones).toSet()

            for (delId in remoteDeletedRecordIds) {
                db.cropRecordDao().deleteRecordById(delId, currentUid)
                tombstoneDao.insertTombstone(
                    DeletionTombstoneEntity(
                        recordUuid = delId,
                        recordType = "CROP_RECORD",
                        recordId = delId,
                        firebaseUid = currentUid,
                        ownerFirebaseUid = currentUid,
                        syncStatus = SyncStatus.DELETED
                    )
                )
            }

            for (delId in remoteDeletedEvidenceIds) {
                val localEv = db.geotaggedEvidenceDao().getEvidenceById(delId, currentUid) ?: db.geotaggedEvidenceDao().getEvidenceById(delId)
                if (localEv != null) {
                    try {
                        File(localEv.originalPhotoPath).takeIf { it.exists() }?.delete()
                        localEv.watermarkedPhotoPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
                    } catch (_: Exception) {}
                    db.geotaggedEvidenceDao().deleteEvidenceById(delId, currentUid)
                }
                tombstoneDao.insertTombstone(
                    DeletionTombstoneEntity(
                        recordUuid = delId,
                        recordType = "EVIDENCE",
                        recordId = localEv?.recordId ?: "",
                        firebaseUid = currentUid,
                        ownerFirebaseUid = currentUid,
                        syncStatus = SyncStatus.DELETED
                    )
                )
            }

            val cropRecordsList = restoreData.cropRecords ?: emptyList()
            val rawEvidenceList = restoreData.evidenceList ?: emptyList()
            val fieldsList = restoreData.fields ?: emptyList()

            _restoreState.value = RestoreState.Restoring(15, 100, "Restoring farmer profile and field data...")
            _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                totalRecords = cropRecordsList.size,
                totalPhotos = rawEvidenceList.size,
                currentStepMessage = "Restoring farmer profile and field data..."
            )

            // 1. Restore Farmer Profile
            val profile = restoreData.profile
            if (profile != null) {
                val profileEntity = FarmerProfileEntity(
                    farmerId = profile.userId,
                    ownerFirebaseUid = currentUid.ifEmpty { profile.userId },
                    fullName = profile.fullName.ifEmpty { tokenManager.getUserName().orEmpty() },
                    phoneNumber = profile.phone.ifEmpty { tokenManager.getUserPhone().orEmpty() },
                    village = profile.village,
                    district = profile.district,
                    state = profile.state,
                    preferredLanguage = profile.preferredLanguage
                )
                db.farmerProfileDao().insertOrUpdate(profileEntity)
            }

            // 2. Restore Fields and Crop Cultivation Records
            _restoreState.value = RestoreState.Restoring(30, 100, "Restoring crop cultivation records...")
            _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                currentStepMessage = "Restoring crop cultivation records..."
            )

            val fieldMap = fieldsList.associateBy { it.fieldId }
            val cropEntitiesToSave = mutableListOf<CropRecordEntity>()

            for (r in cropRecordsList) {
                // Skip if deleted or tombstoned
                if (r.recordId in allTombstones) {
                    Log.d(TAG, "Skipping restored crop record ${r.recordId} because it is deleted/tombstoned.")
                    continue
                }

                val field = fieldMap[r.fieldId]
                val createdAtMillis = parseDateMillis(r.createdAt)
                val cloudUpdatedAt = parseDateMillis(r.updatedAt)

                // Preservation check: Do not overwrite newer local records with older cloud versions!
                val local = db.cropRecordDao().getRecordById(r.recordId)
                if (local != null && local.updatedAt > cloudUpdatedAt) {
                    continue
                }

                val sowingDateFormatted = if (r.cultivationStartDate.length >= 10) {
                    r.cultivationStartDate.take(10)
                } else {
                    r.cultivationStartDate
                }

                cropEntitiesToSave.add(
                    CropRecordEntity(
                        recordId = r.recordId,
                        farmerId = r.farmerId.ifEmpty { profile?.userId ?: currentUid },
                        ownerFirebaseUid = currentUid,
                        fieldName = field?.fieldName ?: r.fieldName ?: "Cultivation Field",
                        village = field?.village ?: r.village ?: profile?.village ?: "",
                        district = field?.district ?: r.district ?: profile?.district ?: "",
                        state = field?.state ?: r.state ?: profile?.state ?: "",
                        cropType = r.cropType,
                        sowingDate = sowingDateFormatted,
                        fieldDescription = r.notes.takeIf { it.isNotBlank() } ?: field?.description,
                        createdAt = createdAtMillis,
                        updatedAt = cloudUpdatedAt,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        landlordName = r.landlordName.takeIf { it.isNotBlank() },
                        landlordPhone = r.landlordPhone.takeIf { it.isNotBlank() },
                        landlordConfirmationStatus = when (r.landlordConfirmationStatus) {
                            "CONFIRMED", "CONFIRMED_WRITTEN" -> LandlordStatus.CONFIRMED_WRITTEN
                            "CONFIRMED_VERBAL" -> LandlordStatus.CONFIRMED_VERBAL
                            "REFUSED", "DISPUTED", "DECLINED" -> LandlordStatus.REFUSED
                            "UNRESPONSIVE" -> LandlordStatus.UNRESPONSIVE
                            "REQUESTED", "PENDING" -> LandlordStatus.REQUESTED
                            else -> LandlordStatus.NOT_REQUESTED
                        },
                        landlordConfirmationNotes = r.landlordConfirmationNotes
                    )
                )
            }

            if (cropEntitiesToSave.isNotEmpty()) {
                db.cropRecordDao().insertRecords(cropEntitiesToSave)
            }

            _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                restoredRecords = cropEntitiesToSave.size,
                currentStepMessage = "Records restored. Downloading verified photos..."
            )

            // 3. Download Authorized Evidence Photographs and Restore Metadata
            val validEvidenceList = rawEvidenceList.filter { ev ->
                ev.evidenceId !in allTombstones && ev.recordId !in allTombstones
            }
            val totalEv = validEvidenceList.size
            var downloadedPhotos = 0
            var failedPhotos = 0
            val photosDir = FileUtils.getEvidencePhotosDirectory(appContext)

            for ((index, ev) in validEvidenceList.withIndex()) {
                val stepProgress = 40 + if (totalEv > 0) ((index + 1) * 55 / totalEv) else 55
                val msg = "Restoring evidence photo ${index + 1} of $totalEv..."
                _restoreState.value = RestoreState.Restoring(stepProgress, 100, msg)
                _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                    currentStepMessage = msg,
                    downloadedPhotos = downloadedPhotos,
                    failedPhotos = failedPhotos
                )

                val targetPhotoFile = File(photosDir, "ORIG_${ev.recordId}_${ev.evidenceId}.jpg")

                // Determine photo download URL
                var downloadUrl = ev.photoUrl ?: ev.cloudinaryUrl ?: ev.downloadUrl
                if (downloadUrl.isNullOrBlank()) {
                    try {
                        val dlResponse = apiClient.evidenceApi.getDownloadUrl(ev.evidenceId)
                        val dlData = dlResponse.body()?.data
                        if (dlResponse.isSuccessful && dlData != null) {
                            downloadUrl = dlData.downloadUrl
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed requesting download URL for ${ev.evidenceId}: ${e.message}")
                    }
                }

                // Download photo binary if not present locally
                if (!targetPhotoFile.exists() || targetPhotoFile.length() == 0L) {
                    var downloadOk = false
                    if (!downloadUrl.isNullOrBlank()) {
                        try {
                            val resolvedUrl = if (downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
                                downloadUrl
                            } else {
                                val fullBase = ServerConfig.getBaseUrl(appContext)
                                val urlObj = java.net.URL(fullBase)
                                val hostOrigin = "${urlObj.protocol}://${urlObj.host}${if (urlObj.port != -1 && urlObj.port != 80 && urlObj.port != 443) ":${urlObj.port}" else ""}"
                                if (downloadUrl.startsWith("/")) {
                                    hostOrigin + downloadUrl
                                } else {
                                    fullBase.removeSuffix("/") + "/" + downloadUrl
                                }
                            }

                            val dlRequest = Request.Builder().url(resolvedUrl).build()
                            val dlResponse = apiClient.okHttpClient.newCall(dlRequest).execute()
                            if (dlResponse.isSuccessful && dlResponse.body != null) {
                                val bytes = dlResponse.body!!.bytes()
                                targetPhotoFile.writeBytes(bytes)
                                downloadedPhotos++
                                downloadOk = true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not download image from $downloadUrl: ${e.message}")
                        }
                    }
                    if (!downloadOk) {
                        failedPhotos++
                    }
                } else {
                    downloadedPhotos++
                }

                val calculatedSha256 = if (targetPhotoFile.exists() && targetPhotoFile.length() > 0L) {
                    ChecksumUtils.calculateSha256(targetPhotoFile)
                } else {
                    ev.sha256Checksum ?: ""
                }

                val capturedMillis = parseDateMillis(ev.clientCapturedAtUtc)
                val formattedLocal = localDateFormat.format(Date(capturedMillis))

                val evidenceEntity = GeotaggedEvidenceEntity(
                    evidenceId = ev.evidenceId,
                    recordId = ev.recordId,
                    ownerFirebaseUid = currentUid,
                    originalPhotoPath = if (targetPhotoFile.exists()) targetPhotoFile.absolutePath else "",
                    watermarkedPhotoPath = if (targetPhotoFile.exists()) targetPhotoFile.absolutePath else null,
                    photoSha256 = ev.sha256Checksum ?: calculatedSha256,
                    latitude = ev.latitude,
                    longitude = ev.longitude,
                    altitude = ev.altitude ?: 0.0,
                    accuracyMeters = ev.reportedGpsAccuracyMeters,
                    gpsFixAgeMs = (ev.clientFixAgeSeconds ?: 0L) * 1000L,
                    capturedAtUtc = capturedMillis,
                    capturedAtLocalFormatted = formattedLocal,
                    deviceModel = ev.deviceModel ?: Build.MODEL,
                    provider = "gps",
                    notes = ev.notes,
                    syncStatus = SyncStatus.SYNCED
                )

                db.geotaggedEvidenceDao().insertEvidence(evidenceEntity)
            }

            val now = System.currentTimeMillis()
            tokenManager.saveLastSyncTimestamp(now)
            tokenManager.saveLastSyncStatus("SYNCED")
            tokenManager.saveLastSyncMessage(null)
            tokenManager.saveLastRestoreTimestamp(now)
            val summaryStr = "Records restored: ${cropEntitiesToSave.size}  •  Photos downloaded: $downloadedPhotos"
            tokenManager.saveLastRestoreSummary(summaryStr)

            val counts = SyncRestoreCounts(
                fields = fieldsList.size,
                cropRecords = cropEntitiesToSave.size,
                evidenceList = validEvidenceList.size,
                confirmations = (restoreData.confirmations ?: emptyList()).size
            )

            _restoreState.value = RestoreState.Success(cropEntitiesToSave.size, downloadedPhotos)
            _restoreProgressFlow.value = RestoreProgressInfo(
                isRestoring = false,
                totalRecords = cropRecordsList.size,
                restoredRecords = cropEntitiesToSave.size,
                totalPhotos = totalEv,
                downloadedPhotos = downloadedPhotos,
                failedPhotos = failedPhotos,
                currentStepMessage = "Restore completed successfully.",
                lastRestoreTimestamp = now,
                lastSummary = summaryStr
            )

            Log.d(TAG, "Cloud restore finished successfully: ${cropEntitiesToSave.size} records, $downloadedPhotos photos.")
            Result.success(counts)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring account data from cloud", e)
            val errMsg = e.localizedMessage ?: "Unknown restore error occurred."
            _restoreState.value = RestoreState.Error(errMsg)
            _restoreProgressFlow.value = _restoreProgressFlow.value.copy(
                isRestoring = false,
                lastError = errMsg
            )
            Result.failure(e)
        }
    }

    companion object {
        @Volatile
        private var instance: CloudRestoreManager? = null

        fun getInstance(context: Context): CloudRestoreManager =
            instance ?: synchronized(this) {
                instance ?: CloudRestoreManager(context.applicationContext).also { instance = it }
            }
    }
}
