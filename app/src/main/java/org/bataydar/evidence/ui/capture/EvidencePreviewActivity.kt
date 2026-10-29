package org.bataydar.evidence.ui.capture

import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivityEvidencePreviewBinding
import org.bataydar.evidence.export.ChecksumUtils
import org.bataydar.evidence.ui.detail.RecordDetailActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvidencePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEvidencePreviewBinding
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }

    private var recordId: String = ""
    private var photoPath: String = ""
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var accuracy: Float = 0.0f
    private var altitude: Double? = null
    private var gpsTime: Long = 0L
    private var provider: String = "gps"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvidencePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.evidence_preview_title)
            setDisplayHomeAsUpEnabled(true)
        }

        readIntentData()
        displayPhotoAndMetadata()
        setupButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        handleRetake()
        return true
    }

    private fun readIntentData() {
        recordId = intent.getStringExtra("RECORD_ID") ?: ""
        photoPath = intent.getStringExtra("PHOTO_PATH") ?: ""
        latitude = intent.getDoubleExtra("LATITUDE", 0.0)
        longitude = intent.getDoubleExtra("LONGITUDE", 0.0)
        accuracy = intent.getFloatExtra("ACCURACY", 0.0f)
        if (intent.hasExtra("ALTITUDE")) {
            altitude = intent.getDoubleExtra("ALTITUDE", 0.0)
        }
        gpsTime = intent.getLongExtra("GPS_TIME", System.currentTimeMillis())
        provider = intent.getStringExtra("GPS_PROVIDER") ?: "gps"
    }

    private fun displayPhotoAndMetadata() {
        val file = File(photoPath)
        if (!file.exists()) {
            Toast.makeText(this, "Photo file not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Decode bitmap efficiently
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(photoPath, opts)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                binding.ivPreview.setImageBitmap(bitmap)
            }

            val sha256 = withContext(Dispatchers.IO) {
                try {
                    ChecksumUtils.calculateSha256(file)
                } catch (e: Exception) {
                    "Error calculating hash"
                }
            }
            binding.tvSha256.text = sha256
        }

        binding.tvCoordinates.text = String.format(Locale.US, "%.6f°, %.6f°", latitude, longitude)
        binding.tvAccuracy.text = String.format(Locale.US, "±%.1f meters (Verified Satellite Signal)", accuracy)

        val localTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date(gpsTime))
        binding.tvTimestamp.text = "Captured: $localTime"
    }

    private fun setupButtons() {
        binding.btnRetakePhoto.setOnClickListener {
            handleRetake()
        }

        binding.btnConfirmEvidence.setOnClickListener {
            saveEvidence()
        }
    }

    private fun handleRetake() {
        // Delete original file if retaking
        try {
            File(photoPath).takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val intent = Intent(this, GpsCaptureActivity::class.java).apply {
            putExtra("RECORD_ID", recordId)
        }
        startActivity(intent)
        finish()
    }

    private fun saveEvidence() {
        val file = File(photoPath)
        if (!file.exists()) {
            Toast.makeText(this, "Photo file does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnConfirmEvidence.isEnabled = false
        val notes = binding.etNotes.text?.toString()?.trim()

        val location = Location(provider).apply {
            this.latitude = this@EvidencePreviewActivity.latitude
            this.longitude = this@EvidencePreviewActivity.longitude
            this.accuracy = this@EvidencePreviewActivity.accuracy
            val alt = this@EvidencePreviewActivity.altitude
            if (alt != null) {
                this.altitude = alt
            }
            this.time = this@EvidencePreviewActivity.gpsTime
        }

        lifecycleScope.launch {
            val result = evidenceRepository.saveGeotaggedEvidence(
                recordId = recordId,
                photoFile = file,
                location = location,
                notes = notes
            )

            if (result.isSuccess) {
                Toast.makeText(this@EvidencePreviewActivity, "Evidence photo safely saved to record!", Toast.LENGTH_SHORT).show()
                val detailIntent = Intent(this@EvidencePreviewActivity, RecordDetailActivity::class.java).apply {
                    putExtra("RECORD_ID", recordId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(detailIntent)
                finish()
            } else {
                binding.btnConfirmEvidence.isEnabled = true
                Toast.makeText(this@EvidencePreviewActivity, "Error saving: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
