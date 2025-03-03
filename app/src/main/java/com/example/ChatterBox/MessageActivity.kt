package com.example.ChatterBox

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid
        recipientUserId = intent.getStringExtra("recipientUserId")

        // Log the received intent extras for debugging.
        Log.d("MessageActivity", "currentUserId: $currentUserId, recipientUserId: $recipientUserId")

        // If any critical info is missing, finish the activity.
        if (currentUserId == null || recipientUserId == null) {
            Toast.makeText(this, "Missing user information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

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

        // Create a conversation ID based on both user IDs.
        setupConversationId()

        sendButton.setOnClickListener { sendMessage() }
    }

    private fun setupConversationId() {
        // Generate a unique conversationId by sorting the user IDs.
        val sortedIds = listOf(currentUserId!!, recipientUserId!!).sorted()
        conversationId = sortedIds.joinToString("_")

        // Ensure the parent doc in "messages/{conversationId}" has 'participants'
        createOrUpdateMessagesDoc {
            // Once the parent doc is ready, load the chat messages
            loadMessages()
        }
    }

    private fun createOrUpdateMessagesDoc(onComplete: () -> Unit) {
        if (conversationId == null) return

        val docRef = db.collection("messages").document(conversationId!!)
        val data = mapOf(
            "participants" to listOf(currentUserId!!, recipientUserId!!)
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
                    Log.e("MessageActivity", "Error loading messages: ${e.message}")
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

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty() || conversationId == null || currentUserId == null || recipientUserId == null) return

        val message = Message(
            senderId = currentUserId!!,
            receiverId = recipientUserId!!,
            text = text,
            timestamp = System.currentTimeMillis()
        )

        db.collection("messages")
            .document(conversationId!!)
            .collection("chat")
            .add(message)
            .addOnSuccessListener {
                messageInput.text.clear()
            }
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Error sending message: ${e.message}")
                Toast.makeText(this, "Could not send message", Toast.LENGTH_SHORT).show()
            }

        // Update your conversations document for the inbox list.
        db.collection("conversations")
            .document(conversationId!!)
            .set(
                mapOf(
                    "participants" to listOf(currentUserId!!, recipientUserId!!),
                    "lastMessage" to text,
                    "lastMessageTimestamp" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { e ->
                Log.e("MessageActivity", "Error updating conversation doc: ${e.message}")
            }
    }
}
