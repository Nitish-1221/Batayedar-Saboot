package org.bataydar.evidence.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.model.GeotaggedEvidenceEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.model.RecordWithEvidence
import org.bataydar.evidence.databinding.ActivityRecordDetailBinding
import org.bataydar.evidence.ui.capture.GpsCaptureActivity
import org.bataydar.evidence.ui.landlord.LandlordConfirmationActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordDetailBinding
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val pdfExporter by lazy { BataydarApp.instance.pdfExporter }
    private val zipExporter by lazy { BataydarApp.instance.zipExporter }

    private var recordId: String = ""
    private var currentRecordWithEvidence: RecordWithEvidence? = null
    private var activeProfile: FarmerProfileEntity? = null
    private lateinit var galleryAdapter: EvidenceGalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordId = intent.getStringExtra("RECORD_ID") ?: ""
        if (recordId.isEmpty()) {
            Toast.makeText(this, "Record ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.apply {
            title = getString(R.string.record_detail_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupRecyclerView()
        setupButtons()
        observeData()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_record_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_record -> {
                showDeleteConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        galleryAdapter = EvidenceGalleryAdapter { evidence ->
            showDeleteEvidenceConfirmation(evidence)
        }
        binding.rvEvidenceGallery.layoutManager = LinearLayoutManager(this)
        binding.rvEvidenceGallery.adapter = galleryAdapter
    }

    private fun setupButtons() {
        binding.btnAddMorePhoto.setOnClickListener {
            val intent = Intent(this, GpsCaptureActivity::class.java).apply {
                putExtra("RECORD_ID", recordId)
            }
            startActivity(intent)
        }

        binding.btnUpdateLandlordStatus.setOnClickListener {
            val intent = Intent(this, LandlordConfirmationActivity::class.java).apply {
                putExtra("RECORD_ID", recordId)
            }
            startActivity(intent)
        }

        binding.btnExportPdf.setOnClickListener {
            exportPdf()
        }

        binding.btnExportZip.setOnClickListener {
            exportZip()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            activeProfile = profileRepository.getProfile()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                evidenceRepository.getRecordWithEvidenceFlow(recordId).collect { item ->
                    if (item == null) {
                        finish()
                        return@collect
                    }
                    currentRecordWithEvidence = item
                    bindRecordDetails(item)
                }
            }
        }
    }

    private fun bindRecordDetails(item: RecordWithEvidence) {
        val record = item.record
        binding.tvCropType.text = record.cropType
        binding.tvFieldName.text = "Field: ${record.fieldName}"
        binding.tvLocation.text = "Village: ${record.village} • District: ${record.district} • Sown: ${record.sowingDate}"

        val createdStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(record.createdAt))
        binding.tvRecordIdAndCreated.text = "Record ID: ${record.recordId} • Created: $createdStr"

        if (!record.fieldDescription.isNullOrEmpty()) {
            binding.tvFieldNotes.visibility = View.VISIBLE
            binding.tvFieldNotes.text = "Notes: ${record.fieldDescription}"
        } else {
            binding.tvFieldNotes.visibility = View.GONE
        }

        // Landlord Status
        val landlordStatus = record.landlordConfirmationStatus
        binding.tvLandlordBadge.text = landlordStatus.getDisplayName(this)
        when (landlordStatus) {
            LandlordStatus.CONFIRMED_VERBAL, LandlordStatus.CONFIRMED_WRITTEN -> {
                binding.tvLandlordBadge.setTextColor(ContextCompat.getColor(this, R.color.status_good))
                binding.tvLandlordBadge.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_good_light)
            }
            LandlordStatus.REQUESTED -> {
                binding.tvLandlordBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                binding.tvLandlordBadge.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_warning_light)
            }
            LandlordStatus.REFUSED -> {
                binding.tvLandlordBadge.setTextColor(ContextCompat.getColor(this, R.color.status_bad))
                binding.tvLandlordBadge.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_bad_light)
            }
            else -> {
                binding.tvLandlordBadge.setTextColor(ContextCompat.getColor(this, R.color.status_neutral))
                binding.tvLandlordBadge.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_neutral_light)
            }
        }

        val landlordName = record.landlordName ?: "Not specified"
        val notes = record.landlordConfirmationNotes ?: "No confirmation notes"
        binding.tvLandlordDetails.text = "Landowner: $landlordName • Details: $notes"

        // Evidence Gallery
        binding.tvEvidenceHeader.text = "GPS Evidence Photographs (${item.evidenceList.size})"
        galleryAdapter.submitList(item.evidenceList)

        if (item.evidenceList.isEmpty()) {
            binding.tvNoPhotosYet.visibility = View.VISIBLE
            binding.rvEvidenceGallery.visibility = View.GONE
        } else {
            binding.tvNoPhotosYet.visibility = View.GONE
            binding.rvEvidenceGallery.visibility = View.VISIBLE
        }
    }

    private fun exportPdf() {
        val record = currentRecordWithEvidence ?: return
        lifecycleScope.launch {
            Toast.makeText(this@RecordDetailActivity, "Generating PDF Dossier…", Toast.LENGTH_SHORT).show()
            val result = pdfExporter.generateDossierPdf(record, activeProfile)
            if (result.isSuccess) {
                val pdfFile = result.getOrThrow()
                evidenceRepository.logAuditAction(recordId, "PDF_EXPORTED", "Generated PDF: ${pdfFile.name}")
                shareFile(pdfFile, "application/pdf", "Crop Evidence Dossier - ${record.record.fieldName}")
            } else {
                Toast.makeText(this@RecordDetailActivity, "PDF export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportZip() {
        val record = currentRecordWithEvidence ?: return
        lifecycleScope.launch {
            Toast.makeText(this@RecordDetailActivity, "Preparing full ZIP package with photos & metadata…", Toast.LENGTH_SHORT).show()
            val result = zipExporter.exportZipBundle(record, activeProfile)
            if (result.isSuccess) {
                val zipFile = result.getOrThrow()
                evidenceRepository.logAuditAction(recordId, "ZIP_EXPORTED", "Generated ZIP bundle: ${zipFile.name}")
                shareFile(zipFile, "application/zip", "Crop Evidence ZIP Bundle - ${record.record.fieldName}")
            } else {
                Toast.makeText(this@RecordDetailActivity, "ZIP export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(file: File, mimeType: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Crop cultivation evidence dossier for field '${currentRecordWithEvidence?.record?.fieldName}' generated by Batayedar Saboot.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Dossier via…"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    evidenceRepository.deleteRecord(recordId)
                    Toast.makeText(this@RecordDetailActivity, "Record deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDeleteEvidenceConfirmation(evidence: GeotaggedEvidenceEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Evidence Photo?")
            .setMessage("Are you sure you want to delete this GPS photograph? This action cannot be undone.")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    evidenceRepository.deleteEvidence(evidence.evidenceId)
                    Toast.makeText(this@RecordDetailActivity, "Photo deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
