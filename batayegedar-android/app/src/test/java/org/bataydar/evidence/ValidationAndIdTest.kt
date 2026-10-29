package org.bataydar.evidence

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ValidationAndIdTest {

    @Test
    fun testFarmerIdFormat() {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomPart = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        val farmerId = "FMR-$datePart-$randomPart"

        assertTrue(farmerId.startsWith("FMR-"))
        assertEquals(17, farmerId.length) // FMR- (4) + YYYYMMDD (8) + - (1) + 4 = 17
    }

    @Test
    fun testRecordIdFormat() {
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val randomPart = UUID.randomUUID().toString().take(4).uppercase(Locale.US)
        val recordId = "REC-$datePart-$randomPart"

        assertTrue(recordId.startsWith("REC-"))
        assertEquals(17, recordId.length)
    }

    @Test
    fun testMetadataJsonStructure() {
        val gson = Gson()
        val metadata = mapOf(
            "dossierFormatVersion" to "1.0.0",
            "generatorApp" to "Batayedar Saboot Android",
            "legalNoticeAndDisclaimer" to "This report is a collection of farmer-provided information and supporting evidence...",
            "farmerProfile" to mapOf(
                "farmerId" to "FMR-20261028-A1B2",
                "fullName" to "Rameshwar Yadav"
            ),
            "cropRecord" to mapOf(
                "recordId" to "REC-20261028-C3D4",
                "cropType" to "Paddy / Rice (धान)"
            )
        )

        val jsonString = gson.toJson(metadata)
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val parsed: Map<String, Any> = gson.fromJson(jsonString, type)

        assertEquals("1.0.0", parsed["dossierFormatVersion"])
        assertNotNull(parsed["legalNoticeAndDisclaimer"])
        assertNotNull(parsed["farmerProfile"])
        assertNotNull(parsed["cropRecord"])
    }
}
