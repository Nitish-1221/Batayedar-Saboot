package org.bataydar.evidence.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivityProfileEditBinding
import org.bataydar.evidence.ui.main.MainActivity
import org.bataydar.evidence.util.LocaleHelper

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private var currentFarmerId: String? = null
    private var isInitialSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isInitialSetup = intent.getBooleanExtra("IS_INITIAL_SETUP", false)

        supportActionBar?.apply {
            title = getString(if (isInitialSetup) R.string.profile_title else R.string.profile_edit_title)
            setDisplayHomeAsUpEnabled(!isInitialSetup)
        }

        loadProfileData()
        setupSaveButton()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadProfileData() {
        lifecycleScope.launch {
            val profile = profileRepository.getProfile()
            if (profile != null) {
                currentFarmerId = profile.farmerId
                binding.tvFarmerIdDisplay.text = "ID: ${profile.farmerId}"
                binding.etFullName.setText(profile.fullName)
                binding.etPhone.setText(profile.phoneNumber)
                binding.etVillage.setText(profile.village)
                binding.etDistrict.setText(profile.district)
                binding.etState.setText(profile.state)

                if (profile.preferredLanguage == LocaleHelper.LANG_HINDI) {
                    binding.rbHindi.isChecked = true
                } else {
                    binding.rbEnglish.isChecked = true
                }
            } else {
                currentFarmerId = profileRepository.generateFarmerId()
                binding.tvFarmerIdDisplay.text = "New ID: $currentFarmerId"
                binding.etState.setText("Uttar Pradesh") // Default helpful suggestion
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveProfile.setOnClickListener {
            val fullName = binding.etFullName.text?.toString().orEmpty()
            val phone = binding.etPhone.text?.toString().orEmpty()
            val village = binding.etVillage.text?.toString().orEmpty()
            val district = binding.etDistrict.text?.toString().orEmpty()
            val state = binding.etState.text?.toString().orEmpty()
            val language = if (binding.rbHindi.isChecked) LocaleHelper.LANG_HINDI else LocaleHelper.LANG_ENGLISH

            var isValid = true

            if (fullName.trim().isEmpty()) {
                binding.tilFullName.error = getString(R.string.error_name_required)
                isValid = false
            } else {
                binding.tilFullName.error = null
            }

            val cleanPhone = phone.trim().replace("\\s+".toRegex(), "")
            if (cleanPhone.length < 10 || !cleanPhone.all { it.isDigit() }) {
                binding.tilPhone.error = getString(R.string.error_phone_invalid)
                isValid = false
            } else {
                binding.tilPhone.error = null
            }

            if (village.trim().isEmpty()) {
                binding.tilVillage.error = getString(R.string.error_village_required)
                isValid = false
            } else {
                binding.tilVillage.error = null
            }

            if (district.trim().isEmpty()) {
                binding.tilDistrict.error = getString(R.string.error_district_required)
                isValid = false
            } else {
                binding.tilDistrict.error = null
            }

            if (state.trim().isEmpty()) {
                binding.tilState.error = getString(R.string.error_state_required)
                isValid = false
            } else {
                binding.tilState.error = null
            }

            if (!isValid) return@setOnClickListener

            lifecycleScope.launch {
                val result = profileRepository.saveProfile(
                    existingId = currentFarmerId,
                    fullName = fullName,
                    phoneNumber = cleanPhone,
                    village = village,
                    district = district,
                    state = state,
                    preferredLanguage = language
                )

                if (result.isSuccess) {
                    LocaleHelper.setLanguage(this@ProfileEditActivity, language)
                    Toast.makeText(this@ProfileEditActivity, R.string.profile_saved_success, Toast.LENGTH_SHORT).show()

                    if (isInitialSetup) {
                        startActivity(Intent(this@ProfileEditActivity, MainActivity::class.java))
                    }
                    finish()
                } else {
                    Toast.makeText(this@ProfileEditActivity, result.exceptionOrNull()?.message ?: "Error saving profile", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
