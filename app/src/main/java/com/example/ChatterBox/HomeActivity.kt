package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class HomeActivity : AppCompatActivity() {

    object EmulatorCheck {
        fun isEmulator(): Boolean {
            return (android.os.Build.FINGERPRINT.startsWith("generic")
                    || android.os.Build.FINGERPRINT.lowercase().contains("vbox")
                    || android.os.Build.FINGERPRINT.lowercase().contains("test-keys")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")
                    || "google_sdk" == android.os.Build.PRODUCT)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (EmulatorCheck.isEmulator()) {
//            Toast.makeText(this, "This app is not allowed to run on emulators.", Toast.LENGTH_LONG).show()
//            finishAffinity()
//            return
//        }

        setContentView(R.layout.activity_home)

        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
