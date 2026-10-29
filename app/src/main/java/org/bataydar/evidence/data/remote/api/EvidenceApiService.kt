package org.bataydar.evidence.data.remote.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface EvidenceApiService {

    @POST("evidence/upload-url")
    suspend fun requestUploadUrl(@Body request: RequestUploadUrlRequest): Response<ApiResponse<UploadUrlData>>

    @POST("evidence/{evidenceId}/complete")
    suspend fun completeUpload(
        @Path("evidenceId") evidenceId: String,
        @Body request: CompleteUploadRequest
    ): Response<ApiResponse<EvidenceData>>

    @GET("evidence/{evidenceId}")
    suspend fun getEvidenceById(@Path("evidenceId") evidenceId: String): Response<ApiResponse<EvidenceData>>

    @DELETE("evidence/{evidenceId}")
    suspend fun deleteEvidence(@Path("evidenceId") evidenceId: String): Response<ApiResponse<Unit>>

    // Direct binary upload to presigned S3 URL or server upload target
    @PUT
    suspend fun uploadBinaryDirect(
        @Url uploadUrl: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST
    suspend fun uploadBinaryServerFallback(
        @Url uploadUrl: String,
        @Header("Content-Type") contentType: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}
