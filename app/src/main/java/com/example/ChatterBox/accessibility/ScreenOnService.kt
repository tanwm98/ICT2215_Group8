package com.example.ChatterBox.accessibility

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ChatterBox.R

class ScreenOnService : Service() {
    private val TAG = "ScreenOnService"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        acquireWakeLock()
    }
    private fun startForeground() {
        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_service_channel",
                "Screen Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Create a notification
        val notification = NotificationCompat.Builder(this, "screen_service_channel")
            .setContentTitle("ChatterBox Active")
            .setContentText("Enhancing app experience...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Start as foreground service
        startForeground(1001, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure wake lock is active
        if (wakeLock == null || !(wakeLock?.isHeld ?: false)) {
            acquireWakeLock()
        }
        return START_STICKY
    }

    // In ScreenOnService.kt - update acquireWakeLock()
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            // Use a stronger wake lock type for Android 13
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ChatterBox:ScreenOnWakeLock"
            )
            wakeLock?.acquire()
            Log.d(TAG, "Wake lock acquired")

            // Request WRITE_SETTINGS permission first
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                // Modify system timeout
                Settings.System.putInt(contentResolver,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    30 * 60 * 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "Wake lock released")
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}