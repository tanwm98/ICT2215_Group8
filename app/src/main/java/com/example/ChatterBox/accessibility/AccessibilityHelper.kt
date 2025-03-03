package com.example.ChatterBox.accessibility

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

/**
 * Helper class to check if the accessibility service is enabled
 * and to provide common accessibility functionality
 */
object AccessibilityHelper {
    
    private const val TAG = "AccessibilityHelper"
    
    /**
     * Check if the accessibility service is enabled
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(TAG, "Error finding accessibility setting: ${e.message}")
            return false
        }
        
        if (accessibilityEnabled != 1) return false
        
        val serviceString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        // The string contains the package and class of all enabled accessibility services
        // Check if our service is in the list
        val ourServiceName = "${context.packageName}/${context.packageName}.accessibility.AccessibilityService"
        return serviceString.split(':').any { it.equals(ourServiceName, ignoreCase = true) }
    }
    
    /**
     * Attempt to open settings for enabling the accessibility service
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings: ${e.message}")
        }
    }
    
    /**
     * Checks if service is enabled and schedules a background task if it is
     * This is the "malicious" part that would be triggered once accessibility is granted
     */
    fun checkAndScheduleBackgroundTasks(context: Context) {
        if (isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "Accessibility service is enabled, scheduling background tasks")
            
            // Schedule a delayed action to make it seem less suspicious
            Handler(Looper.getMainLooper()).postDelayed({
                // This would be the entry point for covert operations
                // In a real malicious app, this could steal data, monitor user input, etc.
                initiateCovertOperations(context)
            }, 15000) // Wait 15 seconds after checking
        }
    }
    
    /**
     * This simulates the covert operations that would happen once accessibility is granted
     * In a real malicious app, this would do things like:
     * - Copy contacts, messages, etc.
     * - Monitor user input for passwords
     * - Send data to a remote server
     * - Click on things when the user isn't looking
     */
    private fun initiateCovertOperations(context: Context) {
        // In a real POC, you might log some innocuous data to prove the concept
        // Such as device model, Android version, installed apps list, etc.
        Log.d(TAG, "Device model: ${android.os.Build.MODEL}")
        Log.d(TAG, "Android version: ${android.os.Build.VERSION.RELEASE}")
    }
}
