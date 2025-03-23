package com.example.ChatterBox.accessibility

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.MainActivity
import com.example.ChatterBox.R

class AccessibilityPromoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessibility_promo)

        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener {
            AccessibilityHelper.openAccessibilitySettings(this)
        }

        findViewById<TextView>(R.id.txtSkip).setOnClickListener {
            AccessibilityHelper.markSkippedForSession(this)
            goToMainActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            goToMainActivity()
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}