package org.bataydar.evidence.data.remote.api

import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface FarmerApiService {

    @GET("users/me")
    suspend fun getProfile(): Response<ApiResponse<ProfileData>>

    @PATCH("users/me")
    suspend fun updateProfile(@Body profile: Map<String, String>): Response<ApiResponse<ProfileData>>

    @DELETE("users/me")
    suspend fun requestAccountDeletion(): Response<ApiResponse<Unit>>
}
