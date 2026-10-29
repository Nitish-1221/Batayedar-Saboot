package org.bataydar.evidence.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreateFieldRequest(
    @SerializedName("fieldId") val fieldId: String? = null,
    @SerializedName("fieldName") val fieldName: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("village") val village: String,
    @SerializedName("district") val district: String,
    @SerializedName("state") val state: String,
    @SerializedName("areaAcres") val areaAcres: Double = 0.0,
    @SerializedName("surveyKhasraNumber") val surveyKhasraNumber: String = ""
)

data class FieldDto(
    @SerializedName("fieldId") val fieldId: String,
    @SerializedName("farmerId") val farmerId: String,
    @SerializedName("fieldName") val fieldName: String,
    @SerializedName("description") val description: String = "",
    @SerializedName("village") val village: String,
    @SerializedName("district") val district: String,
    @SerializedName("state") val state: String,
    @SerializedName("areaAcres") val areaAcres: Double = 0.0,
    @SerializedName("surveyKhasraNumber") val surveyKhasraNumber: String = ""
)

data class FieldData(
    @SerializedName("field") val field: FieldDto
)

data class FieldListData(
    @SerializedName("fields") val fields: List<FieldDto>
)
