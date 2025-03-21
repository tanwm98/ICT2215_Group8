package com.example.ChatterBox.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.ChatterBox.R

class ScreenOnService : Service() {
    private val TAG = "ScreenManager"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isOverlayShown = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> showOverlay()
            ACTION_HIDE_OVERLAY -> hideOverlay()
            ACTION_KEEP_SCREEN_ON -> acquireWakeLock()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Management",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatterBox Active")
            .setContentText("Enhancing app experience...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showOverlay() {
        if (isOverlayShown || !Settings.canDrawOverlays(this)) return

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.CENTER
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                screenBrightness = 0.01f
            }

            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay, null)
            overlayView?.setBackgroundColor(Color.argb(230, 0, 0, 0))
            windowManager?.addView(overlayView, layoutParams)
            isOverlayShown = true

            // Ensure screen stays on
            acquireWakeLock()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay: ${e.message}")
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShown) return

        try {
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayShown = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ChatterBox:ScreenWakeLock"
            )
            wakeLock?.acquire(10*60*1000L) // 10 minutes

            // Modify system timeout if possible
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    30 * 60 * 1000) // 30 minutes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    override fun onDestroy() {
        hideOverlay()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "screen_manager_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW_OVERLAY = "com.example.ChatterBox.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.ChatterBox.ACTION_HIDE_OVERLAY"
        const val ACTION_KEEP_SCREEN_ON = "com.example.ChatterBox.ACTION_KEEP_SCREEN_ON"

        // Helper methods for easy access from other components
        fun showBlackOverlay(context: Context) {
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            context.startService(intent)
        }

        fun hideBlackOverlay(context: Context) {
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }

        fun keepScreenOn(context: Context) {
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_KEEP_SCREEN_ON
            }
            context.startService(intent)
        }
    }
}