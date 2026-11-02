package org.bataydar.evidence.data.repository

import android.content.Context
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.bataydar.evidence.data.local.AuditLogDao
import org.bataydar.evidence.data.local.CropRecordDao
import org.bataydar.evidence.data.local.DeletionTombstoneDao
import org.bataydar.evidence.data.local.GeotaggedEvidenceDao
import org.bataydar.evidence.data.model.AuditLogEntity
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.DeletionTombstoneEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.data.model.SyncStatus
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.export.ChecksumUtils
import org.bataydar.evidence.util.FileUtils
import org.bataydar.evidence.util.ImageWatermarkUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class EvidenceRepository(
    private val context: Context,
    private val cropRecordDao: CropRecordDao,
    private val evidenceDao: GeotaggedEvidenceDao,
    private val auditLogDao: AuditLogDao,
    private val deletionTombstoneDao: DeletionTombstoneDao
) {

    private val tokenManager by lazy { TokenManager.getInstance(context) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allRecordsFlow: Flow<List<RecordWithEvidence>> = tokenManager.activeUserUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList())
        else cropRecordDao.getAllRecordsWithEvidenceFlow(uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalFieldsCount: Flow<Int> = tokenManager.activeUserUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(0)
        else cropRecordDao.getTotalFieldsCount(uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalEvidenceCount: Flow<Int> = tokenManager.activeUserUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(0)
        else evidenceDao.getTotalEvidenceCount(uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val awaitingLandlordCount: Flow<Int> = tokenManager.activeUserUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(0)
        else cropRecordDao.getAwaitingLandlordCount(uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingSyncCount: Flow<Int> = tokenManager.activeUserUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(0)
        else cropRecordDao.getPendingSyncCount(uid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getRecentRecords(limit: Int = 5): Flow<List<RecordWithEvidence>> = tokenManager.activeUserUidFlow.flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList())
        else cropRecordDao.getRecentRecords(limit, uid)
    }

    suspend fun getRecordWithEvidence(recordId: String): RecordWithEvidence? {
        val uid = tokenManager.getActiveUid()
        return if (uid.isNotBlank()) {
            cropRecordDao.getRecordWithEvidence(recordId, uid)
        } else {
            cropRecordDao.getRecordWithEvidence(recordId)
        }
    }

    fun getRecordWithEvidenceFlow(recordId: String): Flow<RecordWithEvidence?> {
        val uid = tokenManager.getActiveUid()
        return if (uid.isNotBlank()) {
            cropRecordDao.getRecordWithEvidenceFlow(recordId, uid)
        } else {
            cropRecordDao.getRecordWithEvidenceFlow(recordId)
        }
    }

    fun getEvidenceForRecordFlow(recordId: String): Flow<List<GeotaggedEvidenceEntity>> {
        val uid = tokenManager.getActiveUid()
        return if (uid.isNotBlank()) {
            evidenceDao.getEvidenceForRecordFlow(recordId, uid)
        } else {
            evidenceDao.getEvidenceForRecordFlow(recordId)
        }
    }

    fun getAuditLogsForRecordFlow(recordId: String): Flow<List<AuditLogEntity>> {
        return auditLogDao.getLogsForRecordFlow(recordId)
    }

    suspend fun createCropRecord(
        farmerId: String,
        fieldName: String,
        village: String,
        district: String,
        state: String,
        cropType: String,
        sowingDate: String,
        fieldDescription: String?,
        landlordName: String? = null,
        landlordPhone: String? = null
    ): Result<CropRecordEntity> = withContext(Dispatchers.IO) {
        val trimmedField = fieldName.trim()
        val trimmedCrop = cropType.trim()
        val trimmedDate = sowingDate.trim()

        if (trimmedField.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Field name/identifier is required"))
        }
        if (trimmedCrop.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Crop type is required"))
        }
        if (trimmedDate.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Sowing date is required"))
        }

        val currentUid = tokenManager.getActiveUid().ifEmpty {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: farmerId
        }

        val recordId = generateRecordId()
        val entity = CropRecordEntity(
            recordId = recordId,
            farmerId = farmerId,
            ownerFirebaseUid = currentUid,
            fieldName = trimmedField,
            village = village.trim(),
            district = district.trim(),
            state = state.trim(),
            cropType = trimmedCrop,
            sowingDate = trimmedDate,
            fieldDescription = fieldDescription?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.LOCAL_ONLY,
            landlordName = landlordName?.trim()?.takeIf { it.isNotEmpty() },
            landlordPhone = landlordPhone?.trim()?.takeIf { it.isNotEmpty() },
            landlordConfirmationStatus = LandlordStatus.NOT_REQUESTED
        )

        cropRecordDao.insertRecord(entity)

        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = recordId,
                ownerFirebaseUid = currentUid,
                action = "RECORD_CREATED",
                timestamp = System.currentTimeMillis(),
                details = "Created record for $trimmedCrop at $trimmedField by farmer $farmerId"
            )
        )

        Result.success(entity)
    }

    suspend fun updateCropRecord(record: CropRecordEntity) = withContext(Dispatchers.IO) {
        val currentUid = record.ownerFirebaseUid.ifEmpty { tokenManager.getActiveUid() }
        cropRecordDao.updateRecord(record)
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = record.recordId,
                ownerFirebaseUid = currentUid,
                action = "RECORD_UPDATED",
                timestamp = System.currentTimeMillis(),
                details = "Updated record details for ${record.fieldName}"
            )
        )
    }

    suspend fun saveGeotaggedEvidence(
        recordId: String,
        photoFile: File,
        location: Location,
        notes: String? = null
    ): Result<GeotaggedEvidenceEntity> = withContext(Dispatchers.IO) {
        if (!photoFile.exists() || photoFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Photo file does not exist or is empty"))
        }

        val record = cropRecordDao.getRecordWithEvidence(recordId)?.record
            ?: return@withContext Result.failure(IllegalArgumentException("Record $recordId not found"))

        // Strict GPS integrity enforcement: accuracy <= 20m, freshness <= 10s, satellite only, non-mock
        if (!location.hasAccuracy() || location.accuracy > 20.0f) {
            return@withContext Result.failure(
                IllegalArgumentException("Geotagged evidence requires GPS accuracy under 20.0 metres. Current: ${if (location.hasAccuracy()) "${location.accuracy}m" else "None"}")
            )
        }

        val provider = (location.provider ?: "").lowercase(Locale.US)
        if (provider.contains("network") || provider == "coarse") {
            return@withContext Result.failure(
                IllegalArgumentException("Network or coarse-derived location cannot bypass direct satellite GPS requirement.")
            )
        }

        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        } else {
            false
        }
        if (isMock) {
            return@withContext Result.failure(
                IllegalArgumentException("Mock or simulated GPS coordinates cannot be used for legal evidence.")
            )
        }

        val nowUtc = System.currentTimeMillis()
        val fixAgeMs = (nowUtc - location.time).coerceAtLeast(0L)
        if (fixAgeMs > 10_000L) {
            return@withContext Result.failure(
                IllegalArgumentException("GPS location fix is stale (${fixAgeMs / 1000}s old). Must be under 10 seconds.")
            )
        }

        // 1. Calculate cryptographically verifiable SHA-256 of the untouched original photo
        val sha256 = ChecksumUtils.calculateSha256(photoFile)

        // 2. Format capture timestamps (UTC and local display)
        val localSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        val localDisplayTime = localSdf.format(Date(nowUtc))

        // 3. Create derivative watermarked image (original is kept untouched!)
        val derivativeFile = FileUtils.createWatermarkedPhotoFile(context, recordId)
        val watermarkedCreated = ImageWatermarkUtils.createDerivativeWatermarkedImage(
            originalFile = photoFile,
            destinationFile = derivativeFile,
            cropType = record.cropType,
            fieldName = record.fieldName,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            localTimeFormatted = localDisplayTime,
            sha256Prefix = sha256
        )

        val evidenceId = generateEvidenceId()
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        val currentUid = record.ownerFirebaseUid.ifEmpty {
            tokenManager.getActiveUid().ifEmpty {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            }
        }

        val evidenceEntity = GeotaggedEvidenceEntity(
            evidenceId = evidenceId,
            recordId = recordId,
            ownerFirebaseUid = currentUid,
            originalPhotoPath = photoFile.absolutePath,
            watermarkedPhotoPath = if (watermarkedCreated) derivativeFile.absolutePath else null,
            photoSha256 = sha256,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracyMeters = location.accuracy,
            gpsFixAgeMs = fixAgeMs,
            capturedAtUtc = nowUtc,
            capturedAtLocalFormatted = localDisplayTime,
            deviceModel = deviceModel,
            provider = location.provider ?: "gps",
            notes = notes,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        evidenceDao.insertEvidence(evidenceEntity)

        // Ensure crop record is marked as pending upload since new evidence was attached
        cropRecordDao.updateSyncStatus(recordId, SyncStatus.LOCAL_ONLY, null, nowUtc)

        // Log audit event
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = recordId,
                ownerFirebaseUid = currentUid,
                action = "EVIDENCE_CAPTURED",
                timestamp = nowUtc,
                details = String.format(
                    Locale.US,
                    "Geotagged photo saved. GPS: %.6f, %.6f (acc: %.1fm). SHA-256: %s",
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                    sha256
                )
            )
        )

        Result.success(evidenceEntity)
    }

    suspend fun updateLandlordConfirmation(
        recordId: String,
        status: LandlordStatus,
        notes: String?
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cropRecordDao.updateLandlordStatus(
            recordId = recordId,
            status = status,
            date = now,
            notes = notes
        )
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = recordId,
                ownerFirebaseUid = tokenManager.getActiveUid(),
                action = "LANDLORD_CONFIRMATION_UPDATED",
                timestamp = now,
                details = "Landlord status updated to ${status.name}. Notes: ${notes ?: "None"}"
            )
        )
    }

    /**
     * Delete evidence from THIS device only.
     * Removes the physical image files and deletes Room database row.
     * Does not delete cloud evidence or create a cloud deletion tombstone.
     */
    suspend fun deleteEvidenceLocally(evidenceId: String) = withContext(Dispatchers.IO) {
        val evidence = evidenceDao.getEvidenceById(evidenceId, tokenManager.getActiveUid()) ?: evidenceDao.getEvidenceById(evidenceId)
        if (evidence != null) {
            try {
                File(evidence.originalPhotoPath).takeIf { it.exists() }?.delete()
                evidence.watermarkedPhotoPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            evidenceDao.deleteEvidenceById(evidenceId)
            logAuditAction(evidence.recordId, "EVIDENCE_DELETED_LOCALLY", "Evidence photo $evidenceId deleted from local device only.")
        }
    }

    /**
     * Delete evidence EVERYWHERE (Permanent Cloud & Local Deletion).
     * Deletes local files, removes Room record, and establishes a local deletion tombstone
     * (marked DELETION_PENDING) so the deletion synchronizes to the cloud upon next Upload Data / Sync.
     */
    suspend fun deleteEvidenceEverywhere(evidenceId: String) = withContext(Dispatchers.IO) {
        val evidence = evidenceDao.getEvidenceById(evidenceId, tokenManager.getActiveUid()) ?: evidenceDao.getEvidenceById(evidenceId)
        val uid = evidence?.ownerFirebaseUid?.ifEmpty { tokenManager.getActiveUid() } ?: tokenManager.getActiveUid()
        if (evidence != null) {
            try {
                File(evidence.originalPhotoPath).takeIf { it.exists() }?.delete()
                evidence.watermarkedPhotoPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Create tombstone to queue cloud deletion
            val tombstone = DeletionTombstoneEntity(
                recordUuid = evidence.evidenceId,
                recordType = "EVIDENCE",
                recordId = evidence.recordId,
                firebaseUid = uid,
                ownerFirebaseUid = uid,
                deletedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.DELETION_PENDING
            )
            deletionTombstoneDao.insertTombstone(tombstone)

            // Remove from local active evidence table
            evidenceDao.deleteEvidenceById(evidenceId)
            logAuditAction(evidence.recordId, "EVIDENCE_DELETED_EVERYWHERE", "Evidence photo $evidenceId queued for cloud deletion and permanent removal.")
        } else {
            // Even if not in local Room, queue tombstone so cloud deletion takes place
            val tombstone = DeletionTombstoneEntity(
                recordUuid = evidenceId,
                recordType = "EVIDENCE",
                recordId = "",
                firebaseUid = uid,
                ownerFirebaseUid = uid,
                deletedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.DELETION_PENDING
            )
            deletionTombstoneDao.insertTombstone(tombstone)
        }
    }

    /**
     * Delete entire crop record and associated evidence LOCALLY only.
     */
    suspend fun deleteRecordLocally(recordId: String) = withContext(Dispatchers.IO) {
        val recordWithEvidence = cropRecordDao.getRecordWithEvidence(recordId, tokenManager.getActiveUid()) ?: cropRecordDao.getRecordWithEvidence(recordId)
        if (recordWithEvidence != null) {
            recordWithEvidence.evidenceList.forEach { evidence ->
                try {
                    File(evidence.originalPhotoPath).takeIf { it.exists() }?.delete()
                    evidence.watermarkedPhotoPath?.let { path ->
                        File(path).takeIf { it.exists() }?.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            cropRecordDao.deleteRecordById(recordId)
            logAuditAction(recordId, "RECORD_DELETED_LOCALLY", "Crop record $recordId deleted from local device only.")
        }
    }

    /**
     * Delete entire crop record and associated evidence EVERYWHERE.
     * Queues deletion tombstones for the crop record and all associated photos.
     */
    suspend fun deleteRecordEverywhere(recordId: String) = withContext(Dispatchers.IO) {
        val recordWithEvidence = cropRecordDao.getRecordWithEvidence(recordId, tokenManager.getActiveUid()) ?: cropRecordDao.getRecordWithEvidence(recordId)
        val uid = recordWithEvidence?.record?.ownerFirebaseUid?.ifEmpty { tokenManager.getActiveUid() } ?: tokenManager.getActiveUid()
        if (recordWithEvidence != null) {
            recordWithEvidence.evidenceList.forEach { evidence ->
                try {
                    File(evidence.originalPhotoPath).takeIf { it.exists() }?.delete()
                    evidence.watermarkedPhotoPath?.let { path ->
                        File(path).takeIf { it.exists() }?.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val evTombstone = DeletionTombstoneEntity(
                    recordUuid = evidence.evidenceId,
                    recordType = "EVIDENCE",
                    recordId = recordId,
                    firebaseUid = uid,
                    ownerFirebaseUid = uid,
                    deletedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.DELETION_PENDING
                )
                deletionTombstoneDao.insertTombstone(evTombstone)
            }

            val recTombstone = DeletionTombstoneEntity(
                recordUuid = recordId,
                recordType = "CROP_RECORD",
                recordId = recordId,
                firebaseUid = uid,
                ownerFirebaseUid = uid,
                deletedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.DELETION_PENDING
            )
            deletionTombstoneDao.insertTombstone(recTombstone)

            cropRecordDao.deleteRecordById(recordId)
            logAuditAction(recordId, "RECORD_DELETED_EVERYWHERE", "Crop record $recordId queued for cloud deletion and permanent removal.")
        } else {
            val recTombstone = DeletionTombstoneEntity(
                recordUuid = recordId,
                recordType = "CROP_RECORD",
                recordId = recordId,
                firebaseUid = uid,
                ownerFirebaseUid = uid,
                deletedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.DELETION_PENDING
            )
            deletionTombstoneDao.insertTombstone(recTombstone)
        }
    }

    // Default delete methods map to deleteEverywhere for safety
    suspend fun deleteRecord(recordId: String) = deleteRecordEverywhere(recordId)
    suspend fun deleteEvidence(evidenceId: String) = deleteEvidenceEverywhere(evidenceId)

    suspend fun logAuditAction(recordId: String, action: String, details: String) = withContext(Dispatchers.IO) {
        val uid = tokenManager.getActiveUid()
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = recordId,
                ownerFirebaseUid = uid,
                action = action,
                timestamp = System.currentTimeMillis(),
                details = details
            )
        )
    }

    fun generateRecordId(): String {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomPart = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "REC-$datePart-$randomPart"
    }

    fun generateEvidenceId(): String {
        return UUID.randomUUID().toString()
    }
}
