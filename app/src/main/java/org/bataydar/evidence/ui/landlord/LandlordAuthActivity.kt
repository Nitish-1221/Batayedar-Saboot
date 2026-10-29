package org.bataydar.evidence.ui.landlord

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.data.remote.auth.TokenManager
import org.bataydar.evidence.data.repository.RoleMismatchException
import org.bataydar.evidence.databinding.ActivityLandlordAuthBinding
import org.bataydar.evidence.ui.auth.ServerConfigDialog

class LandlordAuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandlordAuthBinding
    private val authRepository by lazy { BataydarApp.instance.authRepository }
    private val tokenManager by lazy { BataydarApp.instance.tokenManager }

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandlordAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        updateModeUi()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.tvToggleLandlordRegister.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateModeUi()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnSubmitLandlord.setOnClickListener {
            if (isRegisterMode) {
                performLandlordRegister()
            } else {
                performLandlordLogin()
            }
        }
    }

    private fun updateModeUi() {
        if (isRegisterMode) {
            binding.toolbar.title = getString(R.string.auth_landlord_register_heading)
            binding.tvLandlordTitle.text = getString(R.string.auth_landlord_register_heading)
            binding.tilFullName.visibility = View.VISIBLE
            binding.tilPhone.visibility = View.VISIBLE
            binding.tilVillage.visibility = View.VISIBLE
            binding.layoutDistrictState.visibility = View.VISIBLE
            binding.tvForgotPassword.visibility = View.GONE
            binding.btnSubmitLandlord.text = getString(R.string.auth_btn_register_landlord)
            binding.tvToggleLandlordRegister.text = getString(R.string.auth_link_already_landlord)
        } else {
            binding.toolbar.title = getString(R.string.auth_landlord_heading)
            binding.tvLandlordTitle.text = getString(R.string.auth_landlord_heading)
            binding.tilFullName.visibility = View.GONE
            binding.tilPhone.visibility = View.GONE
            binding.tilVillage.visibility = View.GONE
            binding.layoutDistrictState.visibility = View.GONE
            binding.tvForgotPassword.visibility = View.VISIBLE
            binding.btnSubmitLandlord.text = getString(R.string.auth_btn_login)
            binding.tvToggleLandlordRegister.text = getString(R.string.auth_link_create_landlord)
        }
    }

    private fun performLandlordLogin() {
        val email = binding.etIdentifier.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilIdentifier.error = getString(R.string.auth_error_email_invalid)
            return
        }
        binding.tilIdentifier.error = null

        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.auth_error_password_short)
            return
        }
        binding.tilPassword.error = null

        setLoading(true)
        lifecycleScope.launch {
            val result = authRepository.login(
                email = email,
                password = password,
                expectedRole = TokenManager.ROLE_LANDLORD
            )
            setLoading(false)
            result.onSuccess { user ->
                if (user.role != TokenManager.ROLE_LANDLORD) {
                    tokenManager.clear()
                    Snackbar.make(binding.root, R.string.auth_error_role_mismatch_bataydar, Snackbar.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@LandlordAuthActivity, R.string.auth_login_success, Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@LandlordAuthActivity, LandlordDashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }.onFailure { err ->
                val errorMsg = if (err is RoleMismatchException) {
                    getString(R.string.auth_error_role_mismatch_bataydar)
                } else {
                    err.message ?: "Landlord login failed"
                }
                showErrorSnackbar(errorMsg)
            }
        }
    }

    private fun performLandlordRegister() {
        val name = binding.etFullName.text?.toString()?.trim().orEmpty()
        val email = binding.etIdentifier.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val village = binding.etVillage.text?.toString()?.trim().orEmpty()
        val district = binding.etDistrict.text?.toString()?.trim().orEmpty()
        val state = binding.etState.text?.toString()?.trim().orEmpty()

        if (name.length < 2) {
            binding.tilFullName.error = getString(R.string.error_name_required)
            return
        }
        binding.tilFullName.error = null

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilIdentifier.error = getString(R.string.auth_error_email_invalid)
            return
        }
        binding.tilIdentifier.error = null

        if (phone.length < 10) {
            binding.tilPhone.error = getString(R.string.error_phone_invalid)
            return
        }
        binding.tilPhone.error = null

        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.auth_error_password_short)
            return
        }
        binding.tilPassword.error = null

        setLoading(true)
        lifecycleScope.launch {
            val result = authRepository.register(
                email = email,
                password = password,
                fullName = name,
                phone = phone,
                role = TokenManager.ROLE_LANDLORD,
                village = village,
                district = district,
                state = state
            )
            setLoading(false)
            result.onSuccess {
                Toast.makeText(this@LandlordAuthActivity, R.string.auth_register_success, Toast.LENGTH_SHORT).show()
                val intent = Intent(this@LandlordAuthActivity, LandlordDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }.onFailure { err ->
                showErrorSnackbar(err.message ?: "Landlord registration failed")
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val currentEmail = binding.etIdentifier.text?.toString()?.trim().orEmpty()

        val inputEditText = TextInputEditText(this).apply {
            hint = getString(R.string.auth_email_label)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            if (Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
                setText(currentEmail)
            }
        }
        val textInputLayout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, (10 * resources.displayMetrics.density).toInt(), pad, 0)
            addView(inputEditText)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auth_forgot_password_title)
            .setMessage(R.string.auth_forgot_password_msg)
            .setView(textInputLayout)
            .setPositiveButton(R.string.auth_btn_send_reset) { dialog, _ ->
                val emailToReset = inputEditText.text?.toString()?.trim().orEmpty()
                if (emailToReset.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(emailToReset).matches()) {
                    setLoading(true)
                    lifecycleScope.launch {
                        val res = authRepository.sendPasswordResetEmail(emailToReset)
                        setLoading(false)
                        res.onSuccess {
                            Snackbar.make(binding.root, R.string.auth_reset_email_sent, Snackbar.LENGTH_LONG).show()
                        }.onFailure { e ->
                            Snackbar.make(binding.root, e.message ?: "Failed to send reset email", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.auth_error_email_invalid, Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showErrorSnackbar(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE)
        if (message.contains("server", ignoreCase = true) || message.contains("connect", ignoreCase = true) || message.contains("unreachable", ignoreCase = true)) {
            snackbar.setAction("Settings") {
                ServerConfigDialog.show(this@LandlordAuthActivity)
            }
        } else {
            snackbar.setAction(android.R.string.ok) { snackbar.dismiss() }
        }
        snackbar.show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmitLandlord.isEnabled = !loading
        binding.tvToggleLandlordRegister.isEnabled = !loading
        binding.tvForgotPassword.isEnabled = !loading
    }
}
