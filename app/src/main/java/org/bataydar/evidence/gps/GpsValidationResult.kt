package org.bataydar.evidence.gps

import android.location.Location

sealed class GpsValidationResult {
    object Idle : GpsValidationResult()
    
    object LocationServicesDisabled : GpsValidationResult()
    
    object PermissionMissing : GpsValidationResult()
    
    object SearchingForSatellites : GpsValidationResult()
    
    data class InaccurateFix(
        val location: Location,
        val accuracyMeters: Float,
        val requiredThreshold: Float,
        val ageMs: Long
    ) : GpsValidationResult()

    data class StaleFix(
        val location: Location,
        val ageMs: Long,
        val maxAllowedAgeMs: Long
    ) : GpsValidationResult()

    data class MockOrNetworkLocationRejected(
        val reason: String
    ) : GpsValidationResult()

    data class ValidGpsFix(
        val location: Location,
        val accuracyMeters: Float,
        val ageMs: Long
    ) : GpsValidationResult()
}
