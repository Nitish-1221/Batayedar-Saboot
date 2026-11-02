package org.bataydar.evidence.ui.landlord

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.remote.dto.LandlordEvidencePhotoDto
import org.bataydar.evidence.data.remote.dto.LandlordRequestDetailResponseDto
import org.bataydar.evidence.databinding.ActivityLandlordReviewBinding

class LandlordReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandlordReviewBinding
    private val landlordRepository by lazy { BataydarApp.instance.landlordRepository }
    private var confirmationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandlordReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        confirmationId = intent.getStringExtra(EXTRA_CONFIRMATION_ID).orEmpty()
        if (confirmationId.isBlank()) {
            Toast.makeText(this, "Invalid confirmation request ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        setupListeners()
        loadDetails()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.btnSubmitDecision.setOnClickListener {
            submitDecision()
        }
    }

    private fun loadDetails() {
        setLoading(true)
        lifecycleScope.launch {
            val result = landlordRepository.getRequestDetails(confirmationId)
            setLoading(false)
            result.onSuccess { data ->
                renderDetails(data)
            }.onFailure { err ->
                Snackbar.make(binding.root, err.message ?: "Failed to load request details", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun renderDetails(data: LandlordRequestDetailResponseDto) {
        val farmerInfo = data.farmerDeclaredInfo
        if (farmerInfo != null) {
            val nameText = farmerInfo.farmerName ?: "Farmer"
            val phoneText = farmerInfo.farmerPhone ?: ""
            binding.tvFarmerNameAndPhone.text = "$nameText ($phoneText)"
            binding.tvFarmerLocation.text = farmerInfo.farmerLocation ?: "Samastipur, Bihar"

            val cropText = farmerInfo.cropType ?: "Crop"
            val fieldText = farmerInfo.fieldName ?: "Field"
            binding.tvCropAndField.text = "$cropText • $fieldText"

            val dateText = farmerInfo.sowingDate ?: ""
            binding.tvSowingDate.text = "Sowing Date: ${dateText.take(10)}"

            val descText = farmerInfo.fieldDescription ?: ""
            if (descText.isNotBlank()) {
                binding.tvFarmerDescription.visibility = View.VISIBLE
                binding.tvFarmerDescription.text = "Farmer Description & Notes:\n$descText"
            } else {
                binding.tvFarmerDescription.visibility = View.GONE
            }
        }

        // Render Independently Verified GPS Evidence Photos
        val telemetry = data.independentlyVerifiedTelemetry
        val photos = telemetry?.evidencePhotos ?: emptyList()
        renderEvidencePhotos(photos)

        // Response Section: Check if already answered or pending
        if (data.canRespond) {
            binding.layoutResponseForm.visibility = View.VISIBLE
            binding.layoutAlreadyResponded.visibility = View.GONE
        } else {
            binding.layoutResponseForm.visibility = View.GONE
            binding.layoutAlreadyResponded.visibility = View.VISIBLE

            binding.tvFinalStatusBadge.text = data.status
            when (data.status) {
                "CONFIRMED_WRITTEN", "CONFIRMED_VERBAL" -> {
                    binding.tvFinalStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_good))
                    binding.tvFinalStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_good_light))
                }
                "REFUSED" -> {
                    binding.tvFinalStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_bad))
                    binding.tvFinalStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_bad_light))
                }
                else -> {
                    binding.tvFinalStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_neutral))
                    binding.tvFinalStatusBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.status_neutral_light))
                }
            }

            val dateStr = data.respondedAt ?: ""
            binding.tvRespondedDate.text = "Decision recorded on: ${dateStr.take(10)}"

            val notes = data.notes.orEmpty()
            if (notes.isNotBlank()) {
                binding.tvRecordedNotes.visibility = View.VISIBLE
                binding.tvRecordedNotes.text = "Recorded Landlord Remarks:\n$notes"
            } else {
                binding.tvRecordedNotes.visibility = View.GONE
            }
        }
    }

    private fun renderEvidencePhotos(photos: List<LandlordEvidencePhotoDto>) {
        binding.layoutEvidenceItems.removeAllViews()

        if (photos.isEmpty()) {
            binding.tvNoEvidenceNotice.visibility = View.VISIBLE
            return
        }

        binding.tvNoEvidenceNotice.visibility = View.GONE

        photos.forEachIndexed { index, photo ->
            val photoCard = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
                radius = 12f
                strokeWidth = 2
                setStrokeColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.card_stroke))
                setCardBackgroundColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.bg_screen))
            }

            val cardContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
            }

            val titleView = TextView(this).apply {
                text = "GPS Evidence Photograph #${index + 1}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.primary_green_dark))
            }
            cardContent.addView(titleView)

            // Coordinates
            val coordsView = TextView(this).apply {
                val lat = photo.latitude ?: 0.0
                val lng = photo.longitude ?: 0.0
                text = String.format("GPS Coordinates: %.6f° N, %.6f° E", lat, lng)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.text_primary))
                setPadding(0, 8, 0, 2)
            }
            cardContent.addView(coordsView)

            // Accuracy & satellite lock
            val accuracyView = TextView(this).apply {
                val acc = photo.accuracyMeters ?: 0f
                text = String.format("Satellite Measured Accuracy: ±%.1fm (Threshold < 20m)", acc)
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.status_good))
                setPadding(0, 0, 0, 2)
            }
            cardContent.addView(accuracyView)

            // Timestamp
            val timeView = TextView(this).apply {
                val time = photo.capturedAtLocal ?: photo.capturedAtUtc?.toString() ?: ""
                text = "Capture Time: $time"
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.text_secondary))
                setPadding(0, 0, 0, 4)
            }
            cardContent.addView(timeView)

            // SHA-256 Checksum fingerprint
            val shaView = TextView(this).apply {
                val sha = photo.photoSha256 ?: ""
                text = "SHA-256 Fingerprint: ${if (sha.length > 24) sha.take(24) + "…" else sha}"
                textSize = 11f
                setTextColor(ContextCompat.getColor(this@LandlordReviewActivity, R.color.text_muted))
                setTypeface(Typeface.MONOSPACE)
            }
            cardContent.addView(shaView)

            photoCard.addView(cardContent)
            binding.layoutEvidenceItems.addView(photoCard)
        }
    }

    private fun submitDecision() {
        val isConfirm = binding.rbConfirm.isChecked
        val chosenStatus = if (isConfirm) "CONFIRMED_WRITTEN" else "REFUSED"
        val notes = binding.etResponseNotes.text?.toString()?.trim().orEmpty()
        val witness = binding.etWitnessName.text?.toString()?.trim().orEmpty()

        setLoading(true)
        lifecycleScope.launch {
            val result = landlordRepository.respondToRequest(
                confirmationId = confirmationId,
                status = chosenStatus,
                notes = notes,
                witnessName = witness
            )
            setLoading(false)
            result.onSuccess {
                Toast.makeText(this@LandlordReviewActivity, R.string.landlord_response_success, Toast.LENGTH_SHORT).show()
                // Reload details to display updated read-only view and prevent duplicate response
                loadDetails()
            }.onFailure { err ->
                Snackbar.make(binding.root, err.message ?: "Failed to submit decision", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmitDecision.isEnabled = !loading
        binding.scrollContent.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    companion object {
        const val EXTRA_CONFIRMATION_ID = "EXTRA_CONFIRMATION_ID"
    }
}
