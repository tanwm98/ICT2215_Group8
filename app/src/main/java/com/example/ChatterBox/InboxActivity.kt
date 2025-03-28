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
    private lateinit var searchInput: EditText

    private var conversationUsers = mutableListOf<User>()
    private var searchUsersList = mutableListOf<User>()

    private lateinit var conversationAdapter: UserAdapter
    private lateinit var searchUserAdapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        searchInput = findViewById(R.id.searchInput)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)

        conversationAdapter = UserAdapter(conversationUsers) { selectedUser ->
            openChat(selectedUser)
        }
        searchUserAdapter = UserAdapter(searchUsersList) { selectedUser ->
            openChat(selectedUser)
        }

        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        usersRecyclerView.adapter = conversationAdapter

        loadConversations()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    searchUsersList.clear()
                    searchUserAdapter.notifyDataSetChanged()
                    usersRecyclerView.adapter = conversationAdapter
                    loadConversations()
                } else {
                    searchUsers(query)
                    usersRecyclerView.adapter = searchUserAdapter
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadConversations() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("conversations")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                conversationUsers.clear()
                if (snapshot == null || snapshot.isEmpty) {
                    Toast.makeText(this, "Start messaging!", Toast.LENGTH_SHORT).show()
                    conversationAdapter.notifyDataSetChanged()
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
                                    conversationUsers.add(user)
                                    conversationAdapter.notifyDataSetChanged()
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
            .addOnSuccessListener { snapshot ->
                searchUsersList.clear()
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No users found", Toast.LENGTH_SHORT).show()
                } else {
                    for (document in snapshot.documents) {
                        val user = document.toObject(User::class.java)
                        if (user != null && user.uid != auth.currentUser?.uid) {
                            searchUsersList.add(user)
                        }
                    }
                }
                searchUserAdapter.notifyDataSetChanged()
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

        val sortedIds = listOf(currentUserId, user.uid).sorted()
        val conversationId = sortedIds.joinToString("_")
        val conversationRef = db.collection("conversations").document(conversationId)

        conversationRef.get().addOnSuccessListener { document ->
            if (!document.exists()) {
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
