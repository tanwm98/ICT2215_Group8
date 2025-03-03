package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class HomeActivity : AppCompatActivity() {
    // Preferences key to track if we've shown the accessibility promo
    private val PREFS_KEY = "chatterbox_prefs"
    private val SHOWN_ACCESSIBILITY_PROMO = "shown_accessibility_promo"
    
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
    }
}
