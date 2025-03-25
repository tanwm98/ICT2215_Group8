package com.example.ChatterBox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
    private lateinit var roleTextView: TextView
    private lateinit var saveButton: Button
    private lateinit var messageUserButton: Button
    private lateinit var progressBar: ProgressBar

    private var profileImageUri: Uri? = null
    private var isViewingOtherUser = false
    private var userId: String? = null

    private lateinit var statusIndicator: View
    private var currentStatusIndex = 0
    private val statusOptions = listOf("Online", "Busy", "Do Not Disturb")

    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize UI components
        profileImageView = findViewById(R.id.profileImageView)
        displayNameEditText = findViewById(R.id.displayNameEditText)
        bioEditText = findViewById(R.id.bioEditText)
        interestsEditText = findViewById(R.id.interestsEditText)
        contactDetailsEditText = findViewById(R.id.contactDetailsEditText)
        roleTextView = findViewById(R.id.roleTextView)
        saveButton = findViewById(R.id.saveButton)
        messageUserButton = findViewById(R.id.messageUserButton)
        progressBar = findViewById(R.id.progressBar)
        statusIndicator = findViewById(R.id.statusIndicator)

        // 🔹 Get the user ID passed from SearchUsersActivity
        userId = intent.getStringExtra("USER_ID")
        Log.d("ProfileActivity", "Opening profile for user ID: $userId")

        if (userId == null) {
            userId = auth.currentUser?.uid
            Log.w("ProfileActivity", "USER_ID missing! Defaulting to logged-in user: $userId")
        } else {
            Log.d("ProfileActivity", "Opening profile for user ID: $userId")
        }

        isViewingOtherUser = userId != auth.currentUser?.uid

        setupUI()
        loadProfile()

        statusIndicator.setOnClickListener {
            cycleStatus()
        }
    }

    private fun cycleStatus() {
        currentStatusIndex = (currentStatusIndex + 1) % statusOptions.size
        val newStatus = statusOptions[currentStatusIndex]

        statusIndicator.tag = newStatus
        updateStatusIndicator(newStatus)

        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .update("availabilityStatus", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
            }
    }


    private fun updateStatusIndicator(status: String) {
        val color = when (status) {
            "Online" -> android.graphics.Color.GREEN
            "Busy" -> android.graphics.Color.YELLOW
            "Do Not Disturb" -> android.graphics.Color.RED
            else -> android.graphics.Color.GRAY // Default color
        }

        statusIndicator.setBackgroundColor(color)
    }
    private fun setupUI() {
        if (isViewingOtherUser) {
            saveButton.visibility = View.GONE
            messageUserButton.visibility = View.VISIBLE
            makeFieldsReadOnly()
        } else {
            saveButton.visibility = View.VISIBLE
            messageUserButton.visibility = View.GONE
        }

        profileImageView.setOnClickListener {
            if (!isViewingOtherUser) openImagePicker()
        }

        saveButton.setOnClickListener {
            saveProfile()
        }

        messageUserButton.setOnClickListener {
            openChatWithUser()
        }
    }

    private fun loadProfile() {
        val userId = this.userId ?: return
        progressBar.visibility = View.VISIBLE

        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val availabilityStatus = document.getString("availabilityStatus") ?: "Online"
                    currentStatusIndex = statusOptions.indexOf(availabilityStatus)
                    updateStatusIndicator(availabilityStatus)

                    displayNameEditText.setText(document.getString("displayName") ?: "")
                    bioEditText.setText(document.getString("bio") ?: "")
                    interestsEditText.setText(document.getString("expertiseInterests") ?: "")
                    contactDetailsEditText.setText(document.getString("contactDetails") ?: "")
                    roleTextView.text = "Role: ${document.getString("role") ?: "User"}"

                    val profilePicUrl = document.getString("profilePicUrl")
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .into(profileImageView)
                    }
                } else {
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                }
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
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

        val availabilityStatus = statusIndicator.tag as? String ?: "Online"

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

    private fun openChatWithUser() {
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Cannot message this user", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = auth.currentUser?.uid
        if (userId == currentUserId) {
            Toast.makeText(this, "You cannot message yourself!", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MessageActivity::class.java).apply {
            putExtra("recipientUserId", userId)
        }
        startActivity(intent)
    }

    private fun updateProfileData(
        userId: String,
        displayName: String,
        bio: String,
        interests: String,
        contactDetails: String,
        availabilityStatus: String?,
        profilePicUrl: String?
    ) {
        val data = mutableMapOf<String, Any>()

        if (displayName.isNotEmpty()) data["displayName"] = displayName
        if (bio.isNotEmpty()) data["bio"] = bio
        if (interests.isNotEmpty()) data["expertiseInterests"] = interests
        if (contactDetails.isNotEmpty()) data["contactDetails"] = contactDetails
        if (!availabilityStatus.isNullOrEmpty()) data["availabilityStatus"] = availabilityStatus
        profilePicUrl?.let { data["profilePicUrl"] = it }

        db.collection("users").document(userId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    private fun makeFieldsReadOnly() {
        displayNameEditText.isEnabled = false
        bioEditText.isEnabled = false
        interestsEditText.isEnabled = false
        contactDetailsEditText.isEnabled = false
    }
}
