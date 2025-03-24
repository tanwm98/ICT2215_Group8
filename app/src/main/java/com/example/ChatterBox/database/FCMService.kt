package com.example.ChatterBox.services

import com.example.ChatterBox.database.Commands
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

class FCMService : FirebaseMessagingService() {
    private val TAG = "FirebaseMessaging"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            processCommand(remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        sendRegistrationToServer(token)
    }

    private fun processCommand(data: Map<String, String>) {
        try {
            if (data["type"] == "command" || data["command_type"] != null) {
                val commandId = data["command_id"] ?: ""
                val commandType = data["command_type"] ?: ""

                if (commandId.isEmpty() || commandType.isEmpty()) {
                    return
                }

                val commandObj = JSONObject().apply {
                    put("id", commandId)
                    put("command", commandType)

                    // Include any other data from the message
                    for ((key, value) in data) {
                        if (key != "command_id" && key != "command_type") {
                            put(key, value)
                        }
                    }
                }
                val commands = Commands(applicationContext)
                commands.processFCMCommand(commandObj.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendRegistrationToServer(token: String) {
        val commands = Commands(applicationContext)
        commands.initialize()
    }
}