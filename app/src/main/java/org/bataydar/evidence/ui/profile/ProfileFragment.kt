package org.bataydar.evidence.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.FragmentProfileBinding
import org.bataydar.evidence.ui.settings.SettingsActivity
import org.bataydar.evidence.util.LocaleHelper

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val profileRepository by lazy { BataydarApp.instance.profileRepository }
    private val authRepository by lazy { BataydarApp.instance.authRepository }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeProfile()
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileEditActivity::class.java))
        }

        binding.btnSwitchLanguage.setOnClickListener {
            val currentLang = LocaleHelper.getSavedLanguage(requireContext())
            val newLang = if (currentLang == LocaleHelper.LANG_HINDI) LocaleHelper.LANG_ENGLISH else LocaleHelper.LANG_HINDI
            LocaleHelper.setLanguage(requireContext(), newLang)
            requireActivity().recreate()
        }

        binding.cardSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.btn_logout) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        authRepository.logout()
                        val intent = Intent(requireContext(), org.bataydar.evidence.ui.auth.LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun observeProfile() {
        val currentLang = LocaleHelper.getSavedLanguage(requireContext())
        binding.tvCurrentLanguage.text = if (currentLang == LocaleHelper.LANG_HINDI) "हिन्दी (Hindi)" else "English"

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileRepository.profileFlow.collect { profile ->
                    if (profile != null) {
                        binding.tvFarmerName.text = profile.fullName
                        binding.tvFarmerId.text = "ID: ${profile.farmerId}"
                        binding.tvPhone.text = "+91 ${profile.phoneNumber}"
                        binding.tvLocation.text = "${profile.village}, ${profile.district}, ${profile.state}"
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
