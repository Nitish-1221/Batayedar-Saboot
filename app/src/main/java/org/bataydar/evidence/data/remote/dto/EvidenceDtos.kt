package org.bataydar.evidence.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RequestUploadUrlRequest(
    @SerializedName("recordId") val recordId: String,
    @SerializedName("evidenceId") val evidenceId: String? = null,
    @SerializedName("fileExtension") val fileExtension: String = "jpg",
    @SerializedName("contentType") val contentType: String = "image/jpeg",
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("altitude") val altitude: Double = 0.0,
    @SerializedName("reportedGpsAccuracyMeters") val reportedGpsAccuracyMeters: Float,
    @SerializedName("clientFixAgeSeconds") val clientFixAgeSeconds: Long = 0,
    @SerializedName("clientCapturedAtUtc") val clientCapturedAtUtc: String,
    @SerializedName("deviceManufacturer") val deviceManufacturer: String = "",
    @SerializedName("deviceModel") val deviceModel: String = "",
    @SerializedName("notes") val notes: String = ""
)

data class UploadUrlData(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("objectKey") val objectKey: String,
    @SerializedName("uploadUrl") val uploadUrl: String,
    @SerializedName("storageProvider") val storageProvider: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Long,
    @SerializedName("gpsAccuracyAcceptable") val gpsAccuracyAcceptable: Boolean
)

data class CompleteUploadRequest(
    @SerializedName("clientReportedChecksum") val clientReportedChecksum: String,
    @SerializedName("notes") val notes: String? = null
)

data class EvidenceDto(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("farmerId") val farmerId: String,
    @SerializedName("sha256Checksum") val sha256Checksum: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("reportedGpsAccuracyMeters") val reportedGpsAccuracyMeters: Float,
    @SerializedName("validationStatus") val validationStatus: String,
    @SerializedName("gpsAccuracyAcceptable") val gpsAccuracyAcceptable: Boolean
)

data class EvidenceData(
    @SerializedName("evidence") val evidence: EvidenceDto
)

data class EvidenceListData(
    @SerializedName("evidence") val evidence: List<EvidenceDto>
)
