package org.bataydar.evidence.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SyncPushRequest(
    @SerializedName("idempotencyKey") val idempotencyKey: String,
    @SerializedName("fields") val fields: List<SyncFieldItem> = emptyList(),
    @SerializedName("cropRecords") val cropRecords: List<SyncCropRecordItem> = emptyList(),
    @SerializedName("evidenceList") val evidenceList: List<SyncEvidenceItem> = emptyList()
)

data class SyncFieldItem(
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("fieldName") val fieldName: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("village") val village: String,
    @SerializedName("district") val district: String,
    @SerializedName("state") val state: String,
    @SerializedName("areaAcres") val areaAcres: Double = 0.0,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class SyncCropRecordItem(
    @SerializedName("recordId") val recordId: String,
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("cropType") val cropType: String,
    @SerializedName("cultivationStartDate") val cultivationStartDate: String,
    @SerializedName("notes") val notes: String = "",
    @SerializedName("landlordName") val landlordName: String = "",
    @SerializedName("landlordPhone") val landlordPhone: String = "",
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class SyncEvidenceItem(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("reportedGpsAccuracyMeters") val reportedGpsAccuracyMeters: Float,
    @SerializedName("clientCapturedAtUtc") val clientCapturedAtUtc: String,
    @SerializedName("clientReportedChecksum") val clientReportedChecksum: String = "",
    @SerializedName("notes") val notes: String = "",
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class SyncPushData(
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("syncedFieldIds") val syncedFieldIds: List<String>,
    @SerializedName("syncedRecordIds") val syncedRecordIds: List<String>,
    @SerializedName("syncedEvidenceIds") val syncedEvidenceIds: List<String>
)

data class SyncPullData(
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("fields") val fields: List<FieldDto> = emptyList(),
    @SerializedName("cropRecords") val cropRecords: List<CropRecordDto> = emptyList(),
    @SerializedName("evidenceList") val evidenceList: List<EvidenceRestoreDto> = emptyList()
)

data class SyncRestoreData(
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("firebaseUid") val firebaseUid: String?,
    @SerializedName("profile") val profile: UserDto?,
    @SerializedName("fields") val fields: List<FieldDto> = emptyList(),
    @SerializedName("cropRecords") val cropRecords: List<CropRecordDto> = emptyList(),
    @SerializedName("evidenceList") val evidenceList: List<EvidenceRestoreDto> = emptyList(),
    @SerializedName("confirmations") val confirmations: List<LandlordConfirmationItemDto> = emptyList(),
    @SerializedName("totalCounts") val totalCounts: SyncRestoreCounts? = null
)

data class LandlordConfirmationItemDto(
    @SerializedName("confirmationId") val confirmationId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("landlordName") val landlordName: String? = null,
    @SerializedName("landlordPhone") val landlordPhone: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("requestedAt") val requestedAt: String? = null,
    @SerializedName("respondedAt") val respondedAt: String? = null
)

data class EvidenceRestoreDto(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("farmerId") val farmerId: String? = null,
    @SerializedName("firebaseUid") val firebaseUid: String? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    @SerializedName("downloadUrl") val downloadUrl: String? = null,
    @SerializedName("cloudinaryUrl") val cloudinaryUrl: String? = null,
    @SerializedName("cloudinaryPublicId") val cloudinaryPublicId: String? = null,
    @SerializedName("storageProvider") val storageProvider: String? = null,
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("altitude") val altitude: Double? = 0.0,
    @SerializedName("reportedGpsAccuracyMeters") val reportedGpsAccuracyMeters: Float = 0f,
    @SerializedName("clientFixAgeSeconds") val clientFixAgeSeconds: Long? = 0L,
    @SerializedName("clientCapturedAtUtc") val clientCapturedAtUtc: String? = null,
    @SerializedName("deviceModel") val deviceModel: String? = null,
    @SerializedName("validationStatus") val validationStatus: String? = null,
    @SerializedName("sha256Checksum") val sha256Checksum: String? = null,
    @SerializedName("clientReportedChecksum") val clientReportedChecksum: String? = null,
    @SerializedName("notes") val notes: String? = null
)

data class SyncRestoreCounts(
    @SerializedName("fields") val fields: Int = 0,
    @SerializedName("cropRecords") val cropRecords: Int = 0,
    @SerializedName("evidenceList") val evidenceList: Int = 0,
    @SerializedName("confirmations") val confirmations: Int = 0
)

data class EvidenceUploadResponse(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    @SerializedName("cloudinaryUrl") val cloudinaryUrl: String? = null,
    @SerializedName("cloudinaryPublicId") val cloudinaryPublicId: String? = null,
    @SerializedName("storageProvider") val storageProvider: String? = null,
    @SerializedName("validationStatus") val validationStatus: String? = null,
    @SerializedName("sha256Checksum") val sha256Checksum: String? = null
)
