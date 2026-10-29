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
    @SerializedName("status") val status: String = "ACTIVE"
)

data class CropRecordData(
    @SerializedName("record") val record: CropRecordDto
)

data class CropRecordListData(
    @SerializedName("records") val records: List<CropRecordDto>
)
