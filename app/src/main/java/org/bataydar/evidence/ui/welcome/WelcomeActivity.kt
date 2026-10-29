package org.bataydar.evidence.ui.welcome

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivityWelcomeBinding
import org.bataydar.evidence.ui.main.MainActivity
import org.bataydar.evidence.ui.profile.ProfileEditActivity
import org.bataydar.evidence.util.LocaleHelper

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private val profileRepository by lazy { BataydarApp.instance.profileRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageToggle()
        checkExistingProfile()

        binding.btnGetStarted.setOnClickListener {
            lifecycleScope.launch {
                val profile = profileRepository.getProfile()
                if (profile != null) {
                    startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                } else {
                    val intent = Intent(this@WelcomeActivity, org.bataydar.evidence.ui.auth.LoginActivity::class.java)
                    startActivity(intent)
                }
                finish()
            }
        }
    }

    private fun checkExistingProfile() {
        lifecycleScope.launch {
            val profile = profileRepository.getProfile()
            if (profile != null) {
                binding.btnGetStarted.text = getString(R.string.btn_already_registered)
            }
        }
    }

    private fun setupLanguageToggle() {
        val currentLang = LocaleHelper.getSavedLanguage(this)
        binding.btnLanguageToggle.text = if (currentLang == LocaleHelper.LANG_HINDI) "English" else "हिन्दी"

        binding.btnLanguageToggle.setOnClickListener {
            val newLang = if (currentLang == LocaleHelper.LANG_HINDI) {
                LocaleHelper.LANG_ENGLISH
            } else {
                LocaleHelper.LANG_HINDI
            }
            LocaleHelper.setLanguage(this, newLang)
            recreate()
        }
    }
}
