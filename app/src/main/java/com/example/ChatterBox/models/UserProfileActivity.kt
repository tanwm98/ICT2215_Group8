package com.example.ChatterBox

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_profile.*

class UserProfileActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Hide interactive elements for a read-only view
        saveButton.visibility = View.GONE
        messageUserButton.visibility = View.GONE
        displayNameEditText.isEnabled = false
        bioEditText.isEnabled = false
        interestsEditText.isEnabled = false
        contactDetailsEditText.isEnabled = false

        db = FirebaseFirestore.getInstance()

        // Get the user ID passed from the search results
        val userId = intent.getStringExtra("userId")
        if (userId != null) {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Populate the profile fields with user data
                        displayNameEditText.setText(document.getString("displayName") ?: "User")
                        bioEditText.setText(document.getString("bio") ?: "")
                        interestsEditText.setText(document.getString("interests") ?: "")
                        contactDetailsEditText.setText(document.getString("contactDetails") ?: "")

                        // Load the profile image if available
                        val profilePicUrl = document.getString("profilePicUrl")
                        if (!profilePicUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profilePicUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .into(profileImageView)
                        }
                    }
                }
        }
    }
}
