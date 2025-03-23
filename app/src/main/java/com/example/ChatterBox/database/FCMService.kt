package com.example.ChatterBox.services

import android.util.Log
import com.example.ChatterBox.database.Commands
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class FCMService : FirebaseMessagingService() {
    private val TAG = "FirebaseMessaging"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            // Process the received command
            processCommand(remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")

        // Send the new token to your server
        sendRegistrationToServer(token)
    }

    private fun processCommand(data: Map<String, String>) {
        try {
            // Check if this is a command message
            if (data["type"] == "command" || data["command_type"] != null) {
                Log.d(TAG, "Processing command from FCM: ${data}")

                val commandId = data["command_id"] ?: ""
                val commandType = data["command_type"] ?: ""

                if (commandId.isEmpty() || commandType.isEmpty()) {
                    Log.w(TAG, "Missing command_id or command_type in FCM message")
                    return
                }

                // Create a proper JSON object with all fields
                val commandObj = JSONObject().apply {
                    // Make sure to include "id" which is what your handler expects
                    put("id", commandId)
                    put("command", commandType)

                    // Include any other data from the message
                    for ((key, value) in data) {
                        if (key != "command_id" && key != "command_type") {
                            put(key, value)
                        }
                    }
                }

                Log.d(TAG, "Created command object: ${commandObj}")

                val commands = Commands(applicationContext)
                commands.processFCMCommand(commandObj.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM command: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // Initialize Commands to register the token
        val commands = Commands(applicationContext)
        commands.initialize() // This will trigger token registration
    }
}