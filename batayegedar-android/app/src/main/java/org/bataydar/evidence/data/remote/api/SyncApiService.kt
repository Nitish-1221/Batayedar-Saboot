package org.bataydar.evidence.data.remote.api

import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface SyncApiService {

    @POST("sync/push")
    suspend fun pushSync(@Body request: SyncPushRequest): Response<ApiResponse<SyncPushData>>

    @GET("sync/pull")
    suspend fun pullSync(@Query("since") since: String? = null): Response<ApiResponse<SyncPullData>>
}
