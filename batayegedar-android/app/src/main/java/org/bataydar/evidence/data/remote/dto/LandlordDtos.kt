package org.bataydar.evidence.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LandlordDashboardDto(
    @SerializedName("pendingCount") val pendingCount: Int = 0,
    @SerializedName("completedCount") val completedCount: Int = 0,
    @SerializedName("totalRequests") val totalRequests: Int = 0,
    @SerializedName("recentActivity") val recentActivity: List<LandlordActivityItemDto> = emptyList()
)

data class LandlordActivityItemDto(
    @SerializedName("confirmationId") val confirmationId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("status") val status: String,
    @SerializedName("requestedAt") val requestedAt: String? = null,
    @SerializedName("respondedAt") val respondedAt: String? = null,
    @SerializedName("fieldName") val fieldName: String? = null,
    @SerializedName("cropType") val cropType: String? = null,
    @SerializedName("farmerName") val farmerName: String? = null,
    @SerializedName("farmerPhone") val farmerPhone: String? = null
)

data class LandlordRequestsResponseDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("requests") val requests: List<LandlordRequestItemDto> = emptyList()
)

data class LandlordRequestItemDto(
    @SerializedName("confirmationId") val confirmationId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("status") val status: String,
    @SerializedName("requestedAt") val requestedAt: String? = null,
    @SerializedName("respondedAt") val respondedAt: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("witnessName") val witnessName: String? = null,
    @SerializedName("farmer") val farmer: LandlordFarmerDto? = null,
    @SerializedName("cropRecord") val cropRecord: LandlordCropRecordDto? = null,
    @SerializedName("evidenceCount") val evidenceCount: Int = 0,
    @SerializedName("evidencePhotos") val evidencePhotos: List<LandlordEvidencePhotoDto> = emptyList()
)

data class LandlordFarmerDto(
    @SerializedName("farmerId") val farmerId: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("village") val village: String? = null,
    @SerializedName("district") val district: String? = null,
    @SerializedName("state") val state: String? = null
)

data class LandlordCropRecordDto(
    @SerializedName("recordId") val recordId: String,
    @SerializedName("fieldName") val fieldName: String,
    @SerializedName("cropType") val cropType: String,
    @SerializedName("sowingDate") val sowingDate: String? = null,
    @SerializedName("fieldDescription") val fieldDescription: String? = null,
    @SerializedName("village") val village: String? = null,
    @SerializedName("district") val district: String? = null,
    @SerializedName("state") val state: String? = null
)

data class LandlordEvidencePhotoDto(
    @SerializedName("evidenceId") val evidenceId: String,
    @SerializedName("photoSha256") val photoSha256: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("accuracyMeters") val accuracyMeters: Float? = null,
    @SerializedName("altitude") val altitude: Double? = null,
    @SerializedName("gpsFixAgeMs") val gpsFixAgeMs: Long? = null,
    @SerializedName("capturedAtLocal") val capturedAtLocal: String? = null,
    @SerializedName("capturedAtUtc") val capturedAtUtc: Long? = null,
    @SerializedName("deviceModel") val deviceModel: String? = null,
    @SerializedName("validationStatus") val validationStatus: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
)

data class LandlordRequestDetailResponseDto(
    @SerializedName("confirmationId") val confirmationId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("status") val status: String,
    @SerializedName("requestedAt") val requestedAt: String? = null,
    @SerializedName("respondedAt") val respondedAt: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("witnessName") val witnessName: String? = null,
    @SerializedName("canRespond") val canRespond: Boolean = false,
    @SerializedName("farmerDeclaredInfo") val farmerDeclaredInfo: FarmerDeclaredInfoDto? = null,
    @SerializedName("independentlyVerifiedTelemetry") val independentlyVerifiedTelemetry: VerifiedTelemetryDto? = null
)

data class FarmerDeclaredInfoDto(
    @SerializedName("farmerId") val farmerId: String? = null,
    @SerializedName("farmerName") val farmerName: String? = null,
    @SerializedName("farmerPhone") val farmerPhone: String? = null,
    @SerializedName("farmerLocation") val farmerLocation: String? = null,
    @SerializedName("fieldName") val fieldName: String? = null,
    @SerializedName("cropType") val cropType: String? = null,
    @SerializedName("sowingDate") val sowingDate: String? = null,
    @SerializedName("fieldDescription") val fieldDescription: String? = null,
    @SerializedName("declaredAt") val declaredAt: String? = null,
    @SerializedName("note") val note: String? = null
)

data class VerifiedTelemetryDto(
    @SerializedName("totalEvidenceCount") val totalEvidenceCount: Int = 0,
    @SerializedName("evidencePhotos") val evidencePhotos: List<LandlordEvidencePhotoDto> = emptyList(),
    @SerializedName("gpsMethod") val gpsMethod: String? = null,
    @SerializedName("integrityProtection") val integrityProtection: String? = null,
    @SerializedName("note") val note: String? = null
)

data class RespondLandlordRequest(
    @SerializedName("status") val status: String,
    @SerializedName("notes") val notes: String = "",
    @SerializedName("witnessName") val witnessName: String = ""
)

data class LandlordHistoryResponseDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("history") val history: List<LandlordHistoryItemDto> = emptyList()
)

data class LandlordHistoryItemDto(
    @SerializedName("confirmationId") val confirmationId: String,
    @SerializedName("recordId") val recordId: String,
    @SerializedName("status") val status: String,
    @SerializedName("requestedAt") val requestedAt: String? = null,
    @SerializedName("respondedAt") val respondedAt: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("witnessName") val witnessName: String? = null,
    @SerializedName("farmerName") val farmerName: String? = null,
    @SerializedName("farmerPhone") val farmerPhone: String? = null,
    @SerializedName("fieldName") val fieldName: String? = null,
    @SerializedName("cropType") val cropType: String? = null
)
