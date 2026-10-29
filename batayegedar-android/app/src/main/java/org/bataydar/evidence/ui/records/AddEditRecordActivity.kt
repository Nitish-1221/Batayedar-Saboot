package org.bataydar.evidence.ui.records

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.model.CropRecordEntity
import org.bataydar.evidence.data.model.FarmerProfileEntity
import org.bataydar.evidence.data.model.LandlordStatus
import org.bataydar.evidence.data.remote.ApiClient
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.remote.dto.SyncCropRecordItem
import org.bataydar.evidence.data.remote.dto.SyncFieldItem
import org.bataydar.evidence.data.remote.dto.SyncPushRequest
import org.bataydar.evidence.databinding.ActivityAddEditRecordBinding
import org.bataydar.evidence.ui.capture.GpsCaptureActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class AddEditRecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditRecordBinding
    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val evidenceRepository by lazy { BataydarApp.instance.evidenceRepository }

    private var activeProfile: FarmerProfileEntity? = null
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.add_record_title)
            setDisplayHomeAsUpEnabled(true)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(getColor(R.color.primary_green)))
        }

        binding.tvRecordIdLabel.text = getString(R.string.add_record_title)
        setupDateDefaults()
        setupCropChips()
        loadFarmerProfile()
        setupButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupDateDefaults() {
        val todayStr = dateFormat.format(Date())
        binding.etSowingDate.setText(todayStr)
        binding.etSowingDate.setTextColor(getColor(R.color.text_primary))

        binding.etSowingDate.setOnClickListener { showDatePicker() }
        binding.tilSowingDate.setEndIconOnClickListener { showDatePicker() }
    }

    private fun showDatePicker() {
        val picker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                binding.etSowingDate.setText(dateFormat.format(calendar.time))
                binding.etSowingDate.setTextColor(getColor(R.color.text_primary))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Disallow future dates for sowing
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }

    private fun setupCropChips() {
        val chipPairs = listOf(
            binding.chipPaddy to getString(R.string.crop_paddy),
            binding.chipWheat to getString(R.string.crop_wheat),
            binding.chipMustard to getString(R.string.crop_mustard),
            binding.chipSugarcane to getString(R.string.crop_sugarcane),
            binding.chipMaize to getString(R.string.crop_maize),
            binding.chipVegetables to getString(R.string.crop_vegetables)
        )

        chipPairs.forEach { (chip, cropName) ->
            chip.setOnClickListener {
                chipPairs.forEach { (otherChip, _) -> otherChip.isChecked = (otherChip == chip) }
                binding.etCropType.setText(cropName)
                binding.tilCropType.error = null
            }
        }
    }

    private fun loadFarmerProfile() {
        lifecycleScope.launch {
            val profile = profileRepository.getProfile()
            activeProfile = profile
            if (profile != null) {
                binding.tvFarmerIdHeader.text = "Farmer: ${profile.fullName} (ID: ${profile.farmerId}) • ${profile.village}"
            } else {
                binding.tvFarmerIdHeader.text = "Local Farmer Record"
            }
        }
    }

    private fun setupButtons() {
        binding.btnSaveDraft.setOnClickListener {
            saveRecord(launchCaptureAfter = false)
        }

        binding.btnSaveAndCapture.setOnClickListener {
            saveRecord(launchCaptureAfter = true)
        }
    }

    private fun saveRecord(launchCaptureAfter: Boolean) {
        val fieldName = binding.etFieldName.text?.toString().orEmpty().trim()
        val cropType = binding.etCropType.text?.toString().orEmpty().trim()
        val sowingDate = binding.etSowingDate.text?.toString().orEmpty().trim()
        val fieldDesc = binding.etFieldDesc.text?.toString().orEmpty().trim()
        val landlordName = binding.etLandlordName.text?.toString().orEmpty().trim()
        val landlordPhone = binding.etLandlordPhone.text?.toString().orEmpty().trim()
        val requestConfirmation = binding.cbRequestConfirmation.isChecked

        var isValid = true

        if (fieldName.isEmpty()) {
            binding.tilFieldName.error = getString(R.string.error_field_name_required)
            isValid = false
        } else {
            binding.tilFieldName.error = null
        }

        if (cropType.isEmpty()) {
            binding.tilCropType.error = getString(R.string.error_crop_required)
            isValid = false
        } else {
            binding.tilCropType.error = null
        }

        if (sowingDate.isEmpty()) {
            binding.tilSowingDate.error = getString(R.string.error_date_required)
            isValid = false
        } else {
            binding.tilSowingDate.error = null
        }

        // Landlord details are completely optional UNLESS confirmation request is explicitly checked
        if (requestConfirmation) {
            val digitsOnly = landlordPhone.filter { it.isDigit() }
            if (digitsOnly.length != 10) {
                binding.tilLandlordPhone.error = getString(R.string.landlord_phone_required_for_confirmation)
                isValid = false
            } else {
                binding.tilLandlordPhone.error = null
            }
        } else {
            binding.tilLandlordPhone.error = null
        }

        if (!isValid) return

        val profile = activeProfile
        val farmerId = profile?.farmerId ?: "LOCAL-FARMER"
        val village = profile?.village ?: "Local Village"
        val district = profile?.district ?: "Local District"
        val state = profile?.state ?: "Local State"

        lifecycleScope.launch {
            val result = evidenceRepository.createCropRecord(
                farmerId = farmerId,
                fieldName = fieldName,
                village = village,
                district = district,
                state = state,
                cropType = cropType,
                sowingDate = sowingDate,
                fieldDescription = fieldDesc,
                landlordName = landlordName,
                landlordPhone = landlordPhone
            )

            if (result.isSuccess) {
                val createdRecord = result.getOrThrow()

                if (requestConfirmation) {
                    processLandlordConfirmationRequest(
                        record = createdRecord,
                        landlordName = landlordName.ifEmpty { "Landowner" },
                        landlordPhone = landlordPhone,
                        launchCaptureAfter = launchCaptureAfter
                    )
                } else {
                    Toast.makeText(this@AddEditRecordActivity, "Crop record saved!", Toast.LENGTH_SHORT).show()
                    proceedNext(createdRecord.recordId, launchCaptureAfter)
                }
            } else {
                Toast.makeText(this@AddEditRecordActivity, result.exceptionOrNull()?.message ?: "Error saving record", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun processLandlordConfirmationRequest(
        record: CropRecordEntity,
        landlordName: String,
        landlordPhone: String,
        launchCaptureAfter: Boolean
    ) {
        val tokenManager = TokenManager.getInstance(this@AddEditRecordActivity)
        val apiClient = ApiClient.getInstance(this@AddEditRecordActivity)

        if (!tokenManager.isLoggedIn()) {
            Toast.makeText(
                this@AddEditRecordActivity,
                "Crop record saved locally. Log in to cloud to request landlord confirmation.",
                Toast.LENGTH_LONG
            ).show()
            proceedNext(record.recordId, launchCaptureAfter)
            return
        }

        try {
            // Push record and field to backend first to ensure MongoDB persistence
            val syncField = SyncFieldItem(
                fieldId = record.recordId,
                fieldName = record.fieldName,
                village = record.village,
                district = record.district,
                state = record.state
            )
            val syncCrop = SyncCropRecordItem(
                recordId = record.recordId,
                fieldId = record.recordId,
                cropType = record.cropType,
                cultivationStartDate = record.sowingDate,
                notes = record.fieldDescription ?: "",
                landlordName = landlordName,
                landlordPhone = landlordPhone
            )
            apiClient.syncApi.pushSync(
                SyncPushRequest(
                    idempotencyKey = "sync-create-${UUID.randomUUID()}",
                    fields = listOf(syncField),
                    cropRecords = listOf(syncCrop)
                )
            )

            // Submit confirmation request
            val response = apiClient.cropRecordApi.requestLandlordConfirmation(
                record.recordId,
                mapOf(
                    "landlordName" to landlordName,
                    "landlordPhone" to landlordPhone,
                    "verificationMethod" to "DIRECT_VERBAL",
                    "notes" to "Confirmation requested during crop record creation."
                )
            )

            if (response.isSuccessful) {
                evidenceRepository.updateLandlordConfirmation(
                    recordId = record.recordId,
                    status = LandlordStatus.REQUESTED,
                    notes = "Confirmation request submitted to $landlordName ($landlordPhone)"
                )
                Toast.makeText(
                    this@AddEditRecordActivity,
                    "Crop record saved and confirmation requested!",
                    Toast.LENGTH_SHORT
                ).show()
                proceedNext(record.recordId, launchCaptureAfter)
            } else {
                val errorJson = response.errorBody()?.string()
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

                if (response.code() == 404 && (errorCode == "LANDLORD_NOT_REGISTERED" || errorMessage.contains("registered", ignoreCase = true))) {
                    MaterialAlertDialogBuilder(this@AddEditRecordActivity)
                        .setTitle(R.string.landlord_unregistered_dialog_title)
                        .setMessage(getString(R.string.landlord_unregistered_dialog_message, landlordPhone))
                        .setPositiveButton(android.R.string.ok) { dialog, _ ->
                            dialog.dismiss()
                            proceedNext(record.recordId, launchCaptureAfter)
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    val displayMsg = if (errorMessage.isNotEmpty()) errorMessage else "Could not request confirmation (HTTP ${response.code()})"
                    Toast.makeText(this@AddEditRecordActivity, "Record saved. $displayMsg", Toast.LENGTH_LONG).show()
                    proceedNext(record.recordId, launchCaptureAfter)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(
                this@AddEditRecordActivity,
                "Crop record saved safely locally.",
                Toast.LENGTH_SHORT
            ).show()
            proceedNext(record.recordId, launchCaptureAfter)
        }
    }

    private fun proceedNext(recordId: String, launchCaptureAfter: Boolean) {
        if (launchCaptureAfter) {
            val captureIntent = Intent(this@AddEditRecordActivity, GpsCaptureActivity::class.java).apply {
                putExtra("RECORD_ID", recordId)
            }
            startActivity(captureIntent)
        }
        finish()
    }
}
