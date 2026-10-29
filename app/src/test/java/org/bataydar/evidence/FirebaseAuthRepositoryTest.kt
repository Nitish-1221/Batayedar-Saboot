package org.bataydar.evidence

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.bataydar.evidence.data.local.FarmerProfileDao
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.api.AuthApiService
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.*
import org.bataydar.evidence.data.repository.AuthRepository
import org.bataydar.evidence.data.repository.RoleMismatchException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import retrofit2.Response

class FirebaseAuthRepositoryTest {

    private val gson = Gson()
    private lateinit var apiClient: ApiClient
    private lateinit var authApi: AuthApiService
    private lateinit var tokenManager: TokenManager
    private lateinit var profileDao: FarmerProfileDao
    private lateinit var firebaseAuth: FirebaseAuth

    @Before
    fun setUp() {
        apiClient = mock(ApiClient::class.java)
        authApi = mock(AuthApiService::class.java)
        tokenManager = mock(TokenManager::class.java)
        profileDao = mock(FarmerProfileDao::class.java)
        firebaseAuth = mock(FirebaseAuth::class.java)

        `when`(apiClient.authApi).thenReturn(authApi)
    }

    @Test
    fun testLoginRequestWithFirebaseIdToken() {
        val req = LoginRequest(
            email = "farmer.ramesh@batayedar.org",
            idToken = "firebase-test-id-token-12345",
            role = "BATAYDAR"
        )

        val json = gson.toJson(req)
        assertTrue(json.contains("\"email\":\"farmer.ramesh@batayedar.org\""))
        assertTrue(json.contains("\"idToken\":\"firebase-test-id-token-12345\""))
        assertTrue(json.contains("\"role\":\"BATAYDAR\""))

        val deserialized = gson.fromJson(json, LoginRequest::class.java)
        assertEquals("farmer.ramesh@batayedar.org", deserialized.email)
        assertEquals("firebase-test-id-token-12345", deserialized.idToken)
        assertEquals("BATAYDAR", deserialized.role)
    }

    @Test
    fun testRegisterRequestWithFirebaseIdToken() {
        val req = RegisterRequest(
            fullName = "Ramesh Yadav",
            phone = "+919810010001",
            email = "farmer.ramesh@batayedar.org",
            idToken = "firebase-token-sample",
            role = "BATAYDAR",
            village = "Hasanpur",
            district = "Samastipur",
            state = "Bihar"
        )

        val json = gson.toJson(req)
        assertTrue(json.contains("\"fullName\":\"Ramesh Yadav\""))
        assertTrue(json.contains("\"phone\":\"+919810010001\""))
        assertTrue(json.contains("\"email\":\"farmer.ramesh@batayedar.org\""))
        assertTrue(json.contains("\"idToken\":\"firebase-token-sample\""))
        assertTrue(json.contains("\"role\":\"BATAYDAR\""))

        val deserialized = gson.fromJson(json, RegisterRequest::class.java)
        assertEquals("Ramesh Yadav", deserialized.fullName)
        assertEquals("farmer.ramesh@batayedar.org", deserialized.email)
        assertEquals("firebase-token-sample", deserialized.idToken)
    }

    @Test
    fun testProfileDataDeserializationFromGetMe() {
        val json = """
            {
                "user": {
                    "userId": "firebase-uid-001",
                    "fullName": "Thakur Virendra Singh",
                    "phone": "+919820020002",
                    "email": "landlord.singh@batayedar.org",
                    "role": "LANDLORD",
                    "village": "Hasanpur",
                    "district": "Samastipur",
                    "state": "Bihar",
                    "preferredLanguage": "hi",
                    "accountStatus": "ACTIVE"
                }
            }
        """.trimIndent()

        val profileData = gson.fromJson(json, ProfileData::class.java)
        assertNotNull(profileData.user)
        assertEquals("firebase-uid-001", profileData.user.userId)
        assertEquals("LANDLORD", profileData.user.role)
        assertEquals("landlord.singh@batayedar.org", profileData.user.email)
    }

    @Test
    fun testRoleMismatchExceptionAttributes() {
        val exception = RoleMismatchException("LANDLORD", "This account is registered as Landlord.")
        assertEquals("LANDLORD", exception.actualRole)
        assertEquals("This account is registered as Landlord.", exception.message)
    }

    @Test
    fun testIsLoggedInState_FirebaseUserPresentAndTokenValid() {
        val mockFirebaseUser = mock(FirebaseUser::class.java)
        `when`(firebaseAuth.currentUser).thenReturn(mockFirebaseUser)
        `when`(tokenManager.isLoggedIn()).thenReturn(true)

        val repository = AuthRepository(apiClient, tokenManager, profileDao, firebaseAuth)
        assertTrue(repository.isLoggedIn())
        assertEquals(mockFirebaseUser, repository.getCurrentFirebaseUser())
    }

    @Test
    fun testIsLoggedInState_OfflineWithValidTokenSession() {
        `when`(firebaseAuth.currentUser).thenReturn(null)
        `when`(tokenManager.isLoggedIn()).thenReturn(true)

        val repository = AuthRepository(apiClient, tokenManager, profileDao, firebaseAuth)
        // In offline mode with persisted local token, farmer can still access local records
        assertTrue(repository.isLoggedIn())
    }

    @Test
    fun testIsLoggedInState_LoggedOut() {
        `when`(firebaseAuth.currentUser).thenReturn(null)
        `when`(tokenManager.isLoggedIn()).thenReturn(false)

        val repository = AuthRepository(apiClient, tokenManager, profileDao, firebaseAuth)
        assertFalse(repository.isLoggedIn())
    }
}

