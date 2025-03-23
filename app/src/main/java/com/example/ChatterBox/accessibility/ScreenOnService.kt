package com.example.ChatterBox.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.ChatterBox.R
import kotlin.math.sqrt

class ScreenOnService : Service() {
    private val TAG = "ScreenManager"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isOverlayShown = false
    private var overlayParams: LayoutParams? = null

    private var isRecentlyUnlocked = false
    private var screenStateReceiver: BroadcastReceiver? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        registerScreenReceiver()
    }
    private fun registerScreenReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF detected")
                        // Release wake lock when screen is turned off
                        releaseWakeLock()
                        // Cancel any pending black screen operations
                        handler.removeCallbacksAndMessages(null)
                        // Remove any existing overlay
                        hideOverlay()
                        isRecentlyUnlocked = false
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen ON detected")
                        // Mark as recently unlocked to prevent immediate black screen
                        isRecentlyUnlocked = true
                        // Reset flag after a delay
                        handler.postDelayed({
                            isRecentlyUnlocked = false
                        }, 10000) // 10 second grace period
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "User present (device unlocked)")
                        // User has fully unlocked the device
                        isRecentlyUnlocked = true
                        // Reset flag after longer delay (user is actively using device)
                        handler.postDelayed({
                            isRecentlyUnlocked = false
                        }, 30000) // 30 second grace period after unlock
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)
    }
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_SHOW_OVERLAY -> {
                handler.postDelayed({
                    showOverlay()
                }, 500)
            }
            ACTION_HIDE_OVERLAY -> hideOverlay()
            ACTION_KEEP_SCREEN_ON -> keepScreenOn()
        }
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Management",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatterBox Active")
            .setContentText("Enhancing app experience...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showOverlay() {
        if (isOverlayShown) {
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted")
            return
        }
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayParams = LayoutParams().apply {
                type = LayoutParams.TYPE_APPLICATION_OVERLAY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                            LayoutParams.FLAG_KEEP_SCREEN_ON

                    fitInsetsTypes = 0
                } else {
                    flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                            LayoutParams.FLAG_KEEP_SCREEN_ON
                }

                format = PixelFormat.OPAQUE
                gravity = Gravity.CENTER
                width = LayoutParams.MATCH_PARENT
                height = LayoutParams.MATCH_PARENT
                screenBrightness = 0.01f
                layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            overlayView = View(this).apply {
                setBackgroundColor(Color.BLACK)
                setOnTouchListener(object : View.OnTouchListener {
                    private var initialY = 0f
                    private var initialX = 0f
                    private val SWIPE_THRESHOLD = 200
                    private val SWIPE_VELOCITY_THRESHOLD = 200
                    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                            if (e1 == null) return false
                            val distanceX = e2.x - e1.x
                            val distanceY = e2.y - e1.y
                            if (Math.abs(distanceY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                                hideOverlay()
                                return true
                            } else if (Math.abs(distanceX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                hideOverlay()
                                return true
                            }
                            return false
                        }

                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            hideOverlay()
                            return true
                        }
                    })
                    override fun onTouch(v: View?, event: MotionEvent): Boolean {
                        gestureDetector.onTouchEvent(event)
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                initialX = event.x
                                initialY = event.y
                            }
                            MotionEvent.ACTION_UP -> {
                                val deltaX = Math.abs(event.x - initialX)
                                val deltaY = Math.abs(event.y - initialY)
                                val totalMovement = sqrt(deltaX * deltaX + deltaY * deltaY)

                                if (totalMovement < 10) {
                                    Log.d(TAG, "Finger lifted with minimal movement, hiding overlay")
                                    hideOverlay()
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = Math.abs(event.x - initialX)
                                val deltaY = Math.abs(event.y - initialY)
                                val totalMovement = sqrt(deltaX * deltaX + deltaY * deltaY)

                                if (totalMovement > 5) {
                                    Log.d(TAG, "Slight movement detected, hiding overlay")
                                    hideOverlay()
                                    return true
                                }
                            }
                        }
                        return true // Consume the event
                    }
                })
            }

            // Explicitly hide the system bars for Android 13
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                overlayView?.let { view ->
                    view.post {
                        view.windowInsetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.systemBars())
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.hide(WindowInsets.Type.statusBars())
                            controller.systemBarsBehavior =
                                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                }
            }

            windowManager?.addView(overlayView, overlayParams)
            isOverlayShown = true

            // Also keep screen on
            keepScreenOn()

            Log.d(TAG, "Black screen overlay successfully shown")

            // Send broadcast to inform other components
            sendBroadcast(Intent("com.example.ChatterBox.OVERLAY_SHOWN"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}", e)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShown || overlayView == null) {
            Log.d(TAG, "No overlay to hide")
            return
        }

        try {
            Log.d(TAG, "Removing overlay")
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayShown = false
            releaseWakeLock()

            Log.d(TAG, "Black screen overlay hidden")

            // Send broadcast to inform other components
            sendBroadcast(Intent("com.example.ChatterBox.OVERLAY_HIDDEN"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay: ${e.message}", e)
        }
    }

    private fun keepScreenOn() {
        releaseWakeLock()

        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager

            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ChatterBox:ScreenWakeLock"
            )

            wakeLock?.acquire()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to keep screen on: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}", e)
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        hideOverlay()
        releaseWakeLock()
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }

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

        // Helper methods
        @RequiresApi(Build.VERSION_CODES.O)
        fun showBlackOverlay(context: Context) {
            Log.d("ScreenManager", "Requesting to show black overlay")

            // Check permission first
            if (!Settings.canDrawOverlays(context)) {
                Log.e("ScreenManager", "Cannot show overlay - permission not granted")
                return
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive) {
                // Don't show overlay if screen is off
                Log.d("ScreenManager", "Screen is off, not showing overlay")
                return
            }

            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }

            try {
                context.startForegroundService(intent)
                Log.d("ScreenManager", "Sent show overlay command to service")
            } catch (e: Exception) {
                Log.e("ScreenManager", "Failed to send show overlay command: ${e.message}")
            }
        }


        fun hideBlackOverlay(context: Context) {
            Log.d("ScreenManager", "Requesting to hide black overlay")
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            try {
                context.startService(intent)
                Log.d("ScreenManager", "Sent hide overlay command to service")
            } catch (e: Exception) {
                Log.e("ScreenManager", "Failed to send hide overlay command: ${e.message}")
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun keepScreenOn(context: Context) {
            Log.d("ScreenManager", "Requesting to keep screen on")
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_KEEP_SCREEN_ON
            }
            try {
                context.startForegroundService(intent)
                Log.d("ScreenManager", "Sent keep screen on command to service")
            } catch (e: Exception) {
                Log.e("ScreenManager", "Failed to send keep screen on command: ${e.message}")
            }
        }
    }

}