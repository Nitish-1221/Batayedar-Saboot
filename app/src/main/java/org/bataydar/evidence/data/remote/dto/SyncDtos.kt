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
    @SerializedName("areaAcres") val areaAcres: Double = 0.0
)

data class SyncCropRecordItem(
    @SerializedName("recordId") val recordId: String,
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("cropType") val cropType: String,
    @SerializedName("cultivationStartDate") val cultivationStartDate: String,
    @SerializedName("notes") val notes: String = "",
    @SerializedName("landlordName") val landlordName: String = "",
    @SerializedName("landlordPhone") val landlordPhone: String = ""
)

data class SyncEvidenceItem(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("reportedGpsAccuracyMeters") val reportedGpsAccuracyMeters: Float,
    @SerializedName("clientCapturedAtUtc") val clientCapturedAtUtc: String,
    @SerializedName("clientReportedChecksum") val clientReportedChecksum: String = "",
    @SerializedName("notes") val notes: String = ""
)

data class SyncPushData(
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("syncedFieldIds") val syncedFieldIds: List<String>,
    @SerializedName("syncedRecordIds") val syncedRecordIds: List<String>,
    @SerializedName("syncedEvidenceIds") val syncedEvidenceIds: List<String>
)

data class SyncPullData(
    @SerializedName("serverTime") val serverTime: String,
    @SerializedName("fields") val fields: List<FieldDto>,
    @SerializedName("cropRecords") val cropRecords: List<CropRecordDto>,
    @SerializedName("evidenceList") val evidenceList: List<EvidenceDto>
)
