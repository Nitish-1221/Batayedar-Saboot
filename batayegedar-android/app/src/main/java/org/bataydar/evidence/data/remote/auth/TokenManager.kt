package org.bataydar.evidence.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "batayedar_secure_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for environments / unit tests where Android KeyStore is unavailable
        context.getSharedPreferences("batayedar_tokens_fallback", Context.MODE_PRIVATE)
    }

    fun saveTokens(accessToken: String, refreshToken: String, userId: String? = null) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply {
                if (userId != null) putString(KEY_USER_ID, userId)
            }
            .apply()
    }

    fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        role: String = ROLE_BATAYDAR,
        fullName: String? = null,
        phone: String? = null,
        email: String? = null
    ) {
        val editor = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_ROLE, role)

        if (fullName != null) editor.putString(KEY_USER_NAME, fullName)
        if (phone != null) editor.putString(KEY_USER_PHONE, phone)
        if (email != null) editor.putString(KEY_USER_EMAIL, email)

        editor.apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)

    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    fun getUserPhone(): String? = prefs.getString(KEY_USER_PHONE, null)

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()

    fun isBataydar(): Boolean = getUserRole() == ROLE_BATAYDAR || (isLoggedIn() && getUserRole() == null)

    fun isLandlord(): Boolean = getUserRole() == ROLE_LANDLORD

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val ROLE_BATAYDAR = "BATAYDAR"
        const val ROLE_LANDLORD = "LANDLORD"

        private const val KEY_ACCESS_TOKEN = "jwt_access_token"
        private const val KEY_REFRESH_TOKEN = "jwt_refresh_token"
        private const val KEY_USER_ID = "auth_user_id"
        private const val KEY_USER_ROLE = "auth_user_role"
        private const val KEY_USER_NAME = "auth_user_name"
        private const val KEY_USER_PHONE = "auth_user_phone"
        private const val KEY_USER_EMAIL = "auth_user_email"

        @Volatile
        private var instance: TokenManager? = null

        fun getInstance(context: Context): TokenManager =
            instance ?: synchronized(this) {
                instance ?: TokenManager(context.applicationContext).also { instance = it }
            }
    }
}
