package org.bataydar.evidence.data.repository

import android.content.Context
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.bataydar.evidence.data.local.AuditLogDao
import org.bataydar.evidence.data.local.CropRecordDao
import org.bataydar.evidence.data.local.GeotaggedEvidenceDao
import org.bataydar.evidence.data.model.AuditLogEntity
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.data.model.SyncStatus
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
    private val auditLogDao: AuditLogDao
) {

    val allRecordsFlow: Flow<List<RecordWithEvidence>> = cropRecordDao.getAllRecordsWithEvidenceFlow()
    val totalFieldsCount: Flow<Int> = cropRecordDao.getTotalFieldsCount()
    val totalEvidenceCount: Flow<Int> = evidenceDao.getTotalEvidenceCount()
    val awaitingLandlordCount: Flow<Int> = cropRecordDao.getAwaitingLandlordCount()
    val pendingSyncCount: Flow<Int> = cropRecordDao.getPendingSyncCount()

    fun getRecentRecords(limit: Int = 5): Flow<List<RecordWithEvidence>> {
        return cropRecordDao.getRecentRecords(limit)
    }

    suspend fun getRecordWithEvidence(recordId: String): RecordWithEvidence? {
        return cropRecordDao.getRecordWithEvidence(recordId)
    }

    fun getRecordWithEvidenceFlow(recordId: String): Flow<RecordWithEvidence?> {
        return cropRecordDao.getRecordWithEvidenceFlow(recordId)
    }

    fun getEvidenceForRecordFlow(recordId: String): Flow<List<GeotaggedEvidenceEntity>> {
        return evidenceDao.getEvidenceForRecordFlow(recordId)
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

        val recordId = generateRecordId()
        val entity = CropRecordEntity(
            recordId = recordId,
            farmerId = farmerId,
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
                action = "RECORD_CREATED",
                timestamp = System.currentTimeMillis(),
                details = "Created record for $trimmedCrop at $trimmedField by farmer $farmerId"
            )
        )

        Result.success(entity)
    }

    suspend fun updateCropRecord(record: CropRecordEntity) = withContext(Dispatchers.IO) {
        cropRecordDao.updateRecord(record)
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = record.recordId,
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

        val evidenceEntity = GeotaggedEvidenceEntity(
            evidenceId = evidenceId,
            recordId = recordId,
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

        // Log audit event
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = recordId,
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
                action = "LANDLORD_CONFIRMATION_UPDATED",
                timestamp = now,
                details = "Landlord status updated to ${status.name}. Notes: ${notes ?: "None"}"
            )
        )
    }

    suspend fun deleteRecord(recordId: String) = withContext(Dispatchers.IO) {
        val recordWithEvidence = cropRecordDao.getRecordWithEvidence(recordId)
        if (recordWithEvidence != null) {
            // Delete associated physical photo files
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
        }
    }

    suspend fun deleteEvidence(evidenceId: String) = withContext(Dispatchers.IO) {
        val evidence = evidenceDao.getEvidenceById(evidenceId)
        if (evidence != null) {
            try {
                File(evidence.originalPhotoPath).takeIf { it.exists() }?.delete()
                evidence.watermarkedPhotoPath?.let { File(it).takeIf { f -> f.exists() }?.delete() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            evidenceDao.deleteEvidenceById(evidenceId)
        }
    }

    suspend fun logAuditAction(recordId: String, action: String, details: String) = withContext(Dispatchers.IO) {
        auditLogDao.insertLog(
            AuditLogEntity(
                recordId = recordId,
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
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomPart = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        return "EVD-$datePart-$randomPart"
    }
}
