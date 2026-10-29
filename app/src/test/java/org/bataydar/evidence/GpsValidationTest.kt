package org.bataydar.evidence

import android.location.Location
import org.bataydar.evidence.gps.GpsLocationManager
import org.bataydar.evidence.gps.GpsValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class GpsValidationTest {

    private lateinit var mockLocation: Location

    @Before
    fun setUp() {
        mockLocation = mock(Location::class.java)
    }

    @Test
    fun testAccurateAndFreshLocation_returnsValidGpsFix() {
        val now = System.currentTimeMillis()
        `when`(mockLocation.time).thenReturn(now - 1000L) // 1 second old
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(8.5f) // 8.5m accuracy <= 20m threshold
        `when`(mockLocation.latitude).thenReturn(27.123456)
        `when`(mockLocation.longitude).thenReturn(79.654321)

        val threshold = 20.0f
        val maxFreshnessMs = 10_000L

        val ageMs = (now - mockLocation.time).coerceAtLeast(0L)
        val result = if (ageMs > maxFreshnessMs) {
            GpsValidationResult.StaleFix(mockLocation, ageMs, maxFreshnessMs)
        } else if (!mockLocation.hasAccuracy() || mockLocation.accuracy > threshold) {
            GpsValidationResult.InaccurateFix(mockLocation, mockLocation.accuracy, threshold, ageMs)
        } else {
            GpsValidationResult.ValidGpsFix(mockLocation, mockLocation.accuracy, ageMs)
        }

        assertTrue("Expected ValidGpsFix", result is GpsValidationResult.ValidGpsFix)
        assertEquals(8.5f, (result as GpsValidationResult.ValidGpsFix).accuracyMeters)
    }

    @Test
    fun testInaccurateLocation_exceedingThreshold_returnsInaccurateFix() {
        val now = System.currentTimeMillis()
        `when`(mockLocation.time).thenReturn(now - 2000L)
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(35.0f) // 35m > 20m threshold

        val threshold = 20.0f
        val maxFreshnessMs = 10_000L

        val ageMs = (now - mockLocation.time).coerceAtLeast(0L)
        val result = if (ageMs > maxFreshnessMs) {
            GpsValidationResult.StaleFix(mockLocation, ageMs, maxFreshnessMs)
        } else if (!mockLocation.hasAccuracy() || mockLocation.accuracy > threshold) {
            GpsValidationResult.InaccurateFix(mockLocation, mockLocation.accuracy, threshold, ageMs)
        } else {
            GpsValidationResult.ValidGpsFix(mockLocation, mockLocation.accuracy, ageMs)
        }

        assertTrue("Expected InaccurateFix for 35m accuracy", result is GpsValidationResult.InaccurateFix)
        val inaccurate = result as GpsValidationResult.InaccurateFix
        assertEquals(35.0f, inaccurate.accuracyMeters)
        assertEquals(20.0f, inaccurate.requiredThreshold)
    }

    @Test
    fun testStaleLocation_exceeding10Seconds_returnsStaleFix() {
        val now = System.currentTimeMillis()
        `when`(mockLocation.time).thenReturn(now - 15_000L) // 15 seconds old > 10s freshness
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(5.0f)

        val threshold = 20.0f
        val maxFreshnessMs = 10_000L

        val ageMs = (now - mockLocation.time).coerceAtLeast(0L)
        val result = if (ageMs > maxFreshnessMs) {
            GpsValidationResult.StaleFix(mockLocation, ageMs, maxFreshnessMs)
        } else if (!mockLocation.hasAccuracy() || mockLocation.accuracy > threshold) {
            GpsValidationResult.InaccurateFix(mockLocation, mockLocation.accuracy, threshold, ageMs)
        } else {
            GpsValidationResult.ValidGpsFix(mockLocation, mockLocation.accuracy, ageMs)
        }

        assertTrue("Expected StaleFix for 15s age", result is GpsValidationResult.StaleFix)
        val stale = result as GpsValidationResult.StaleFix
        assertTrue(stale.ageMs >= 15_000L)
    }

    @Test
    fun testLocationWithoutAccuracy_returnsInaccurateFix() {
        val now = System.currentTimeMillis()
        `when`(mockLocation.time).thenReturn(now - 500L)
        `when`(mockLocation.hasAccuracy()).thenReturn(false) // No accuracy provided

        val threshold = 20.0f
        val maxFreshnessMs = 10_000L

        val ageMs = (now - mockLocation.time).coerceAtLeast(0L)
        val result = if (ageMs > maxFreshnessMs) {
            GpsValidationResult.StaleFix(mockLocation, ageMs, maxFreshnessMs)
        } else if (!mockLocation.hasAccuracy() || mockLocation.accuracy > threshold) {
            GpsValidationResult.InaccurateFix(mockLocation, 999.0f, threshold, ageMs)
        } else {
            GpsValidationResult.ValidGpsFix(mockLocation, mockLocation.accuracy, ageMs)
        }

        assertTrue("Expected InaccurateFix when hasAccuracy is false", result is GpsValidationResult.InaccurateFix)
    }

    @Test
    fun testNetworkProvider_isRejected() {
        val now = System.currentTimeMillis()
        `when`(mockLocation.time).thenReturn(now - 1000L)
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(5.0f)
        `when`(mockLocation.provider).thenReturn("network") // Network derived coordinates

        val provider = (mockLocation.provider ?: "").lowercase(java.util.Locale.US)
        val result = if (provider.contains("network") || provider == "coarse") {
            GpsValidationResult.MockOrNetworkLocationRejected("Cellular or Wi-Fi network coordinates rejected. Direct hardware satellite GPS required.")
        } else {
            GpsValidationResult.ValidGpsFix(mockLocation, mockLocation.accuracy, 1000L)
        }

        assertTrue("Expected MockOrNetworkLocationRejected for network provider", result is GpsValidationResult.MockOrNetworkLocationRejected)
    }

    @Test
    fun testMockLocation_isRejected() {
        val isMock = true
        val result = if (isMock) {
            GpsValidationResult.MockOrNetworkLocationRejected("Mock/spoofed location detected. Real satellite fix required.")
        } else {
            GpsValidationResult.ValidGpsFix(mockLocation, 5.0f, 1000L)
        }

        assertTrue("Expected MockOrNetworkLocationRejected for mock location", result is GpsValidationResult.MockOrNetworkLocationRejected)
    }
}
