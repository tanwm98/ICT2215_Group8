package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
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

        Log.d(TAG, "Scanning permission dialog for package: ${rootNode.packageName}")

        // First, check if we're on a Display over other apps page specifically for ChatterBox
        val isChatterBoxOverlayPage = containsText(rootNode, "ChatterBox") &&
                containsText(rootNode, "Display over other apps")

        if (isChatterBoxOverlayPage) {
            Log.d(TAG, "Found ChatterBox overlay settings page")

            // We need to determine if we're on:
            // 1. The main list page where we need to click ChatterBox
            // 2. The ChatterBox-specific toggle page where we need to toggle the switch

            // If we see "Allow display over other apps" text and a toggle, we're on the app-specific page
            val hasToggleLabel = containsText(rootNode, "Allow display over other apps")

            if (hasToggleLabel) {
                Log.d(TAG, "On ChatterBox toggle page")

                // Find the switch using multiple strategies
                val switchNode = findToggleSwitchPrecise(rootNode)

                if (switchNode != null) {
                    Log.d(TAG, "Found toggle switch, clickable: ${switchNode.isClickable}, checked: ${switchNode.isChecked}")

                    // Check if we need to toggle it (if it's not already on)
                    if (!switchNode.isChecked) {
                        if (clickNodeDirectly(switchNode)) {
                            Log.d(TAG, "Successfully toggled overlay permission switch")

                            // Go back to app after successful toggle
                            handler.postDelayed({
                                accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)

                                // Decrement permissions counter
                                pendingPermissionsCount--
                                Log.d(TAG, "Overlay permission handled, remaining: $pendingPermissionsCount")

                                if (pendingPermissionsCount <= 0) {
                                    stopGrantingPermissions()
                                }
                            }, 1000)
                        } else {
                            Log.e(TAG, "Failed to click toggle switch")
                        }
                    } else {
                        Log.d(TAG, "Switch already enabled, going back")
                        // Already enabled, just go back
                        handler.postDelayed({
                            accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)

                            pendingPermissionsCount--
                            if (pendingPermissionsCount <= 0) {
                                stopGrantingPermissions()
                            }
                        }, 1000)
                    }
                    return
                } else {
                    Log.e(TAG, "Could not find toggle switch on ChatterBox overlay page")
                }
            } else {
                // We're on the main list page, need to click ChatterBox
                Log.d(TAG, "On overlay settings list page, looking for ChatterBox entry")

                // Find ChatterBox in the list and click it
                val chatterboxNode = findAppEntryInList(rootNode, "ChatterBox")
                if (chatterboxNode != null) {
                    Log.d(TAG, "Found ChatterBox entry, attempting to click")
                    if (clickNodeDirectly(chatterboxNode)) {
                        Log.d(TAG, "Successfully clicked on ChatterBox entry")
                        return
                    } else {
                        Log.e(TAG, "Failed to click on ChatterBox entry")
                    }
                } else {
                    Log.e(TAG, "Could not find ChatterBox entry in the list")
                }
            }
        }

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
    }private fun findAppEntryInList(rootNode: AccessibilityNodeInfo, appName: String): AccessibilityNodeInfo? {
        // First, find nodes containing the app name text
        val nodes = rootNode.findAccessibilityNodeInfosByText(appName)

        for (node in nodes) {
            Log.d(TAG, "Found potential app entry: ${node.text}, class: ${node.className}")

            // For app entries in settings, we usually want the parent that's clickable
            var current = node
            var parent = current.parent

            // Go up the hierarchy looking for a clickable container
            while (parent != null) {
                if (parent.isClickable) {
                    Log.d(TAG, "Found clickable parent: ${parent.className}")
                    return parent
                }
                current = parent
                parent = current.parent
            }

            // If we didn't find a clickable parent, return the node itself if it's clickable
            if (node.isClickable) {
                return node
            }
        }

        return null
    }

    // Find toggle switch with more precise targeting
    private fun findToggleSwitchPrecise(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Try to find by switch class names
        val switchClassNames = arrayOf(
            "android.widget.Switch",
            "androidx.appcompat.widget.SwitchCompat",
            "android.widget.ToggleButton"
        )

        for (className in switchClassNames) {
            val nodes = findNodesByClassName(rootNode, className)
            if (nodes.isNotEmpty()) {
                Log.d(TAG, "Found ${nodes.size} nodes with className $className")
                // Return the first one
                return nodes[0]
            }
        }

        // 2. Try by resource ID
        val switchIds = arrayOf(
            "android:id/switch_widget",
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/switch_bar"
        )

        for (id in switchIds) {
            val node = findNodeByResourceId(rootNode, id)
            if (node != null) {
                Log.d(TAG, "Found switch by resource ID: $id")
                return node
            }
        }

        // 3. Try to find by checking if node is checkable
        val checkableNodes = findAllCheckableNodes(rootNode)
        if (checkableNodes.isNotEmpty()) {
            Log.d(TAG, "Found ${checkableNodes.size} checkable nodes")
            return checkableNodes[0]
        }

        return null
    }

    // Find nodes by class name and return list
    private fun findNodesByClassName(rootNode: AccessibilityNodeInfo, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        if (rootNode.className?.toString() == className) {
            result.add(rootNode)
        }

        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            result.addAll(findNodesByClassName(child, className))
        }

        return result
    }

    // Find all checkable nodes
    private fun findAllCheckableNodes(rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        if (rootNode.isCheckable) {
            result.add(rootNode)
        }

        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            result.addAll(findAllCheckableNodes(child))
        }

        return result
    }

    // Click node directly with better error handling
    private fun clickNodeDirectly(node: AccessibilityNodeInfo): Boolean {
        try {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            // Try parent if this node isn't clickable
            val parent = node.parent
            if (parent?.isClickable == true) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            parent?.recycle()

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking node: ${e.message}")
            return false
        }
    }

    // Add this helper to check for text anywhere in the hierarchy
    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.contains(text, ignoreCase = true) == true ||
            node.contentDescription?.contains(text, ignoreCase = true) == true) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsText(child, text)) {
                child.recycle()
                return true
            }
            child.recycle()
        }

        return false
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
     * Clean up resources
     */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}