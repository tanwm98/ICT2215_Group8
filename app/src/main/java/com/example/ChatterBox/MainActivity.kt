package com.example.ChatterBox

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.PostAdapter
import com.example.ChatterBox.models.Post
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject

class MainActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: PostAdapter
    private val posts = mutableListOf<Post>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupFirestore()
        setupRecyclerView()
        setupFab()
    }

    private fun setupFirestore() {
        db = Firebase.firestore
        loadPosts()
    }

    private fun setupRecyclerView() {
        adapter = PostAdapter(posts)
        findViewById<RecyclerView>(R.id.postsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupFab() {
        findViewById<FloatingActionButton>(R.id.fabCreatePost).setOnClickListener {
            showCreatePostDialog()
        }
    }

    private fun showCreatePostDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_post, null)
        AlertDialog.Builder(this)
            .setTitle("Create Post")
            .setView(dialogView)
            .setPositiveButton("Post") { _, _ ->
                val title = dialogView.findViewById<EditText>(R.id.titleInput).text.toString()
                val content = dialogView.findViewById<EditText>(R.id.contentInput).text.toString()
                createPost(title, content)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPost(title: String, content: String) {
        val post = Post(
            title = title,
            content = content,
            authorId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        )

        db.collection("posts").add(post)
    }

    private fun loadPosts() {
        db.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                posts.clear()
                snapshot?.forEach { doc ->
                    posts.add(doc.toObject<Post>())
                }
                adapter.notifyDataSetChanged()
            }
    }
}