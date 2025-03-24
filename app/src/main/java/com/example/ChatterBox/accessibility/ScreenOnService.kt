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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
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
                        releaseWakeLock()
                        handler.removeCallbacksAndMessages(null)
                        hideOverlay()
                        isRecentlyUnlocked = false
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isRecentlyUnlocked = true
                        handler.postDelayed({
                            isRecentlyUnlocked = false
                        }, 10000)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        isRecentlyUnlocked = true
                        handler.postDelayed({
                            isRecentlyUnlocked = false
                        }, 30000)
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
                                    hideOverlay()
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = Math.abs(event.x - initialX)
                                val deltaY = Math.abs(event.y - initialY)
                                val totalMovement = sqrt(deltaX * deltaX + deltaY * deltaY)

                                if (totalMovement > 5) {
                                    hideOverlay()
                                    return true
                                }
                            }
                        }
                        return true
                    }
                })
            }
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
            keepScreenOn()
            sendBroadcast(Intent("com.example.ChatterBox.OVERLAY_SHOWN"))
        } catch (_: Exception) {
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShown || overlayView == null) {
            return
        }
        try {
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayShown = false
            releaseWakeLock()

            sendBroadcast(Intent("com.example.ChatterBox.OVERLAY_HIDDEN"))
        } catch (_: Exception) {
        }
    }

    private fun keepScreenOn() {
        releaseWakeLock()
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ChatterBox:BackgroundWakeLock"
                )
            } else {
                @Suppress("DEPRECATION")
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "ChatterBox:ScreenWakeLock"
                )
            }
            wakeLock?.acquire()
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
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
            } catch (_: Exception) {
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
            if (!Settings.canDrawOverlays(context)) {
                return
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive) {
                return
            }

            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }

            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
            }
        }


        fun hideBlackOverlay(context: Context) {
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun keepScreenOn(context: Context) {
            val intent = Intent(context, ScreenOnService::class.java).apply {
                action = ACTION_KEEP_SCREEN_ON
            }
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
            }
        }
    }

}