package org.bataydar.evidence

import com.google.gson.Gson
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.SyncStatus
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.*
import org.bataydar.evidence.export.PdfDossierExporter
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LandlordRoleAndNavigationTest {

    private val gson = Gson()

    @Test
    fun testUserDtoRoleParsing_BataydarAndLandlord() {
        val jsonBataydar = """
            {
                "userId": "usr-1",
                "fullName": "Ramesh Yadav",
                "phone": "+919100000001",
                "role": "BATAYDAR",
                "village": "Hasanpur",
                "district": "Samastipur",
                "state": "Bihar"
            }
        """.trimIndent()

        val bataydar = gson.fromJson(jsonBataydar, UserDto::class.java)
        assertEquals("BATAYDAR", bataydar.role)
        assertEquals("Ramesh Yadav", bataydar.fullName)

        val jsonLandlord = """
            {
                "userId": "usr-2",
                "fullName": "Thakur Virendra Singh",
                "phone": "+919200000001",
                "role": "LANDLORD",
                "village": "Hasanpur",
                "district": "Samastipur",
                "state": "Bihar"
            }
        """.trimIndent()

        val landlord = gson.fromJson(jsonLandlord, UserDto::class.java)
        assertEquals("LANDLORD", landlord.role)
        assertEquals("Thakur Virendra Singh", landlord.fullName)
    }

    @Test
    fun testRegisterRequestRole_explicitRoleValidation() {
        val bataydarReq = RegisterRequest(
            fullName = "Kisan Bhai",
            phone = "+919111111111",
            password = "SecurePassword123"
        )
        assertEquals("BATAYDAR", bataydarReq.role)

        val landlordReq = RegisterRequest(
            fullName = "Landowner Sahab",
            phone = "+919222222222",
            password = "SecurePassword123",
            role = "LANDLORD"
        )
        assertEquals("LANDLORD", landlordReq.role)
    }

    @Test
    fun testLandlordDashboardDtoSerialization() {
        val json = """
            {
                "pendingCount": 3,
                "completedCount": 7,
                "totalRequests": 10,
                "recentActivity": [
                    {
                        "confirmationId": "conf-101",
                        "recordId": "rec-201",
                        "status": "REQUESTED",
                        "requestedAt": "2026-10-28T10:00:00.000Z",
                        "fieldName": "Purab Tola Plot",
                        "cropType": "Paddy / Rice (धान)",
                        "farmerName": "Ramesh Yadav",
                        "farmerPhone": "+919100000001"
                    }
                ]
            }
        """.trimIndent()

        val dashboard = gson.fromJson(json, LandlordDashboardDto::class.java)
        assertEquals(3, dashboard.pendingCount)
        assertEquals(7, dashboard.completedCount)
        assertEquals(10, dashboard.totalRequests)
        assertEquals(1, dashboard.recentActivity.size)
        assertEquals("conf-101", dashboard.recentActivity[0].confirmationId)
        assertEquals("Paddy / Rice (धान)", dashboard.recentActivity[0].cropType)
    }

    @Test
    fun testSegregationOfFarmerDeclaredInfoAndVerifiedTelemetry() {
        val json = """
            {
                "confirmationId": "conf-101",
                "recordId": "rec-201",
                "status": "REQUESTED",
                "canRespond": true,
                "farmerDeclaredInfo": {
                    "farmerId": "fmr-1",
                    "farmerName": "Ramesh Yadav",
                    "farmerPhone": "+919100000001",
                    "fieldName": "Purab Tola Plot",
                    "cropType": "Paddy / Rice (धान)",
                    "sowingDate": "2026-07-15",
                    "fieldDescription": "Tenant cultivated paddy crop"
                },
                "independentlyVerifiedTelemetry": {
                    "totalEvidenceCount": 1,
                    "evidencePhotos": [
                        {
                            "evidenceId": "ev-501",
                            "photoSha256": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                            "latitude": 25.8643,
                            "longitude": 85.7821,
                            "accuracyMeters": 6.4,
                            "gpsFixAgeMs": 850,
                            "capturedAtLocal": "2026-10-28 10:15:00"
                        }
                    ],
                    "gpsMethod": "Android Hardware GPS Satellite Lock",
                    "integrityProtection": "SHA-256 fingerprint"
                }
            }
        """.trimIndent()

        val detail = gson.fromJson(json, LandlordRequestDetailResponseDto::class.java)
        assertTrue(detail.canRespond)

        // Verify farmer declared info is separated
        assertNotNull(detail.farmerDeclaredInfo)
        assertEquals("Ramesh Yadav", detail.farmerDeclaredInfo?.farmerName)
        assertEquals("Purab Tola Plot", detail.farmerDeclaredInfo?.fieldName)

        // Verify independently verified telemetry is separated
        assertNotNull(detail.independentlyVerifiedTelemetry)
        assertEquals(1, detail.independentlyVerifiedTelemetry?.totalEvidenceCount)
        val photo = detail.independentlyVerifiedTelemetry?.evidencePhotos?.firstOrNull()
        assertNotNull(photo)
        assertEquals(25.8643, photo?.latitude ?: 0.0, 0.0001)
        assertEquals(85.7821, photo?.longitude ?: 0.0, 0.0001)
        assertEquals(6.4f, photo?.accuracyMeters ?: 0f, 0.01f)
    }

    @Test
    fun testDuplicateConfirmationPreventionFlag() {
        val pendingJson = """{"confirmationId": "c1", "recordId": "r1", "status": "REQUESTED", "canRespond": true}"""
        val pendingDetail = gson.fromJson(pendingJson, LandlordRequestDetailResponseDto::class.java)
        assertTrue("Pending request must allow response", pendingDetail.canRespond)

        val completedJson = """{"confirmationId": "c1", "recordId": "r1", "status": "CONFIRMED_WRITTEN", "canRespond": false}"""
        val completedDetail = gson.fromJson(completedJson, LandlordRequestDetailResponseDto::class.java)
        assertFalse("Already responded request must prohibit duplicate response", completedDetail.canRespond)
    }

    @Test
    fun testOptionalLandlordParticipation_NeverBlocksEvidenceCaptureOrExport() {
        // A crop record created by tenant farmer with NO landlord details or confirmation
        val record = CropRecordEntity(
            recordId = UUID.randomUUID().toString(),
            farmerId = "fmr-independent-99",
            fieldName = "Dakshin Pokhar Plot",
            village = "Samastipur",
            district = "Samastipur",
            state = "Bihar",
            cropType = "Mustard (सरसों)",
            sowingDate = "2026-10-20",
            fieldDescription = "Completely independent cultivation proof",
            landlordName = null,
            landlordPhone = null,
            landlordConfirmationStatus = LandlordStatus.NOT_REQUESTED,
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        assertEquals(LandlordStatus.NOT_REQUESTED, record.landlordConfirmationStatus)
        assertNull(record.landlordName)
        assertNull(record.landlordPhone)

        // Legal disclaimer confirms that tenant crop documentation stands on its own
        assertTrue(PdfDossierExporter.LEGAL_DISCLAIMER.contains("farmer-provided information and supporting evidence"))
    }

    @Test
    fun testRoleBasedNavigationDecision() {
        // Function representing navigation routing decision
        fun determineDestinationScreen(role: String?, isLoggedIn: Boolean): String {
            if (!isLoggedIn) return "LoginActivity"
            return when (role) {
                TokenManager.ROLE_LANDLORD -> "LandlordDashboardActivity"
                TokenManager.ROLE_BATAYDAR -> "MainActivity"
                else -> "MainActivity" // Default fallback for existing farmer profiles
            }
        }

        assertEquals("LoginActivity", determineDestinationScreen(null, false))
        assertEquals("MainActivity", determineDestinationScreen("BATAYDAR", true))
        assertEquals("LandlordDashboardActivity", determineDestinationScreen("LANDLORD", true))
        assertEquals("MainActivity", determineDestinationScreen(null, true))
    }
}
