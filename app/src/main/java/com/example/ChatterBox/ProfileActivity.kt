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
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var profileImageView: ImageView
    private lateinit var displayNameEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var interestsEditText: EditText
    private lateinit var contactDetailsEditText: EditText
    private lateinit var availabilityStatusEditText: EditText
    private lateinit var roleTextView: TextView
    private lateinit var saveButton: Button
    private lateinit var messageUserButton: Button
    private lateinit var progressBar: ProgressBar

    private var profileImageUri: Uri? = null
    private var isViewingOtherUser = false
    private var userId: String? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize UI elements
        profileImageView = findViewById(R.id.profileImageView)
        displayNameEditText = findViewById(R.id.displayNameEditText)
        bioEditText = findViewById(R.id.bioEditText)
        interestsEditText = findViewById(R.id.interestsEditText)
        contactDetailsEditText = findViewById(R.id.contactDetailsEditText)
        availabilityStatusEditText = findViewById(R.id.availabilityStatusEditText)
        roleTextView = findViewById(R.id.roleTextView)
        saveButton = findViewById(R.id.saveButton)
        messageUserButton = findViewById(R.id.messageUserButton)
        progressBar = findViewById(R.id.progressBar)

        // Get User ID from Intent (if null, load current user's profile)
        userId = intent.getStringExtra("USER_ID") ?: auth.currentUser?.uid
        isViewingOtherUser = userId != auth.currentUser?.uid

        setupUI()
        loadProfile()
    }

    /** 🔹 Setup UI based on profile type */
    private fun setupUI() {
        if (isViewingOtherUser) {
            saveButton.visibility = View.GONE  // Hide save button for other users
            messageUserButton.visibility = View.VISIBLE  // Show message button
            makeFieldsReadOnly()
        } else {
            saveButton.visibility = View.VISIBLE  // Show save button for own profile
            messageUserButton.visibility = View.GONE  // Hide message button for own profile
        }

        profileImageView.setOnClickListener {
            if (!isViewingOtherUser) openImagePicker()
        }

        saveButton.setOnClickListener {
            saveProfile()
        }

        messageUserButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }
    }

    /** 🔹 Load user profile data */
    private fun loadProfile() {
        if (userId == null) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        db.collection("users").document(userId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    displayNameEditText.setText(document.getString("displayName") ?: "")
                    bioEditText.setText(document.getString("bio") ?: "")
                    interestsEditText.setText(document.getString("expertiseInterests") ?: "")
                    contactDetailsEditText.setText(document.getString("contactDetails") ?: "")
                    availabilityStatusEditText.setText(document.getString("availabilityStatus") ?: "")
                    roleTextView.text = "Role: ${document.getString("role") ?: "User"}"

                    val profilePicUrl = document.getString("profilePicUrl")
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this).load(profilePicUrl).into(profileImageView)
                    }
                }
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    /** 🔹 Open Image Picker (Only for Own Profile) */
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
                profileImageView.setImageURI(it) // Show preview immediately
            }
        }
    }

    /** 🔹 Save User Profile */
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

        if (profileImageUri != null) {
            val imageRef = storage.reference.child("profile_pictures/${user.uid}/${UUID.randomUUID()}.jpg")
            imageRef.putFile(profileImageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        throw task.exception ?: Exception("Image upload failed")
                    }
                    imageRef.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        updateProfileData(user.uid, displayName, bio, interests, contactDetails, availabilityStatus, task.result.toString())
                    } else {
                        Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
                }
        } else {
            updateProfileData(user.uid, displayName, bio, interests, contactDetails, availabilityStatus, null)
        }
    }

    /** 🔹 Update Firestore User Data */
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
        profilePicUrl?.let { data["profilePicUrl"] = it }

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

    /** 🔹 Make Fields Read-Only */
    private fun makeFieldsReadOnly() {
        displayNameEditText.isEnabled = false
        bioEditText.isEnabled = false
        interestsEditText.isEnabled = false
        contactDetailsEditText.isEnabled = false
        availabilityStatusEditText.isEnabled = false
    }
}
