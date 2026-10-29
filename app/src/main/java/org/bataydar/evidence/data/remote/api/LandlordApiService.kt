package org.bataydar.evidence.data.remote.api

import org.bataydar.evidence.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface LandlordApiService {

    @GET("landlord/dashboard")
    suspend fun getDashboard(): Response<ApiResponse<LandlordDashboardDto>>

    @GET("landlord/requests")
    suspend fun getRequests(
        @Query("status") status: String? = null
    ): Response<ApiResponse<LandlordRequestsResponseDto>>

    @GET("landlord/requests/{confirmationId}")
    suspend fun getRequestDetails(
        @Path("confirmationId") confirmationId: String
    ): Response<ApiResponse<LandlordRequestDetailResponseDto>>

    @POST("landlord/requests/{confirmationId}/respond")
    suspend fun respondToRequest(
        @Path("confirmationId") confirmationId: String,
        @Body body: RespondLandlordRequest
    ): Response<ApiResponse<Any>>

    @GET("landlord/history")
    suspend fun getHistory(): Response<ApiResponse<LandlordHistoryResponseDto>>

    @GET("users/me")
    suspend fun getProfile(): Response<ApiResponse<ProfileData>>
}
