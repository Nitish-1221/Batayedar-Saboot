package org.bataydar.evidence.gps

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    var accuracyThresholdMeters: Float = 20.0f
    var maxFreshnessMs: Long = 10_000L // 10 seconds freshness limit

    private val _gpsState = MutableStateFlow<GpsValidationResult>(GpsValidationResult.Idle)
    val gpsState: StateFlow<GpsValidationResult> = _gpsState.asStateFlow()

    private var lastObservedLocation: Location? = null
    private var isTracking = false

    private val nativeGpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            checkStatusAndStart()
        }
        override fun onProviderDisabled(provider: String) {
            _gpsState.value = GpsValidationResult.LocationServicesDisabled
        }
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleNewLocation(it) }
        }
    }

    fun isLocationPermissionGranted(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted
    }

    fun isGpsProviderEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startGpsTracking() {
        if (!isLocationPermissionGranted()) {
            _gpsState.value = GpsValidationResult.PermissionMissing
            return
        }

        if (!isGpsProviderEnabled()) {
            _gpsState.value = GpsValidationResult.LocationServicesDisabled
            return
        }

        _gpsState.value = GpsValidationResult.SearchingForSatellites
        isTracking = true

        try {
            // 1. Native hardware GPS listener (works 100% offline with satellites directly)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0.5f,
                    nativeGpsListener,
                    Looper.getMainLooper()
                )
            }

            // 2. Fused location client for enhanced satellite processing
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateDelayMillis(1500L)
                .build()

            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
            _gpsState.value = GpsValidationResult.SearchingForSatellites
        }
    }

    fun stopGpsTracking() {
        isTracking = false
        try {
            locationManager.removeUpdates(nativeGpsListener)
            fusedClient.removeLocationUpdates(fusedCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkStatusAndStart() {
        if (!isLocationPermissionGranted()) {
            _gpsState.value = GpsValidationResult.PermissionMissing
            return
        }
        if (!isGpsProviderEnabled()) {
            _gpsState.value = GpsValidationResult.LocationServicesDisabled
            return
        }
        startGpsTracking()
    }

    private fun handleNewLocation(location: Location) {
        lastObservedLocation = location
        _gpsState.value = evaluateLocation(location)
    }

    fun evaluateLocation(location: Location): GpsValidationResult {
        // 0. Mock location rejection
        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        } else {
            false
        }
        if (isMock) {
            return GpsValidationResult.MockOrNetworkLocationRejected(
                "Mock/spoofed location detected. Real satellite fix required."
            )
        }

        // 1. Direct hardware satellite GPS provider check (reject cell/Wi-Fi network provider)
        val provider = (location.provider ?: "").lowercase(java.util.Locale.US)
        if (provider.contains("network") || provider == "coarse") {
            return GpsValidationResult.MockOrNetworkLocationRejected(
                "Cellular or Wi-Fi network coordinates rejected. Direct hardware satellite GPS required."
            )
        }

        // 2. Freshness check: prefer monotonic CPU elapsedRealtimeNanos to prevent clock manipulation
        val ageMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - location.time).coerceAtLeast(0L)
        }

        if (ageMs > maxFreshnessMs) {
            return GpsValidationResult.StaleFix(
                location = location,
                ageMs = ageMs,
                maxAllowedAgeMs = maxFreshnessMs
            )
        }

        // 3. Accuracy check: must have accuracy and <= threshold (e.g. 20m)
        if (!location.hasAccuracy() || location.accuracy > accuracyThresholdMeters) {
            return GpsValidationResult.InaccurateFix(
                location = location,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 999.0f,
                requiredThreshold = accuracyThresholdMeters,
                ageMs = ageMs
            )
        }

        // 4. Valid GPS fix obtained!
        return GpsValidationResult.ValidGpsFix(
            location = location,
            accuracyMeters = location.accuracy,
            ageMs = ageMs
        )
    }

    /**
     * Strict check immediately prior to capturing evidence photograph.
     * Rejects any stale or inaccurate fix.
     */
    fun validateForCapture(): GpsValidationResult {
        val loc = lastObservedLocation ?: return GpsValidationResult.SearchingForSatellites
        return evaluateLocation(loc)
    }

    fun getLastValidLocation(): Location? {
        val loc = lastObservedLocation ?: return null
        return if (evaluateLocation(loc) is GpsValidationResult.ValidGpsFix) loc else null
    }
}
