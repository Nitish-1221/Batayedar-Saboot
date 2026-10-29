package org.bataydar.evidence.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.bataydar.evidence.data.local.FarmerProfileDao
import org.bataydar.evidence.data.model.FarmerProfileEntity
import android.content.Context
import com.google.gson.JsonParser
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.ServerConfig
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.*

class RoleMismatchException(val actualRole: String, message: String) : Exception(message)

class AuthRepository(
    private val apiClient: ApiClient,
    private val tokenManager: TokenManager,
    private val profileDao: FarmerProfileDao,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val context: Context? = null
) {

    fun isLoggedIn(): Boolean {
        // Authenticated if Firebase current user exists and local token session is valid,
        // or local token session exists for offline support
        return (firebaseAuth.currentUser != null && tokenManager.isLoggedIn()) ||
                tokenManager.isLoggedIn()
    }

    fun getCurrentFirebaseUser(): FirebaseUser? = firebaseAuth.currentUser

    fun getStoredUserId(): String? = tokenManager.getUserId() ?: firebaseAuth.currentUser?.uid

    fun getStoredUserRole(): String? = tokenManager.getUserRole()

    /**
     * Firebase Email/Password Registration.
     * Creates Firebase Auth user, retrieves ID token, syncs authoritative profile to backend,
     * and sets up session in TokenManager and Room DB.
     */
    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phone: String,
        role: String = TokenManager.ROLE_BATAYDAR,
        village: String = "",
        district: String = "",
        state: String = "",
        preferredLanguage: String = "hi"
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        var createdUser: FirebaseUser? = null
        try {
            // Step 1: Create user with Firebase Authentication
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return@withContext Result.failure(Exception("Firebase user creation failed"))
            createdUser = firebaseUser

            // Step 2: Obtain Firebase ID token
            val idToken = firebaseUser.getIdToken(false).await()?.token
                ?: return@withContext Result.failure(Exception("Failed to obtain authentication token"))

            // Step 3: Register authoritative profile with server-verified role on backend
            val req = RegisterRequest(
                fullName = fullName,
                phone = phone,
                email = email,
                password = password,
                idToken = idToken,
                role = role,
                village = village,
                district = district,
                state = state,
                preferredLanguage = preferredLanguage
            )

            val response = apiClient.authApi.register(req)
            if (response.isSuccessful && response.body()?.success == true) {
                val authData = response.body()!!.data!!
                val user = authData.user

                tokenManager.saveSession(
                    accessToken = idToken,
                    refreshToken = authData.tokens.refreshToken,
                    userId = user.userId,
                    role = user.role,
                    fullName = user.fullName,
                    phone = user.phone,
                    email = user.email ?: email
                )

                // Sync with local Room database if Bataydar
                if (user.role == TokenManager.ROLE_BATAYDAR) {
                    val profile = FarmerProfileEntity(
                        farmerId = user.userId,
                        fullName = user.fullName,
                        phoneNumber = user.phone,
                        village = user.village,
                        district = user.district,
                        state = user.state,
                        preferredLanguage = user.preferredLanguage,
                        createdAt = System.currentTimeMillis()
                    )
                    profileDao.insertOrUpdate(profile)
                }

                Result.success(user)
            } else {
                // Roll back newly created Firebase user if backend profile registration fails
                try {
                    createdUser.delete().await()
                } catch (_: Exception) {}

                val errorMsg = extractErrorMessage(response, "Registration failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            if (createdUser != null) {
                try {
                    createdUser.delete().await()
                } catch (_: Exception) {}
            }
            Result.failure(parseFirebaseException(e))
        }
    }

    /**
     * Firebase Email/Password Login.
     * Authenticates with Firebase, obtains ID token, verifies authoritative role with backend,
     * and strictly rejects mismatched roles before opening dashboard.
     */
    suspend fun login(
        email: String,
        password: String,
        expectedRole: String? = null
    ): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Sign in with Firebase Authentication
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user
                ?: return@withContext Result.failure(Exception("Authentication failed"))

            // Step 2: Retrieve fresh Firebase ID token
            val idToken = firebaseUser.getIdToken(true).await()?.token
                ?: return@withContext Result.failure(Exception("Failed to obtain ID token"))

            // Save token temporarily to allow authenticated backend call
            tokenManager.saveTokens(accessToken = idToken, refreshToken = "")

            // Step 3: Fetch authoritative profile and server-verified role
            val response = apiClient.authApi.getMe()
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()!!.data!!.user

                // Step 4: Strict server-verified role enforcement
                if (expectedRole != null && user.role != expectedRole) {
                    firebaseAuth.signOut()
                    tokenManager.clear()
                    val msg = if (user.role == TokenManager.ROLE_LANDLORD) {
                        "This account is registered as Landlord. Please use Landlord Login."
                    } else {
                        "This account is registered as Bataydar. Please use Bataydar Login."
                    }
                    return@withContext Result.failure(RoleMismatchException(user.role, msg))
                }

                tokenManager.saveSession(
                    accessToken = idToken,
                    refreshToken = "",
                    userId = user.userId,
                    role = user.role,
                    fullName = user.fullName,
                    phone = user.phone,
                    email = user.email ?: email
                )

                // Sync with local Room database if Bataydar
                if (user.role == TokenManager.ROLE_BATAYDAR) {
                    val profile = FarmerProfileEntity(
                        farmerId = user.userId,
                        fullName = user.fullName,
                        phoneNumber = user.phone,
                        village = user.village,
                        district = user.district,
                        state = user.state,
                        preferredLanguage = user.preferredLanguage,
                        createdAt = System.currentTimeMillis()
                    )
                    profileDao.insertOrUpdate(profile)
                }

                Result.success(user)
            } else {
                firebaseAuth.signOut()
                tokenManager.clear()
                val errorMsg = extractErrorMessage(response, "Account verification failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            tokenManager.clear()
            Result.failure(parseFirebaseException(e))
        }
    }

    /**
     * Send Password Reset Email via Firebase Authentication.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(parseFirebaseException(e))
        }
    }

    /**
     * Sign out user from Firebase and clear local session.
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = tokenManager.getRefreshToken()
            if (!refreshToken.isNullOrBlank()) {
                try {
                    apiClient.authApi.logout(RefreshRequest(refreshToken))
                } catch (_: Exception) {}
            }
            firebaseAuth.signOut()
            tokenManager.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            firebaseAuth.signOut()
            tokenManager.clear()
            Result.success(Unit)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword)
            val response = apiClient.authApi.changePassword(req)
            if (response.isSuccessful && response.body()?.success == true) {
                firebaseAuth.signOut()
                tokenManager.clear()
                Result.success(Unit)
            } else {
                val errorMsg = extractErrorMessage(response, "Failed to change password")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFirebaseException(e: Exception): Exception {
        val msg = e.message.orEmpty()
        if (msg.contains("API key not valid", ignoreCase = true) ||
            msg.contains("API_KEY_INVALID", ignoreCase = true)) {
            return Exception("Firebase configuration required: Please download your real google-services.json from Firebase Console for 'org.bataydar.evidence' and place it in the app/ folder.")
        }
        if (e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException ||
            msg.contains("failed to connect", ignoreCase = true) ||
            msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true)) {
            val url = context?.let { ServerConfig.getBaseUrl(it) } ?: "http://127.0.0.1:5000/api/v1/"
            return Exception("Backend server unreachable at $url.\n\nPlease ensure backend server is running (`npm start`) and if using USB, run:\nadb reverse tcp:5000 tcp:5000")
        }
        return when (e) {
            is RoleMismatchException -> e
            is FirebaseAuthInvalidUserException -> Exception("No account found with this email address.")
            is FirebaseAuthInvalidCredentialsException -> Exception("Invalid email or password.")
            is FirebaseAuthUserCollisionException -> Exception("An account already exists with this email address.")
            is FirebaseAuthWeakPasswordException -> Exception("Password is too weak. Please use at least 6 characters.")
            is FirebaseNetworkException -> Exception("Internet connection required for login. Please check your network connection.")
            else -> Exception(e.message ?: "Authentication error occurred.")
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
        if (response.code() == 409) {
            return "An account with this phone number or email already exists."
        }
        return "$fallback (${response.code()})"
    }
}
