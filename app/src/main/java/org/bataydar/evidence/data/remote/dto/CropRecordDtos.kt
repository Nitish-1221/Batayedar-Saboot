package org.bataydar.evidence.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateCropRecordRequest(
    @SerializedName("recordId") val recordId: String? = null,
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("cropType") val cropType: String,
    @SerializedName("cultivationStartDate") val cultivationStartDate: String,
    @SerializedName("notes") val notes: String = "",
    @SerializedName("landlordName") val landlordName: String = "",
    @SerializedName("landlordPhone") val landlordPhone: String = ""
)

data class CropRecordDto(
    @SerializedName("recordId") val recordId: String,
    @SerializedName("farmerId") val farmerId: String,
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("cropType") val cropType: String,
    @SerializedName("cultivationStartDate") val cultivationStartDate: String,
    @SerializedName("notes") val notes: String = "",
    @SerializedName("landlordName") val landlordName: String = "",
    @SerializedName("landlordPhone") val landlordPhone: String = "",
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("fieldName") val fieldName: String? = null,
    @SerializedName("village") val village: String? = null,
    @SerializedName("district") val district: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("landlordConfirmationStatus") val landlordConfirmationStatus: String? = null,
    @SerializedName("landlordConfirmationNotes") val landlordConfirmationNotes: String? = null
)

data class CropRecordData(
    @SerializedName("record") val record: CropRecordDto
)

data class CropRecordListData(
    @SerializedName("records") val records: List<CropRecordDto>
)
