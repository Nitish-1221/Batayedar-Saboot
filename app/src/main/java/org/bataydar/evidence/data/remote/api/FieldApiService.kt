package org.bataydar.evidence.data.remote.api

import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface FieldApiService {

    @POST("fields")
    suspend fun createField(@Body request: CreateFieldRequest): Response<ApiResponse<FieldData>>

    @GET("fields")
    suspend fun getFields(): Response<ApiResponse<FieldListData>>

    @GET("fields/{fieldId}")
    suspend fun getFieldById(@Path("fieldId") fieldId: String): Response<ApiResponse<FieldData>>

    @PATCH("fields/{fieldId}")
    suspend fun updateField(
        @Path("fieldId") fieldId: String,
        @Body updates: Map<String, Any>
    ): Response<ApiResponse<FieldData>>

    @DELETE("fields/{fieldId}")
    suspend fun deleteField(@Path("fieldId") fieldId: String): Response<ApiResponse<Unit>>
}
