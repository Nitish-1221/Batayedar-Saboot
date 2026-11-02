package org.bataydar.evidence.data.remote.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SyncApiService {

    @POST("sync/push")
    suspend fun pushSync(@Body request: SyncPushRequest): Response<ApiResponse<SyncPushData>>

    @GET("sync/pull")
    suspend fun pullSync(@Query("since") since: String? = null): Response<ApiResponse<SyncPullData>>

    @GET("sync/changes")
    suspend fun getChanges(@Query("since") since: String? = null): Response<ApiResponse<SyncPullData>>

    @GET("sync/restore")
    suspend fun restoreUserData(@Query("since") since: String? = null): Response<ApiResponse<SyncRestoreData>>

    @Multipart
    @POST("sync/upload")
    suspend fun uploadEvidenceDirect(
        @Part photo: MultipartBody.Part,
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody>
    ): Response<ApiResponse<EvidenceUploadResponse>>
}
