package com.example.ChatterBox

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.PostAdapter
import com.example.ChatterBox.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class SavedPostsActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PostAdapter
    private val savedPosts = mutableListOf<Post>()
    private var savedPostsListener: ListenerRegistration? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_posts)

        db = Firebase.firestore
        auth = FirebaseAuth.getInstance()

        setupRecyclerView()
        loadSavedPosts()
    }

    private fun setupRecyclerView() {
        adapter = PostAdapter(savedPosts)
        findViewById<RecyclerView>(R.id.savedPostsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@SavedPostsActivity)
            adapter = this@SavedPostsActivity.adapter
        }
    }


    private fun loadSavedPosts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to view saved posts", Toast.LENGTH_SHORT).show()
            return
        }

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        val savedPostsRef = db.collection("users").document(currentUser.uid)
            .collection("savedPosts")

        savedPostsListener = savedPostsRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Error loading saved posts: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }

            if (snapshot == null || snapshot.isEmpty) {
                Toast.makeText(this, "No saved posts found.", Toast.LENGTH_SHORT).show()
                savedPosts.clear()
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                return@addSnapshotListener
            }

            savedPosts.clear()

            for (doc in snapshot.documents) {
                val savedPostId = doc.id

                db.collection("posts").document(savedPostId)
                    .addSnapshotListener { postDoc, error ->
                        if (error != null) {
                            return@addSnapshotListener
                        }

                        if (postDoc != null && postDoc.exists()) {
                            val post = postDoc.toObject(Post::class.java)?.copy(id = postDoc.id)

                            savedPosts.removeAll { it.id == savedPostId } // Remove old entry
                            if (post != null) {
                                savedPosts.add(post)
                            }

                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                        }
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        savedPostsListener?.remove()
    }
}
