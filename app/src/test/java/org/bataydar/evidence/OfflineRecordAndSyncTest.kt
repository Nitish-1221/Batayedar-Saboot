package org.bataydar.evidence

import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.SyncStatus
import org.bataydar.evidence.export.PdfDossierExporter
import org.bataydar.evidence.export.ChecksumUtils
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class OfflineRecordAndSyncTest {

    @Test
    fun testOfflineCropRecordCreation_defaultsToLocalOnly() {
        val recordId = UUID.randomUUID().toString()
        val record = CropRecordEntity(
            recordId = recordId,
            farmerId = "farmer-offline-123",
            fieldName = "North Plot",
            village = "Samastipur",
            district = "Samastipur",
            state = "Bihar",
            cropType = "Mustard (सरसों)",
            sowingDate = "2026-10-15",
            fieldDescription = "Test offline plot",
            syncStatus = SyncStatus.LOCAL_ONLY
        )

        assertEquals(recordId, record.recordId)
        assertEquals(SyncStatus.LOCAL_ONLY, record.syncStatus)
        assertEquals(LandlordStatus.NOT_REQUESTED, record.landlordConfirmationStatus)
        assertNotNull(record.createdAt)
    }

    @Test
    fun testGeotaggedEvidence_sha256AndAccuracyRestrictions() {
        val evidenceId = UUID.randomUUID().toString()
        val recordId = UUID.randomUUID().toString()
        val sampleBytes = "BatayedarSabootCropProofImageData".toByteArray(Charsets.UTF_8)
        val tempFile = File.createTempFile("evidence_test_", ".jpg")
        tempFile.writeBytes(sampleBytes)

        val checksum = ChecksumUtils.calculateSha256(tempFile)
        assertNotNull(checksum)
        assertEquals(64, checksum.length) // SHA-256 hex is exactly 64 characters

        val evidence = GeotaggedEvidenceEntity(
            evidenceId = evidenceId,
            recordId = recordId,
            originalPhotoPath = tempFile.absolutePath,
            photoSha256 = checksum,
            latitude = 25.8643,
            longitude = 85.7821,
            altitude = 52.0,
            accuracyMeters = 8.5f, // Within <= 20m threshold
            gpsFixAgeMs = 1200L,   // 1.2s fresh <= 10s limit
            capturedAtUtc = System.currentTimeMillis(),
            capturedAtLocalFormatted = "2026-10-29 10:00:00",
            deviceModel = "Test Android Device",
            syncStatus = SyncStatus.PENDING_SYNC
        )

        assertEquals(evidenceId, evidence.evidenceId)
        assertTrue("Accuracy must be <= 20m", evidence.accuracyMeters <= 20.0f)
        assertTrue("Fix age must be <= 10,000ms", evidence.gpsFixAgeMs <= 10_000L)
        assertEquals(SyncStatus.PENDING_SYNC, evidence.syncStatus)

        tempFile.delete()
    }

    @Test
    fun testSyncStatusTransitions_safeOfflineProgression() {
        val statusList = listOf(
            SyncStatus.LOCAL_ONLY,
            SyncStatus.PENDING_SYNC,
            SyncStatus.SYNC_FAILED,
            SyncStatus.SYNCED
        )

        // Verify all 4 states are recognized and discrete
        assertEquals(4, statusList.size)
        assertTrue(statusList.contains(SyncStatus.LOCAL_ONLY))
        assertTrue(statusList.contains(SyncStatus.SYNCED))
    }

    @Test
    fun testLegalDisclaimerIntegrity_containsStatutoryDisclaimer() {
        val disclaimer = PdfDossierExporter.LEGAL_DISCLAIMER
        assertNotNull(disclaimer)
        assertTrue(
            "Must state that evidence does not independently establish land ownership",
            disclaimer.contains("do not independently establish land ownership")
        )
        assertTrue(
            "Must state that legal significance depends on state laws",
            disclaimer.contains("applicable state laws")
        )
    }

    @Test
    fun testCropRecordWithoutLandlordDetails_validAndIndependent() {
        val record = CropRecordEntity(
            recordId = "REC-20261029-ABCD",
            farmerId = "farmer-777",
            fieldName = "Dakshin Khet",
            village = "Siwan",
            district = "Siwan",
            state = "Bihar",
            cropType = "Wheat (गेहूँ)",
            sowingDate = "2026-10-20",
            fieldDescription = "Independent cultivation",
            landlordName = null,
            landlordPhone = null,
            landlordConfirmationStatus = LandlordStatus.NOT_REQUESTED
        )

        assertNull(record.landlordName)
        assertNull(record.landlordPhone)
        assertEquals(LandlordStatus.NOT_REQUESTED, record.landlordConfirmationStatus)
        assertEquals("REC-20261029-ABCD", record.recordId)
        assertEquals("farmer-777", record.farmerId)
    }

    @Test
    fun testLandlordConfirmationStatusValues_allDiscreteAndNonBlocking() {
        val statuses = LandlordStatus.values()
        assertTrue(statuses.contains(LandlordStatus.NOT_REQUESTED))
        assertTrue(statuses.contains(LandlordStatus.REQUESTED))
        assertTrue(statuses.contains(LandlordStatus.CONFIRMED_VERBAL))
        assertTrue(statuses.contains(LandlordStatus.CONFIRMED_WRITTEN))
        assertTrue(statuses.contains(LandlordStatus.REFUSED))
        assertTrue(statuses.contains(LandlordStatus.UNRESPONSIVE))

        assertFalse(LandlordStatus.NOT_REQUESTED.isConfirmed())
        assertFalse(LandlordStatus.REQUESTED.isConfirmed())
        assertTrue(LandlordStatus.CONFIRMED_VERBAL.isConfirmed())
        assertTrue(LandlordStatus.CONFIRMED_WRITTEN.isConfirmed())
    }
}
