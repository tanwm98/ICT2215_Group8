package com.example.ChatterBox

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.PostAdapter
import com.example.ChatterBox.models.Post
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ForumPostsActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PostAdapter
    private val posts = mutableListOf<Post>()
    private var forumCode: String = "" // 🔥 Forum code passed from previous screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum_posts)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        forumCode = intent.getStringExtra("FORUM_CODE") ?: ""
        if (forumCode.isEmpty()) {
            Toast.makeText(this, "Forum not found!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupFab()
        loadPosts()
    }

    /** 🔹 Setup RecyclerView */
    private fun setupRecyclerView() {
        adapter = PostAdapter(posts)
        findViewById<RecyclerView>(R.id.postsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ForumPostsActivity)
            adapter = this@ForumPostsActivity.adapter
        }
    }

    /** 🔹 Floating Action Button to Create Post */
    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabCreatePost).setOnClickListener {
            showCreatePostDialog()
        }
    }

    /** 🔹 Show Dialog for Creating a New Post */
    private fun showCreatePostDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_post, null)
        AlertDialog.Builder(this)
            .setTitle("Create Post")
            .setView(dialogView)
            .setPositiveButton("Post") { _, _ ->
                val title = dialogView.findViewById<EditText>(R.id.titleInput).text.toString()
                val content = dialogView.findViewById<EditText>(R.id.contentInput).text.toString()
                if (title.isNotBlank() && content.isNotBlank()) {
                    createPost(title, content)
                } else {
                    Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** 🔹 Create and Upload a New Post to Firestore */
    private fun createPost(title: String, content: String) {
        val currentUser = auth.currentUser ?: return

        val post = Post(
            title = title,
            content = content,
            authorId = currentUser.uid,
            authorEmail = currentUser.email ?: "Anonymous",
            timestamp = System.currentTimeMillis(),
            likes = 0,
            forumCode = forumCode // 🔥 Attach forum code to post
        )

        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE

        db.collection("posts")
            .add(post)
            .addOnSuccessListener {
                Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show()
                loadPosts() // ✅ Refresh posts after creating
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error creating post: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
            }
    }

    /** 🔹 Load Posts for this Forum */
    private fun loadPosts() {
        db.collection("posts")
            .whereEqualTo("forumCode", forumCode) // 🔥 Filter by forum code
            //.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                posts.clear()
                for (doc in snapshot?.documents ?: emptyList()) {
                    val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                    if (post != null) {
                        posts.add(post)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }
}
