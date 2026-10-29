package org.bataydar.evidence.ui.landlord

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.databinding.ActivityLandlordConfirmationBinding

class LandlordConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandlordConfirmationBinding
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private var recordId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandlordConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recordId = intent.getStringExtra("RECORD_ID") ?: ""
        if (recordId.isEmpty()) {
            Toast.makeText(this, "Record ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        supportActionBar?.apply {
            title = getString(R.string.landlord_section_title)
            setDisplayHomeAsUpEnabled(true)
        }

        loadCurrentStatus()
        setupSaveButton()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadCurrentStatus() {
        lifecycleScope.launch {
            val recordWithEvidence = evidenceRepository.getRecordWithEvidence(recordId)
            val record = recordWithEvidence?.record ?: return@launch

            when (record.landlordConfirmationStatus) {
                LandlordStatus.NOT_REQUESTED -> binding.rbNotRequested.isChecked = true
                LandlordStatus.REQUESTED -> binding.rbRequested.isChecked = true
                LandlordStatus.CONFIRMED_VERBAL -> binding.rbConfirmedVerbal.isChecked = true
                LandlordStatus.CONFIRMED_WRITTEN -> binding.rbConfirmedWritten.isChecked = true
                LandlordStatus.REFUSED -> binding.rbRefused.isChecked = true
                LandlordStatus.UNRESPONSIVE -> binding.rbUnresponsive.isChecked = true
            }

            binding.etNotes.setText(record.landlordConfirmationNotes ?: "")
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveStatus.setOnClickListener {
            val selectedStatus = when (binding.rgLandlordStatus.checkedRadioButtonId) {
                R.id.rbRequested -> LandlordStatus.REQUESTED
                R.id.rbConfirmedVerbal -> LandlordStatus.CONFIRMED_VERBAL
                R.id.rbConfirmedWritten -> LandlordStatus.CONFIRMED_WRITTEN
                R.id.rbRefused -> LandlordStatus.REFUSED
                R.id.rbUnresponsive -> LandlordStatus.UNRESPONSIVE
                else -> LandlordStatus.NOT_REQUESTED
            }

            val notes = binding.etNotes.text?.toString()?.trim()

            lifecycleScope.launch {
                evidenceRepository.updateLandlordConfirmation(
                    recordId = recordId,
                    status = selectedStatus,
                    notes = notes
                )
                Toast.makeText(this@LandlordConfirmationActivity, "Landlord status updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
