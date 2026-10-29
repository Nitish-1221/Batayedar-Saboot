package org.bataydar.evidence.data.remote.auth

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.bataydar.evidence.BuildConfig
import android.content.Context
import org.bataydar.evidence.data.remote.ServerConfig
import org.bataydar.evidence.data.remote.dto.ApiResponse
import org.bataydar.evidence.data.remote.dto.RefreshRequest
import org.bataydar.evidence.data.remote.dto.TokenRefreshData

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val gson: Gson,
    private val context: Context? = null
) : Authenticator {

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loop if refresh itself returns 401
        if (response.request.url.encodedPath.contains("auth/refresh") ||
            response.request.url.encodedPath.contains("auth/login") ||
            response.request.url.encodedPath.contains("auth/register")) {
            tokenManager.clear()
            return null
        }

        // Retry limit: do not retry more than twice
        if (responseCount(response) >= 2) {
            return null
        }

        // 1. Try refreshing Firebase ID token first if user is logged into Firebase
        try {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                val tokenResult = Tasks.await(firebaseUser.getIdToken(true))
                val freshToken = tokenResult?.token
                if (!freshToken.isNullOrBlank()) {
                    tokenManager.saveTokens(
                        accessToken = freshToken,
                        refreshToken = tokenManager.getRefreshToken() ?: "",
                        userId = firebaseUser.uid
                    )
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $freshToken")
                        .build()
                }
            }
        } catch (_: Exception) {
            // Fall through if Firebase refresh is unavailable
        }

        // 2. Fallback to standard refresh token rotation
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        val refreshClient = OkHttpClient.Builder().build()
        val refreshPayload = gson.toJson(RefreshRequest(refreshToken))
        val baseUrl = context?.let { ServerConfig.getBaseUrl(it) } ?: BuildConfig.BASE_URL
        val refreshUrl = if (baseUrl.endsWith("/")) "${baseUrl}auth/refresh" else "$baseUrl/auth/refresh"
        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(refreshPayload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            if (refreshResponse.isSuccessful) {
                val bodyString = refreshResponse.body?.string()
                if (bodyString != null) {
                    val type = object : com.google.gson.reflect.TypeToken<ApiResponse<TokenRefreshData>>() {}.type
                    val apiResp: ApiResponse<TokenRefreshData> = gson.fromJson(bodyString, type)
                    if (apiResp.success && apiResp.data != null) {
                        val newTokens = apiResp.data.tokens
                        tokenManager.saveTokens(
                            accessToken = newTokens.accessToken,
                            refreshToken = newTokens.refreshToken
                        )
                        return response.request.newBuilder()
                            .header("Authorization", "Bearer ${newTokens.accessToken}")
                            .build()
                    }
                }
            }
        } catch (_: Exception) {}

        tokenManager.clear()
        return null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
