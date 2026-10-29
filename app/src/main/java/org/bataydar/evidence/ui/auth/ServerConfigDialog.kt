package org.bataydar.evidence.ui.auth

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bataydar.evidence.data.remote.ServerConfig
import java.util.concurrent.TimeUnit

object ServerConfigDialog {

    fun show(activity: Activity, onSaved: ((String) -> Unit)? = null) {
        val currentUrl = ServerConfig.getBaseUrl(activity)
        val density = activity.resources.displayMetrics.density

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, (12 * density).toInt(), pad, 0)
        }

        // Device Mode Indicator
        val tvDeviceInfo = TextView(activity).apply {
            text = "Device Mode: ${ServerConfig.getDeviceDescription()}"
            textSize = 13f
            setTextColor(Color.parseColor("#2E7D32"))
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        container.addView(tvDeviceInfo)

        // Presets Radio Group
        val radioGroup = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
        }

        val rbRender = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "Cloud Server (Render - Live)\n${ServerConfig.URL_RENDER}"
            textSize = 13f
        }
        val rbUsb = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "USB Cable (adb reverse)\n${ServerConfig.URL_USB_REVERSE}"
            textSize = 13f
        }
        val rbWifi = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "PC Wi-Fi LAN\n${ServerConfig.URL_PC_WIFI}"
            textSize = 13f
        }
        val rbEmu = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "Android Studio Emulator\n${ServerConfig.URL_EMULATOR}"
            textSize = 13f
        }
        val rbCustom = RadioButton(activity).apply {
            id = View.generateViewId()
            text = "Custom Server URL"
            textSize = 13f
        }

        radioGroup.addView(rbRender)
        radioGroup.addView(rbUsb)
        radioGroup.addView(rbWifi)
        radioGroup.addView(rbEmu)
        radioGroup.addView(rbCustom)
        container.addView(radioGroup)

        // Custom URL input
        val etCustomUrl = EditText(activity).apply {
            hint = "https://<host>/api/v1/"
            textSize = 14f
            setText(currentUrl)
            visibility = View.GONE
        }
        container.addView(etCustomUrl)

        // Pre-select based on current URL
        when (currentUrl) {
            ServerConfig.URL_RENDER -> rbRender.isChecked = true
            ServerConfig.URL_USB_REVERSE -> rbUsb.isChecked = true
            ServerConfig.URL_PC_WIFI -> rbWifi.isChecked = true
            ServerConfig.URL_EMULATOR -> rbEmu.isChecked = true
            else -> {
                rbCustom.isChecked = true
                etCustomUrl.visibility = View.VISIBLE
            }
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbCustom.id) {
                etCustomUrl.visibility = View.VISIBLE
                etCustomUrl.requestFocus()
            } else {
                etCustomUrl.visibility = View.GONE
            }
        }

        // Test status text
        val tvTestStatus = TextView(activity).apply {
            textSize = 12f
            setPadding(0, (10 * density).toInt(), 0, (6 * density).toInt())
            visibility = View.GONE
        }
        container.addView(tvTestStatus)

        // Test Connection Button
        val btnTest = Button(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Test Connection"
            textSize = 13f
        }
        container.addView(btnTest)

        fun getSelectedUrl(): String {
            return when (radioGroup.checkedRadioButtonId) {
                rbRender.id -> ServerConfig.URL_RENDER
                rbUsb.id -> ServerConfig.URL_USB_REVERSE
                rbWifi.id -> ServerConfig.URL_PC_WIFI
                rbEmu.id -> ServerConfig.URL_EMULATOR
                else -> {
                    val raw = etCustomUrl.text.toString().trim()
                    if (raw.isNotEmpty()) {
                        if (raw.endsWith("/")) raw else "$raw/"
                    } else {
                        ServerConfig.getDefaultBaseUrl()
                    }
                }
            }
        }

        btnTest.setOnClickListener {
            val testTarget = getSelectedUrl()
            val healthUrl = if (testTarget.endsWith("/")) "${testTarget}health" else "$testTarget/health"
            tvTestStatus.visibility = View.VISIBLE
            tvTestStatus.setTextColor(Color.parseColor("#555555"))
            tvTestStatus.text = "Testing connection to $healthUrl..."
            btnTest.isEnabled = false

            CoroutineScope(Dispatchers.Main).launch {
                val okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

                val result = withContext(Dispatchers.IO) {
                    try {
                        val req = Request.Builder().url(healthUrl).build()
                        val resp = okHttpClient.newCall(req).execute()
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful && body.contains("ok")) {
                            Pair(true, "✓ Connected! Backend server online & MongoDB active.")
                        } else {
                            Pair(false, "Server responded with HTTP ${resp.code}")
                        }
                    } catch (e: Exception) {
                        Pair(false, "Connection failed: ${e.message ?: e.javaClass.simpleName}")
                    }
                }

                btnTest.isEnabled = true
                if (result.first) {
                    tvTestStatus.setTextColor(Color.parseColor("#2E7D32"))
                    tvTestStatus.text = result.second
                } else {
                    tvTestStatus.setTextColor(Color.parseColor("#C62828"))
                    tvTestStatus.text = "${result.second}\n\nTip: If using USB cable, run in terminal:\nadb reverse tcp:5000 tcp:5000"
                }
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Backend Server Settings")
            .setMessage("Select or enter the Node.js API server endpoint:")
            .setView(container)
            .setPositiveButton("Save & Connect") { dialog, _ ->
                val chosen = getSelectedUrl()
                ServerConfig.setBaseUrl(activity, chosen)
                Toast.makeText(activity, "Server set to: $chosen", Toast.LENGTH_SHORT).show()
                onSaved?.invoke(chosen)
                dialog.dismiss()
            }
            .setNeutralButton("Reset Default") { dialog, _ ->
                ServerConfig.setBaseUrl(activity, null)
                val def = ServerConfig.getDefaultBaseUrl()
                Toast.makeText(activity, "Reset to default: $def", Toast.LENGTH_SHORT).show()
                onSaved?.invoke(def)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
