package org.bataydar.evidence.ui.export

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivityExportDossierBinding
import java.io.File

class ExportDossierActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportDossierBinding
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val pdfExporter by lazy { BataydarApp.instance.pdfExporter }
    private val zipExporter by lazy { BataydarApp.instance.zipExporter }

    private var recordId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportDossierBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordId = intent.getStringExtra("RECORD_ID") ?: ""

        supportActionBar?.apply {
            title = getString(R.string.export_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupButtons() {
        binding.btnGeneratePdf.setOnClickListener {
            generateAndSharePdf()
        }

        binding.btnGenerateZip.setOnClickListener {
            generateAndShareZip()
        }
    }

    private fun generateAndSharePdf() {
        if (recordId.isEmpty()) {
            Toast.makeText(this, "No record selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.tvExportStatus.visibility = View.VISIBLE
            binding.tvExportStatus.text = "Generating Printable PDF Dossier…"

            val record = evidenceRepository.getRecordWithEvidence(recordId)
            val profile = profileRepository.getProfile()

            if (record == null) {
                binding.tvExportStatus.text = "Record not found"
                return@launch
            }

            val result = pdfExporter.generateDossierPdf(record, profile)
            if (result.isSuccess) {
                val pdfFile = result.getOrThrow()
                binding.tvExportStatus.text = "PDF Created: ${pdfFile.name}"
                shareFile(pdfFile, "application/pdf")
            } else {
                binding.tvExportStatus.text = "PDF failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun generateAndShareZip() {
        if (recordId.isEmpty()) {
            Toast.makeText(this, "No record selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            binding.tvExportStatus.visibility = View.VISIBLE
            binding.tvExportStatus.text = "Packaging photos, PDF, and metadata into ZIP…"

            val record = evidenceRepository.getRecordWithEvidence(recordId)
            val profile = profileRepository.getProfile()

            if (record == null) {
                binding.tvExportStatus.text = "Record not found"
                return@launch
            }

            val result = zipExporter.exportZipBundle(record, profile)
            if (result.isSuccess) {
                val zipFile = result.getOrThrow()
                binding.tvExportStatus.text = "ZIP Bundle Created: ${zipFile.name}"
                shareFile(zipFile, "application/zip")
            } else {
                binding.tvExportStatus.text = "ZIP failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Dossier via…"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
