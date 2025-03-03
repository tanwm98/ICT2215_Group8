package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity to promote the voice assistant accessibility features
 * This is shown to users to encourage them to enable the accessibility service
 */
class AccessibilityPromoActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessibility_promo)
        
        // Button to enable accessibility service
        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener {
            // Open accessibility settings
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            
            // Show toast guiding the user
            Toast.makeText(
                this,
                "Find and enable 'ChatterBox Voice Assistant' in the list",
                Toast.LENGTH_LONG
            ).show()
        }
        
        // Skip button
        findViewById<TextView>(R.id.txtSkip).setOnClickListener {
            // Go to main activity and don't show this again in this session
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    // This would check if the accessibility service is already enabled
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        )
        return accessibilityEnabled == 1
    }
    override fun onResume() {
        super.onResume()

        // Check if accessibility service is already enabled
        if (isAccessibilityServiceEnabled()) {
            // Service is enabled, proceed to main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
