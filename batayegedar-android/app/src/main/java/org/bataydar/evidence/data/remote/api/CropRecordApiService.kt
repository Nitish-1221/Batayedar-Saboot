package org.bataydar.evidence.data.remote.api

import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface CropRecordApiService {

    @POST("crop-records")
    suspend fun createCropRecord(@Body request: CreateCropRecordRequest): Response<ApiResponse<CropRecordData>>

    @GET("crop-records")
    suspend fun getCropRecords(@Query("fieldId") fieldId: String? = null): Response<ApiResponse<CropRecordListData>>

    @GET("crop-records/{recordId}")
    suspend fun getCropRecordById(@Path("recordId") recordId: String): Response<ApiResponse<CropRecordData>>

    @PATCH("crop-records/{recordId}")
    suspend fun updateCropRecord(
        @Path("recordId") recordId: String,
        @Body updates: Map<String, Any>
    ): Response<ApiResponse<CropRecordData>>

    @GET("crop-records/{recordId}/evidence")
    suspend fun getRecordEvidence(@Path("recordId") recordId: String): Response<ApiResponse<EvidenceListData>>

    @POST("crop-records/{recordId}/confirmation-requests")
    suspend fun requestLandlordConfirmation(
        @Path("recordId") recordId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse<Any>>

    @DELETE("crop-records/{recordId}")
    suspend fun deleteCropRecord(@Path("recordId") recordId: String): Response<ApiResponse<Any>>
}
