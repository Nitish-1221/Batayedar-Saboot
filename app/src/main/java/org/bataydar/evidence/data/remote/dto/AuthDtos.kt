package org.bataydar.evidence.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("idToken") val idToken: String? = null,
    @SerializedName("role") val role: String = "BATAYDAR",
    @SerializedName("village") val village: String = "",
    @SerializedName("district") val district: String = "",
    @SerializedName("state") val state: String = "",
    @SerializedName("preferredLanguage") val preferredLanguage: String = "hi"
)

data class LoginRequest(
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("idToken") val idToken: String? = null,
    @SerializedName("role") val role: String? = null
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class ChangePasswordRequest(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String
)

data class AuthData(
    @SerializedName("user") val user: UserDto,
    @SerializedName("tokens") val tokens: TokenDto
)

data class TokenDto(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("tokenType") val tokenType: String = "Bearer",
    @SerializedName("expiresIn") val expiresIn: String? = null
)

data class TokenRefreshData(
    @SerializedName("tokens") val tokens: TokenDto
)

data class UserDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String = "BATAYDAR",
    @SerializedName("village") val village: String = "",
    @SerializedName("district") val district: String = "",
    @SerializedName("state") val state: String = "",
    @SerializedName("preferredLanguage") val preferredLanguage: String = "hi",
    @SerializedName("accountStatus") val accountStatus: String = "ACTIVE"
)

data class ProfileData(
    @SerializedName("user") val user: UserDto
)
