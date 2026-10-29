package org.bataydar.evidence.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.*

class LandlordRepository(
    private val apiClient: ApiClient,
    private val tokenManager: TokenManager
) {

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn() && tokenManager.isLandlord()

    fun getStoredLandlordName(): String? = tokenManager.getUserName()

    fun getStoredLandlordPhone(): String? = tokenManager.getUserPhone()

    suspend fun getDashboard(): Result<LandlordDashboardDto> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.landlordApi.getDashboard()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data ?: LandlordDashboardDto())
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to load dashboard")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRequests(status: String? = null): Result<List<LandlordRequestItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.landlordApi.getRequests(status)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data?.requests ?: emptyList())
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to load requests")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRequestDetails(confirmationId: String): Result<LandlordRequestDetailResponseDto> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.landlordApi.getRequestDetails(confirmationId)
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(Exception("Confirmation details not found"))
                }
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to load request details")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToRequest(
        confirmationId: String,
        status: String,
        notes: String = "",
        witnessName: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = RespondLandlordRequest(status = status, notes = notes, witnessName = witnessName)
            val response = apiClient.landlordApi.respondToRequest(confirmationId, req)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to record response")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(): Result<List<LandlordHistoryItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.landlordApi.getHistory()
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data?.history ?: emptyList())
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to load history")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProfile(): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.landlordApi.getProfile()
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data?.user
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Profile data unavailable"))
                }
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to fetch profile")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <T> extractErrorMessage(response: retrofit2.Response<T>, fallback: String): String {
        try {
            val errorJson = response.errorBody()?.string()
            if (!errorJson.isNullOrBlank()) {
                val jsonObject = JsonParser.parseString(errorJson).asJsonObject
                if (jsonObject.has("error") && jsonObject.get("error").isJsonObject) {
                    val errorObj = jsonObject.getAsJsonObject("error")
                    if (errorObj.has("message") && !errorObj.get("message").isJsonNull) {
                        return errorObj.get("message").asString
                    }
                }
                if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull) {
                    return jsonObject.get("message").asString
                }
            }
        } catch (_: Exception) {}
        return "$fallback (${response.code()})"
    }
}
