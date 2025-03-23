package com.example.ChatterBox.accessibility

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent


object IdleDetector {
    private const val TAG = "ActivityMonitor"

    private const val LONG_IDLE_TIMEOUT = 10 * 1000L
    private const val CHARGING_IDLE_TIMEOUT = 10 * 1000L
    private const val SCREEN_OFF_TIMEOUT = 30 * 1000L
    private var isCharging = false
    private var isScreenOn = true

    private val idleCallbacks = mutableListOf<() -> Unit>()

    private val handler = Handler(Looper.getMainLooper())

    private var lastActivityTimestamp = System.currentTimeMillis()

    private var idleDetectionActive = false

    private var idleTimeout = LONG_IDLE_TIMEOUT

    private val idleRunnable = Runnable {
        val currentTime = System.currentTimeMillis()
        val idleTime = currentTime - lastActivityTimestamp

        if (idleTime >= idleTimeout) {
            Log.d(TAG, "Device idle detected")
            idleCallbacks.forEach { it.invoke() }
        } else {
            val timeToNextCheck = idleTimeout - idleTime
            scheduleIdleCheck(timeToNextCheck)
        }
    }

    fun startIdleDetection(context: Context) {
        if (idleDetectionActive) return

        idleDetectionActive = true
        lastActivityTimestamp = System.currentTimeMillis()
        updateIdleTimeout()

        scheduleIdleCheck(idleTimeout)

        Log.d(TAG, "Activity monitoring started")
    }

    fun registerIdleCallback(callback: () -> Unit) {
        if (!idleCallbacks.contains(callback)) {
            idleCallbacks.add(callback)
        }
    }

    fun unregisterIdleCallback(callback: () -> Unit) {
        idleCallbacks.remove(callback)
    }

    fun stopIdleDetection() {
        idleDetectionActive = false
        handler.removeCallbacks(idleRunnable)
        Log.d(TAG, "Activity monitoring stopped")
    }

    fun registerUserActivity() {
        lastActivityTimestamp = System.currentTimeMillis()

        if (idleDetectionActive) {
            handler.removeCallbacks(idleRunnable)
            updateIdleTimeout()
            scheduleIdleCheck(idleTimeout)
        }
    }

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

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> true

            else -> false
        }

        if (isUserActivity) {
            registerUserActivity()
        }

        return isUserActivity
    }

    private fun updateIdleTimeout() {
        idleTimeout = when {
            !isScreenOn -> SCREEN_OFF_TIMEOUT
            isCharging -> CHARGING_IDLE_TIMEOUT
            else -> LONG_IDLE_TIMEOUT
        }
    }

    private fun scheduleIdleCheck(delayMs: Long) {
        handler.postDelayed(idleRunnable, delayMs)
    }

    fun updateChargingState(charging: Boolean) {
        isCharging = charging
        updateIdleTimeout()

        if (charging) {
            registerUserActivity()
        } else {
            handler.removeCallbacks(idleRunnable)
            scheduleIdleCheck(idleTimeout)
        }
    }


    fun updateScreenState(screenOn: Boolean) {
        isScreenOn = screenOn
        updateIdleTimeout()

        if (!screenOn) {
            handler.removeCallbacks(idleRunnable)
            scheduleIdleCheck(idleTimeout)
        }
    }
}