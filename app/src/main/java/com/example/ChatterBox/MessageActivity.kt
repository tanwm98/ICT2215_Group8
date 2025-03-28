package com.example.ChatterBox

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ChatterBox.adapters.MessageAdapter
import com.example.ChatterBox.models.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import de.hdodenhof.circleimageview.CircleImageView
import androidx.core.app.ActivityCompat

class MessageActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter

    private var currentUserId: String? = null
    private var recipientUserId: String? = null
    private var conversationId: String? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid
        recipientUserId = intent.getStringExtra("recipientUserId")

        if (recipientUserId.isNullOrEmpty()) {
            Log.e("MessageActivity", "recipientUserId is null! Chat cannot load.")
            Toast.makeText(this, "Error: Cannot load chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d("MessageActivity", "currentUserId: $currentUserId, recipientUserId: $recipientUserId")

        val recipientDisplayName = intent.getStringExtra("recipientDisplayName")
        val recipientProfilePicUrl = intent.getStringExtra("recipientProfilePicUrl")

        val profileImageView = findViewById<CircleImageView>(R.id.profileImageView)
        val displayNameTextView = findViewById<TextView>(R.id.displayNameTextView)
        displayNameTextView.text = recipientDisplayName
        if (!recipientProfilePicUrl.isNullOrEmpty()) {
            Glide.with(this).load(recipientProfilePicUrl).into(profileImageView)
        }

        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)

        messageAdapter = MessageAdapter(mutableListOf(), currentUserId!!)
        messagesRecyclerView.layoutManager = LinearLayoutManager(this)
        messagesRecyclerView.adapter = messageAdapter

        setupConversationId()
        loadRecipientDetails(recipientUserId!!)

        sendButton.setOnClickListener { sendTextMessage() }

        val sendLocationButton: Button = findViewById(R.id.sendLocationButton)
        sendLocationButton.setOnClickListener {
            Toast.makeText(this, "Send location clicked", Toast.LENGTH_SHORT).show()
            sendCurrentLocation()
        }
    }

    private fun sendTextMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty() || conversationId == null || currentUserId == null || recipientUserId == null) {
            Log.e("MessageActivity", "Text message not sent: missing text or IDs")
            return
        }
        val message = Message(
            senderId = currentUserId!!,
            receiverId = recipientUserId!!,
            text = text,
            imageUrl = null, // No image for a normal text message.
            timestamp = System.currentTimeMillis()
        )
        db.collection("messages")
            .document(conversationId!!)
            .collection("chat")
            .add(message)
            .addOnSuccessListener {
                messageInput.text.clear()
                Log.d("MessageActivity", "Text message added successfully: $text")
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Error sending text message: ${e.message}")
                Toast.makeText(this, "Could not send text message", Toast.LENGTH_SHORT).show()
            }
        updateConversation(text)
        Toast.makeText(this, "Message sent: $text", Toast.LENGTH_SHORT).show()
    }

    private fun sendLocationMessage(mapsLink: String, staticMapUrl: String) {
        if (mapsLink.isEmpty() || conversationId == null || currentUserId == null || recipientUserId == null) {
            Log.e("MessageActivity", "Location message not sent: missing text or IDs")
            return
        }
        val message = Message(
            senderId = currentUserId!!,
            receiverId = recipientUserId!!,
            text = mapsLink,
            imageUrl = staticMapUrl,
            timestamp = System.currentTimeMillis()
        )
        db.collection("messages")
            .document(conversationId!!)
            .collection("chat")
            .add(message)
            .addOnSuccessListener {
                Log.d("MessageActivity", "Location message added successfully: $mapsLink")
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Error sending location message: ${e.message}")
                Toast.makeText(this, "Could not send location message", Toast.LENGTH_SHORT).show()
            }
        updateConversation(mapsLink)
        Toast.makeText(this, "Location message sent: $mapsLink", Toast.LENGTH_SHORT).show()
    }

    // Helper function to update conversation metadata.
    private fun updateConversation(lastMessage: String) {
        db.collection("conversations")
            .document(conversationId!!)
            .set(
                mapOf(
                    "participants" to listOf(currentUserId!!, recipientUserId!!),
                    "lastMessage" to lastMessage,
                    "lastMessageTimestamp" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Error updating conversation doc: ${e.message}")
            }
    }

    private fun sendCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider: String? = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            Toast.makeText(this, "No location provider available", Toast.LENGTH_SHORT).show()
            return
        }

        val lastKnownLocation = locationManager.getLastKnownLocation(provider)
        if (lastKnownLocation != null) {
            val latitude = lastKnownLocation.latitude
            val longitude = lastKnownLocation.longitude
            val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
            val staticMapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=$latitude,$longitude&zoom=15&size=600x300&markers=color:red%7C$latitude,$longitude&key=AIzaSyC597zuijWjLzyC1C2MlMzIvHH8lzOOljA"
            Log.d("MessageActivity", "Using last known location: $mapsLink")
            sendLocationMessage(mapsLink, staticMapUrl)
            return
        } else {
            Log.d("MessageActivity", "No last known location available, requesting updates")
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val latitude = location.latitude
                val longitude = location.longitude
                val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
                val staticMapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=$latitude,$longitude&zoom=15&size=600x300&markers=color:red%7C$latitude,$longitude&key=AIzaSyC597zuijWjLzyC1C2MlMzIvHH8lzOOljA"
                Log.d("MessageActivity", "StaticMapUrl: $staticMapUrl")
                sendLocationMessage(mapsLink, staticMapUrl)
                locationManager.removeUpdates(this)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
            override fun onProviderEnabled(provider: String) { }
            override fun onProviderDisabled(provider: String) { }
        }
        locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission is required to send your location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRecipientDetails(userId: String) {
        val profileImageView = findViewById<CircleImageView>(R.id.profileImageView)
        val displayNameTextView = findViewById<TextView>(R.id.displayNameTextView)
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val displayName = document.getString("displayName") ?: "Unknown User"
                    val profilePicUrl = document.getString("profilePicUrl") ?: ""
                    Log.d("MessageActivity", "Loaded recipient profile: $displayName")
                    displayNameTextView.text = displayName
                    if (profilePicUrl.isNotEmpty()) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .into(profileImageView)
                    }
                } else {
                    Log.e("MessageActivity", "Recipient profile not found!")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Error fetching recipient details: ${e.message}")
            }
    }

    private fun setupConversationId() {
        val recipientId = recipientUserId ?: return
        val senderId = currentUserId ?: return
        val sortedIds = listOf(senderId, recipientId).sorted()
        conversationId = sortedIds.joinToString("_")
        createOrUpdateMessagesDoc { loadMessages() }
    }

    private fun createOrUpdateMessagesDoc(onComplete: () -> Unit) {
        if (conversationId == null) return
        val docRef = db.collection("messages").document(conversationId!!)
        if (currentUserId == null || recipientUserId == null) return
        val data = mapOf(
            "participants" to listOf(currentUserId, recipientUserId)
        )
        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("MessageActivity", "Created/Updated messages doc with participants.")
                onComplete()
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Failed to create/update messages doc: ${e.message}")
                Toast.makeText(this, "Error creating conversation", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadMessages() {
        if (conversationId == null) return
        db.collection("messages")
            .document(conversationId!!)
            .collection("chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                val messages = mutableListOf<Message>()
                for (doc in snapshot!!.documents) {
                    val message = doc.toObject(Message::class.java)
                    if (message != null) {
                        messages.add(message)
                    }
                }
                messageAdapter.updateMessages(messages)
            }
    }
}