package com.example.ChatterBox

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.RoleAdapter
import com.example.ChatterBox.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleEditActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var roleAdapter: RoleAdapter
    private val userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_edit)

        db = FirebaseFirestore.getInstance()

        val recyclerView = findViewById<RecyclerView>(R.id.roleRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        roleAdapter = RoleAdapter(userList)
        recyclerView.adapter = roleAdapter

        loadUsers()
    }

    private fun loadUsers() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        userList.clear()

        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    //val user = doc.toObject(User::class.java)
                    val user = User(
                        uid = doc.getString("uid") ?: "",
                        email = doc.getString("email") ?: "",
                        username = doc.getString("username") ?: "",
                        displayName = doc.getString("displayName") ?: "",
                        bio = doc.getString("bio"),
                        contactDetails = doc.getString("contactDetails"),
                        expertiseInterests = doc.getString("expertiseInterests"),
                        availabilityStatus = doc.getString("availabilityStatus") ?: "",
                        profileImage = doc.getString("profileImage"),
                        profilePicUrl = doc.getString("profilePicUrl"),
                        isAdmin = doc.getBoolean("isAdmin") ?: false,
                        enrolledForum = (doc.get("enrolledForum") as? List<String>) ?: emptyList()
                    )

                    Log.d("🔥LOAD_USER", "uid=${doc.id}, user=${user}")

                    if (user != null && user.uid != currentUserId) {
                        userList.add(user)
                    }
                }
                roleAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
    }



}
