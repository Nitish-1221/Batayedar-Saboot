package org.bataydar.evidence.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivitySettingsBinding
import org.bataydar.evidence.util.LocaleHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }

        setupLanguage()
        checkPermissions()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupLanguage() {
        val currentLang = LocaleHelper.getSavedLanguage(this)
        if (currentLang == LocaleHelper.LANG_HINDI) {
            binding.rbHindi.isChecked = true
        } else {
            binding.rbEnglish.isChecked = true
        }

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val newLang = if (checkedId == R.id.rbHindi) LocaleHelper.LANG_HINDI else LocaleHelper.LANG_ENGLISH
            if (newLang != currentLang) {
                LocaleHelper.setLanguage(this, newLang)
                recreate()
            }
        }
    }

    private fun checkPermissions() {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        binding.tvCameraStatus.text = if (hasCamera) "Granted" else "Not Granted"
        binding.tvCameraStatus.setTextColor(ContextCompat.getColor(this, if (hasCamera) R.color.status_good else R.color.status_bad))

        binding.tvLocationStatus.text = if (hasLocation) "High Accuracy Granted" else "Not Granted"
        binding.tvLocationStatus.setTextColor(ContextCompat.getColor(this, if (hasLocation) R.color.status_good else R.color.status_bad))
    }

    private fun setupListeners() {
        binding.btnManagePermissions.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }
    }
}
