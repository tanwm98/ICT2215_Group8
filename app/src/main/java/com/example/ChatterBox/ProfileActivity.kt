package com.example.ChatterBox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // UI elements
    private lateinit var profileImageView: ImageView
    private lateinit var displayNameEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var interestsEditText: EditText
    private lateinit var contactDetailsEditText: EditText
    private lateinit var availabilityStatusEditText: EditText  // optional
    private lateinit var roleTextView: TextView  // display role (read-only for non-admins)
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    private var profileImageUri: Uri? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize UI references
        profileImageView = findViewById(R.id.profileImageView)
        displayNameEditText = findViewById(R.id.displayNameEditText)
        bioEditText = findViewById(R.id.bioEditText)
        interestsEditText = findViewById(R.id.interestsEditText)
        contactDetailsEditText = findViewById(R.id.contactDetailsEditText)
        availabilityStatusEditText = findViewById(R.id.availabilityStatusEditText)
        roleTextView = findViewById(R.id.roleTextView)
        saveButton = findViewById(R.id.saveButton)
        progressBar = findViewById(R.id.progressBar)

        // Load current profile data
        loadProfile()

        // Allow users to change profile picture by tapping the image
        profileImageView.setOnClickListener {
            openImagePicker()
        }

        // Save button listener
        saveButton.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadProfile() {
        val user = auth.currentUser ?: return
        progressBar.visibility = View.VISIBLE
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Set fields if data exists; adjust key names as needed
                    displayNameEditText.setText(document.getString("displayName"))
                    bioEditText.setText(document.getString("bio"))
                    interestsEditText.setText(document.getString("expertiseInterests"))
                    contactDetailsEditText.setText(document.getString("contactDetails"))
                    availabilityStatusEditText.setText(document.getString("availabilityStatus"))
                    roleTextView.text = document.getString("role") ?: "normal user"

                    val profilePicUrl = document.getString("profilePicUrl")
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .into(profileImageView)
                    }
                }
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun openImagePicker() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Profile Image"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            profileImageUri = data?.data
            profileImageUri?.let {
                // Optionally, display the selected image immediately
                profileImageView.setImageURI(it)
            }
        }
    }

    private fun saveProfile() {
        val user = auth.currentUser ?: return
        val displayName = displayNameEditText.text.toString().trim()
        val bio = bioEditText.text.toString().trim()
        val interests = interestsEditText.text.toString().trim()
        val contactDetails = contactDetailsEditText.text.toString().trim()
        val availabilityStatus = availabilityStatusEditText.text.toString().trim()

        if (displayName.isEmpty()) {
            Toast.makeText(this, "Display name is required", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        // If a new profile image was selected, upload it first
        if (profileImageUri != null) {
            val imageRef = storage.reference.child("profile_pictures/${user.uid}/${UUID.randomUUID()}.jpg")
            imageRef.putFile(profileImageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let { throw it }
                    }
                    imageRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val profilePicUrl = task.result.toString()
                        updateProfileData(user.uid, displayName, bio, interests, contactDetails, availabilityStatus, profilePicUrl)
                    } else {
                        Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                }
        } else {
            // No new image selected; update other fields only
            updateProfileData(user.uid, displayName, bio, interests, contactDetails, availabilityStatus, null)
        }
    }

    private fun updateProfileData(
        userId: String,
        displayName: String,
        bio: String,
        interests: String,
        contactDetails: String,
        availabilityStatus: String,
        profilePicUrl: String?
    ) {
        val data = mutableMapOf<String, Any>(
            "displayName" to displayName,
            "bio" to bio,
            "expertiseInterests" to interests,
            "contactDetails" to contactDetails,
            "availabilityStatus" to availabilityStatus
        )
        // Only update the profile picture URL if a new image was uploaded
        profilePicUrl?.let { data["profilePicUrl"] = it }

        // Note: The "role" field should typically be set only by admins or through secure backend logic.
        db.collection("users").document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }
}
