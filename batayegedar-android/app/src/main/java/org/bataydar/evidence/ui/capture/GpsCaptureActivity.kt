package org.bataydar.evidence.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.databinding.ActivityGpsCaptureBinding
import org.bataydar.evidence.gps.GpsLocationManager
import org.bataydar.evidence.gps.GpsValidationResult
import org.bataydar.evidence.util.FileUtils
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GpsCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGpsCaptureBinding
    private lateinit var gpsLocationManager: GpsLocationManager
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }

    private var recordId: String = ""
    private var currentRecord: RecordWithEvidence? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var verifiedLocation: Location? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (cameraGranted && fineLocationGranted) {
            startCamera()
            gpsLocationManager.startGpsTracking()
        } else {
            Toast.makeText(
                this,
                "Both Camera and High-Accuracy GPS permissions are mandatory to capture verifiable crop evidence.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGpsCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordId = intent.getStringExtra("RECORD_ID") ?: ""
        if (recordId.isEmpty()) {
            Toast.makeText(this, "Record ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        gpsLocationManager = GpsLocationManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        loadRecordDetails()
        checkPermissionsAndStart()
        observeGpsState()
    }

    override fun onResume() {
        super.onResume()
        if (gpsLocationManager.isLocationPermissionGranted()) {
            gpsLocationManager.checkStatusAndStart()
        }
    }

    override fun onPause() {
        super.onPause()
        gpsLocationManager.stopGpsTracking()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnOpenGpsSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }

        binding.btnRetryGps.setOnClickListener {
            gpsLocationManager.startGpsTracking()
        }

        binding.btnCaptureShutter.setOnClickListener {
            attemptCaptureWithRevalidation()
        }
    }

    private fun loadRecordDetails() {
        lifecycleScope.launch {
            val record = evidenceRepository.getRecordWithEvidence(recordId)
            currentRecord = record
            if (record != null) {
                binding.tvRecordInfoPill.text = "${record.record.cropType} • ${record.record.fieldName}"
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasFineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasFineLoc) {
            startCamera()
            gpsLocationManager.startGpsTracking()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun observeGpsState() {
        lifecycleScope.launch {
            gpsLocationManager.gpsState.collectLatest { state ->
                when (state) {
                    is GpsValidationResult.Idle -> {
                        updateGpsUi(
                            statusText = getString(R.string.gps_status_checking),
                            statusColor = R.color.status_warning,
                            isAcquiring = true,
                            isShutterEnabled = false,
                            showSettingsBtn = false
                        )
                    }

                    is GpsValidationResult.SearchingForSatellites -> {
                        verifiedLocation = null
                        updateGpsUi(
                            statusText = getString(R.string.gps_status_checking),
                            statusColor = R.color.status_warning,
                            isAcquiring = true,
                            isShutterEnabled = false,
                            showSettingsBtn = false
                        )
                    }

                    is GpsValidationResult.LocationServicesDisabled -> {
                        verifiedLocation = null
                        updateGpsUi(
                            statusText = getString(R.string.gps_status_disabled),
                            statusColor = R.color.status_bad,
                            isAcquiring = false,
                            isShutterEnabled = false,
                            showSettingsBtn = true
                        )
                    }

                    is GpsValidationResult.PermissionMissing -> {
                        verifiedLocation = null
                        updateGpsUi(
                            statusText = "Location Permission Required",
                            statusColor = R.color.status_bad,
                            isAcquiring = false,
                            isShutterEnabled = false,
                            showSettingsBtn = false
                        )
                    }

                    is GpsValidationResult.InaccurateFix -> {
                        verifiedLocation = null
                        updateGpsUi(
                            statusText = getString(R.string.gps_status_weak, state.accuracyMeters, state.requiredThreshold),
                            statusColor = R.color.status_warning,
                            isAcquiring = true,
                            isShutterEnabled = false,
                            showSettingsBtn = false
                        )
                        displayCoordinates(state.location, state.accuracyMeters, state.ageMs, isValid = false)
                    }

                    is GpsValidationResult.StaleFix -> {
                        verifiedLocation = null
                        updateGpsUi(
                            statusText = getString(R.string.gps_status_stale),
                            statusColor = R.color.status_warning,
                            isAcquiring = true,
                            isShutterEnabled = false,
                            showSettingsBtn = false
                        )
                    }

                    is GpsValidationResult.MockOrNetworkLocationRejected -> {
                        verifiedLocation = null
                        updateGpsUi(
                            statusText = state.reason,
                            statusColor = R.color.status_bad,
                            isAcquiring = false,
                            isShutterEnabled = false,
                            showSettingsBtn = false
                        )
                    }

                    is GpsValidationResult.ValidGpsFix -> {
                        verifiedLocation = state.location
                        updateGpsUi(
                            statusText = getString(R.string.gps_status_locked, state.accuracyMeters),
                            statusColor = R.color.status_good,
                            isAcquiring = false,
                            isShutterEnabled = true,
                            showSettingsBtn = false
                        )
                        displayCoordinates(state.location, state.accuracyMeters, state.ageMs, isValid = true)
                    }
                }
            }
        }
    }

    private fun updateGpsUi(
        statusText: String,
        statusColor: Int,
        isAcquiring: Boolean,
        isShutterEnabled: Boolean,
        showSettingsBtn: Boolean
    ) {
        binding.tvGpsStatusText.text = statusText
        binding.ivGpsStatusIcon.imageTintList = ContextCompat.getColorStateList(this, statusColor)
        binding.pbGpsAcquiring.visibility = if (isAcquiring) View.VISIBLE else View.GONE
        binding.btnCaptureShutter.isEnabled = isShutterEnabled
        binding.btnCaptureShutter.alpha = if (isShutterEnabled) 1.0f else 0.45f
        binding.btnOpenGpsSettings.visibility = if (showSettingsBtn) View.VISIBLE else View.GONE
    }

    private fun displayCoordinates(location: Location, accuracy: Float, ageMs: Long, isValid: Boolean) {
        binding.tvLatLong.text = String.format(
            Locale.US,
            "Lat: %.6f°  Long: %.6f°",
            location.latitude,
            location.longitude
        )
        binding.tvAccuracyBadge.text = String.format(Locale.US, "±%.1fm", accuracy)
        binding.tvAccuracyBadge.setTextColor(
            ContextCompat.getColor(this, if (isValid) R.color.status_good else R.color.status_warning)
        )
        val ageSeconds = (ageMs / 1000.0)
        binding.tvFreshnessAndProvider.text = String.format(
            Locale.US,
            "Signal Age: %.1fs • Source: %s (Verified Satellite)",
            ageSeconds,
            location.provider ?: "Hardware GPS"
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to initialize camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun attemptCaptureWithRevalidation() {
        val imageCapture = this.imageCapture ?: return

        // Critical Requirement 12: Revalidate location immediately before capture and reject stale fixes
        val revalidation = gpsLocationManager.validateForCapture()
        if (revalidation !is GpsValidationResult.ValidGpsFix) {
            Toast.makeText(
                this,
                "Capture blocked: GPS fix has expired or accuracy degraded. Waiting for fresh satellite signal…",
                Toast.LENGTH_LONG
            ).show()
            binding.btnCaptureShutter.isEnabled = false
            return
        }

        val location = revalidation.location
        val photoFile = FileUtils.createPhotoFile(this, recordId)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.btnCaptureShutter.isEnabled = false
        binding.pbGpsAcquiring.visibility = View.VISIBLE

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        val previewIntent = Intent(this@GpsCaptureActivity, EvidencePreviewActivity::class.java).apply {
                            putExtra("RECORD_ID", recordId)
                            putExtra("PHOTO_PATH", photoFile.absolutePath)
                            putExtra("LATITUDE", location.latitude)
                            putExtra("LONGITUDE", location.longitude)
                            putExtra("ACCURACY", location.accuracy)
                            if (location.hasAltitude()) {
                                putExtra("ALTITUDE", location.altitude)
                            }
                            putExtra("GPS_TIME", location.time)
                            putExtra("GPS_PROVIDER", location.provider ?: "gps")
                        }
                        startActivity(previewIntent)
                        finish()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        binding.btnCaptureShutter.isEnabled = true
                        binding.pbGpsAcquiring.visibility = View.GONE
                        Toast.makeText(this@GpsCaptureActivity, "Camera capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}
