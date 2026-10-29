package org.bataydar.evidence.ui.landlord

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.databinding.ActivityLandlordConfirmationBinding
import org.json.JSONObject

class LandlordConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandlordConfirmationBinding
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }
    private var recordId: String = ""
    private var recordLandlordPhone: String = ""
    private var recordLandlordName: String = ""

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

            recordLandlordPhone = record.landlordPhone.orEmpty()
            recordLandlordName = record.landlordName.orEmpty()

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
                val tokenManager = TokenManager.getInstance(this@LandlordConfirmationActivity)
                val apiClient = ApiClient.getInstance(this@LandlordConfirmationActivity)

                if (selectedStatus == LandlordStatus.REQUESTED && tokenManager.isLoggedIn() && recordLandlordPhone.isNotBlank()) {
                    try {
                        val resp = apiClient.cropRecordApi.requestLandlordConfirmation(
                            recordId,
                            mapOf(
                                "landlordName" to recordLandlordName.ifEmpty { "Landowner" },
                                "landlordPhone" to recordLandlordPhone,
                                "verificationMethod" to "DIRECT_VERBAL",
                                "notes" to (notes ?: "")
                            )
                        )

                        if (!resp.isSuccessful) {
                            val errorJson = resp.errorBody()?.string()
                            var errorCode = ""
                            var errorMessage = ""
                            if (!errorJson.isNullOrBlank()) {
                                try {
                                    val obj = JSONObject(errorJson)
                                    val errObj = obj.optJSONObject("error")
                                    errorCode = errObj?.optString("code") ?: ""
                                    errorMessage = errObj?.optString("message") ?: ""
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (resp.code() == 404 && (errorCode == "LANDLORD_NOT_REGISTERED" || errorMessage.contains("registered", ignoreCase = true))) {
                                MaterialAlertDialogBuilder(this@LandlordConfirmationActivity)
                                    .setTitle(R.string.landlord_unregistered_dialog_title)
                                    .setMessage(getString(R.string.landlord_unregistered_dialog_message, recordLandlordPhone))
                                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                                        dialog.dismiss()
                                        finish()
                                    }
                                    .show()
                                return@launch
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

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
