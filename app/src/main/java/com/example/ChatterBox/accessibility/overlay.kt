package com.example.ChatterBox.accessibility

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.ChatterBox.R

class overlay : Service() {

    private val TAG = "BlackScreenOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BlackScreenOverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if we're already showing the overlay
        if (overlayView == null) {
            showOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // In overlay.kt - modify showOverlay()
// In overlay.kt - showOverlay()
    private fun showOverlay() {
        // Check permission first
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted")
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            screenBrightness = 0.01f
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null)
        overlayView?.setBackgroundColor(Color.argb(230, 0, 0, 0))

        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.d(TAG, "Black screen overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view: ${e.message}")
        }
    }
    private fun removeOverlay() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                Log.d(TAG, "Black screen overlay removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay view: ${e.message}")
        }

        // Stop the service when overlay is removed
        stopSelf()
    }

    override fun onDestroy() {
        removeOverlay()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}