package com.example.ChatterBox.accessibility

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.MainActivity
import com.example.ChatterBox.R

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
            AccessibilityHelper.openAccessibilitySettings(this)
        }

        // Skip button
        findViewById<TextView>(R.id.txtSkip).setOnClickListener {
            AccessibilityHelper.markSkippedForSession(this)
            goToMainActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        // If accessibility service is enabled, finish the promo activity
        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            goToMainActivity()
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}