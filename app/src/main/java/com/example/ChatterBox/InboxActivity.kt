package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.UserAdapter
import com.example.ChatterBox.models.Conversation
import com.example.ChatterBox.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class InboxActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private lateinit var searchInput: EditText
    private var users = mutableListOf<User>()

    // Flag to indicate whether a search is active.
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        searchInput = findViewById(R.id.searchInput)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)

        userAdapter = UserAdapter(users) { selectedUser ->
            openChat(selectedUser)
        }

        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        usersRecyclerView.adapter = userAdapter

        // Load conversations initially (if not searching)
        loadConversations()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                isSearching = query.isNotEmpty()
                if (query.isEmpty()) {
                    // If search is cleared, reload conversations.
                    users.clear()
                    userAdapter.notifyDataSetChanged()
                    loadConversations()
                } else {
                    searchUsers(query)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadConversations() {
        // Skip loading conversations if user is currently searching.
        if (isSearching) return

        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("conversations")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Log the error detail and show a Toast.
                    Log.e("InboxActivity", "Error loading conversations: ${error.message}")
                    Toast.makeText(
                        this,
                        "Error loading conversations: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }

                // Check again in case search started while waiting for the snapshot.
                if (isSearching) return@addSnapshotListener

                users.clear()
                if (snapshot == null || snapshot.isEmpty) {
                    Toast.makeText(this, "Start messaging!", Toast.LENGTH_SHORT).show()
                    userAdapter.notifyDataSetChanged()
                    return@addSnapshotListener
                }

                for (document in snapshot.documents) {
                    val conversation = document.toObject(Conversation::class.java)
                    val otherUserId = conversation?.participants?.firstOrNull { it != currentUserId }
                    if (otherUserId != null) {
                        db.collection("users").document(otherUserId).get()
                            .addOnSuccessListener { userDoc ->
                                val user = userDoc.toObject(User::class.java)
                                if (user != null) {
                                    users.add(user)
                                    userAdapter.notifyDataSetChanged()
                                }
                            }
                    }
                }
            }
    }

    private fun searchUsers(query: String) {
        db.collection("users")
            .orderBy("displayName")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .get()
            .addOnSuccessListener { result ->
                users.clear()
                if (result.isEmpty) {
                    Toast.makeText(this, "No users found", Toast.LENGTH_SHORT).show()
                } else {
                    for (document in result) {
                        val user = document.toObject(User::class.java)
                        if (user.uid != auth.currentUser?.uid) {
                            users.add(user)
                        }
                    }
                }
                userAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e("InboxActivity", "Error searching users: ${e.message}")
                Toast.makeText(this, "Error searching users", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openChat(user: User) {
        val currentUserId = auth.currentUser?.uid ?: return

        if (user.uid == currentUserId) {
            Toast.makeText(this, "You cannot message yourself!", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate conversation ID by sorting the UIDs.
        val sortedIds = listOf(currentUserId, user.uid).sorted()
        val conversationId = sortedIds.joinToString("_")
        val conversationRef = db.collection("conversations").document(conversationId)

        conversationRef.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                // Create a new conversation document with the proper fields.
                val newConversation = mapOf(
                    "conversationId" to conversationId,
                    "participants" to sortedIds,
                    "lastMessage" to "",
                    "lastMessageTimestamp" to System.currentTimeMillis()
                )
                conversationRef.set(newConversation)
                    .addOnSuccessListener {
                        openMessageActivity(user, conversationId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("InboxActivity", "Error creating conversation: ${e.message}")
                        Toast.makeText(this, "Error creating conversation", Toast.LENGTH_SHORT).show()
                    }
            } else {
                openMessageActivity(user, conversationId)
            }
        }
    }

    private fun openMessageActivity(user: User, conversationId: String) {
        val intent = Intent(this, MessageActivity::class.java)
        intent.putExtra("conversationId", conversationId)
        intent.putExtra("recipientUserId", user.uid)
        intent.putExtra("recipientDisplayName", user.displayName)
        intent.putExtra("recipientProfilePicUrl", user.profilePicUrl)
        startActivity(intent)
    }
}
