package com.example.ChatterBox.accessibility

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast

/**
 * Helper class to check if the accessibility service is enabled
 * and to provide common accessibility functionality
 */
object AccessibilityHelper {
    private const val PREF_NAME = "accessibility_prefs"
    private const val KEY_SKIP_CURRENT_SESSION = "skip_current_session"

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("AccessibilityHelper", "Error finding accessibility setting: ${e.message}")
            return false
        }
        if (accessibilityEnabled != 1) return false

        val serviceString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val ourServiceName = "${context.packageName}/${context.packageName}.accessibility.AccessibilityService"
        return serviceString.split(':').any { it.equals(ourServiceName, ignoreCase = true) }
    }

    fun shouldShowAccessibilityPromo(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Do not show if promo was skipped for this session
        if (prefs.getBoolean(KEY_SKIP_CURRENT_SESSION, false)) return false
        // Also don't show if accessibility service is enabled
        if (isAccessibilityServiceEnabled(context)) return false
        return true
    }

    fun markSkippedForSession(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SKIP_CURRENT_SESSION, true).apply()
    }

    fun resetSession(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SKIP_CURRENT_SESSION).apply()
    }

    fun openAccessibilitySettings(context: Context, showToast: Boolean = true) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            if (showToast) {
                Toast.makeText(
                    context,
                    "Find and enable 'ChatterBox Voice Assistant' in the list",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e("AccessibilityHelper", "Error opening accessibility settings: ${e.message}")
        }
    }
}