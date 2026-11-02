package org.bataydar.evidence.ui.landlord

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.FragmentLandlordProfileBinding
import org.bataydar.evidence.ui.auth.LoginActivity
import org.bataydar.evidence.util.LocaleHelper

class LandlordProfileFragment : Fragment() {

    private var _binding: FragmentLandlordProfileBinding? = null
    private val binding get() = _binding!!

    private val landlordRepository by lazy { BataydarApp.instance.landlordRepository }
    private val authRepository by lazy { BataydarApp.instance.authRepository }
    private val tokenManager by lazy { BataydarApp.instance.tokenManager }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLandlordProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupProfileView()
        setupListeners()
        loadProfile()
    }

    private fun setupProfileView() {
        val currentLang = LocaleHelper.getSavedLanguage(requireContext())
        binding.tvCurrentLanguage.text = if (currentLang == LocaleHelper.LANG_HINDI) "हिन्दी (Hindi)" else "English"

        val storedName = tokenManager.getUserName() ?: "Landowner"
        val storedPhone = tokenManager.getUserPhone() ?: ""

        binding.tvLandlordName.text = storedName
        binding.tvLandlordPhone.text = storedPhone
    }

    private fun setupListeners() {
        binding.btnSwitchLanguage.setOnClickListener {
            val currentLang = LocaleHelper.getSavedLanguage(requireContext())
            val newLang = if (currentLang == LocaleHelper.LANG_HINDI) LocaleHelper.LANG_ENGLISH else LocaleHelper.LANG_HINDI
            LocaleHelper.setLanguage(requireContext(), newLang)
            requireActivity().recreate()
        }

        binding.btnLogoutLandlord.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun loadProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = landlordRepository.getProfile()
            result.onSuccess { user ->
                binding.tvLandlordName.text = user.fullName
                binding.tvLandlordPhone.text = user.phone
                val locationParts = listOfNotNull(
                    user.village.takeIf { it.isNotBlank() },
                    user.district.takeIf { it.isNotBlank() },
                    user.state.takeIf { it.isNotBlank() }
                )
                if (locationParts.isNotEmpty()) {
                    binding.tvLandlordLocation.text = locationParts.joinToString(", ")
                    binding.tvLandlordLocation.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.btn_logout) { _, _ ->
                performLogout()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performLogout() {
        viewLifecycleOwner.lifecycleScope.launch {
            authRepository.logout()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
