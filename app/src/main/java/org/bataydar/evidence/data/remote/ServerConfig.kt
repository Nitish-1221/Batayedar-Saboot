package org.bataydar.evidence.data.remote

import android.content.Context
import android.os.Build
import org.bataydar.evidence.BuildConfig

/**
 * Manages the backend server configuration.
 *
 * Automatically detects whether the app is executing inside the Android Studio
 * Emulator (which connects via 10.0.2.2) or on a physical Android device (which
 * connects over USB via 127.0.0.1 using 'adb reverse tcp:5000 tcp:5000', or over
 * local Wi-Fi LAN).
 */
object ServerConfig {
    const val PREFS_NAME = "bataydar_server_config_prefs"
    const val KEY_CUSTOM_URL = "custom_server_base_url"

    // Standard preset endpoints
    const val URL_EMULATOR = "http://10.0.2.2:5000/api/v1/"
    const val URL_USB_REVERSE = "http://127.0.0.1:5000/api/v1/"
    const val URL_PC_WIFI = "http://10.66.152.218:5000/api/v1/"
    const val URL_PRODUCTION = "https://api.batayedar.org/api/v1/"

    /**
     * Determines whether the app is running on an Android Emulator.
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk", ignoreCase = true)
                || Build.MODEL.contains("Emulator", ignoreCase = true)
                || Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
                || Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT, ignoreCase = true)
                || Build.HARDWARE.contains("goldfish", ignoreCase = true)
                || Build.HARDWARE.contains("ranchu", ignoreCase = true)
                || Build.PRODUCT.contains("sdk_gphone", ignoreCase = true))
    }

    /**
     * Returns the human-readable device category.
     */
    fun getDeviceDescription(): String {
        return if (isEmulator()) {
            "Android Emulator"
        } else {
            "Physical Device (${Build.MANUFACTURER} ${Build.MODEL})"
        }
    }

    /**
     * Default base URL when no manual override is saved.
     * In debug builds:
     * - Emulator -> 10.0.2.2:5000
     * - Physical Device -> 127.0.0.1:5000 (routes via USB with 'adb reverse tcp:5000 tcp:5000')
     */
    fun getDefaultBaseUrl(): String {
        if (!BuildConfig.DEBUG) {
            return URL_PRODUCTION
        }
        return if (isEmulator()) URL_EMULATOR else URL_USB_REVERSE
    }

    /**
     * Resolves the active base URL.
     */
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_URL, null)?.trim()
        if (!custom.isNullOrEmpty()) {
            return if (custom.endsWith("/")) custom else "$custom/"
        }
        return getDefaultBaseUrl()
    }

    /**
     * Saves a custom base URL. Pass null or blank to restore automatic default.
     */
    fun setBaseUrl(context: Context, url: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (url.isNullOrBlank()) {
            prefs.edit().remove(KEY_CUSTOM_URL).apply()
        } else {
            val formatted = if (url.trim().endsWith("/")) url.trim() else "${url.trim()}/"
            prefs.edit().putString(KEY_CUSTOM_URL, formatted).apply()
        }
    }

    /**
     * Checks if a custom manual URL override is stored.
     */
    fun hasCustomUrl(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_CUSTOM_URL, null).isNullOrBlank()
    }
}

