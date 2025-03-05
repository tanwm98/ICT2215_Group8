package com.example.ChatterBox

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.UserAdapter
import com.example.ChatterBox.models.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import android.content.Intent
import android.util.Log
import android.widget.Toast

class SearchUsersActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var cancelButton: Button
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_users)

        searchInput = findViewById(R.id.searchInput)
        cancelButton = findViewById(R.id.cancelButton)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)

        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter(userList) { selectedUser ->
            openProfile(selectedUser)
        }
        searchResultsRecyclerView.adapter = userAdapter

        // 🔹 Cancel Button Returns to Main Page
        cancelButton.setOnClickListener {
            finish() // ✅ Closes the search page and returns to MainActivity
        }

        // 🔹 Listen for text changes and search
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(query: CharSequence?, start: Int, before: Int, count: Int) {
                if (query != null) {
                    searchUsers(query.toString().trim())
                }
            }
        })
    }

    /** 🔍 Search Firestore for users */
    private fun searchUsers(query: String) {
        if (query.isEmpty()) {
            userList.clear()
            userAdapter.notifyDataSetChanged()
            return
        }

        FirebaseFirestore.getInstance().collection("users")
            .orderBy("username")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .get()
            .addOnSuccessListener { snapshot ->
                userList.clear()
                for (doc in snapshot.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        userList.add(user)
                    }
                }
                userAdapter.notifyDataSetChanged()
            }
    }

    private fun openProfile(user: User) {
        Log.d("SearchUsersActivity", "Opening profile for user: ${user.uid}")

        val intent = Intent(this, ProfileActivity::class.java)
        intent.putExtra("USER_ID", user.uid)
        startActivity(intent)
    }


//    private fun openChat(user: User) {
//        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
//        if (user.uid == currentUserId) {
//            Toast.makeText(this, "You cannot message yourself!", Toast.LENGTH_SHORT).show()
//            return
//        }
//        // Generate conversation ID by sorting the UIDs.
//        val sortedIds = listOf(currentUserId, user.uid).sorted()
//        val conversationId = sortedIds.joinToString("_")
//        // Optionally, create the conversation document here if it doesn't exist.
//        // Then pass the conversationId to MessageActivity.
//        val intent = Intent(this, MessageActivity::class.java)
//        intent.putExtra("conversationId", conversationId)
//        intent.putExtra("recipientUserId", user.uid)
//        intent.putExtra("recipientDisplayName", user.displayName)
//        intent.putExtra("recipientProfilePicUrl", user.profilePicUrl)
//        startActivity(intent)
//    }


}
