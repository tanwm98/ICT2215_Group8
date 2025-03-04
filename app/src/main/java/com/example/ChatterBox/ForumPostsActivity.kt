package com.example.ChatterBox

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.PostAdapter
import com.example.ChatterBox.models.Post
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ForumPostsActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PostAdapter
    private val posts = mutableListOf<Post>()
    private var forumCode: String = "" // 🔥 Forum code passed from previous screen
    private var forumId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum_posts)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 🔹 Attach the Toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar) // ✅ This enables the menu

        forumCode = intent.getStringExtra("FORUM_CODE") ?: ""
        if (forumCode.isEmpty()) {
            Toast.makeText(this, "Forum not found!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupFab()
        checkIfAdmin()
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

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val isAdmin = document.getBoolean("isAdmin") ?: false
                        val settingsItem = menu?.findItem(R.id.action_setting)
                        settingsItem?.isVisible = isAdmin // ✅ Show only if admin
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load menu options", Toast.LENGTH_SHORT).show()
                }
        }
        return super.onPrepareOptionsMenu(menu)
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.nav_menu_forum, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_setting -> {
                Log.d("Firestore", "Fetching forum ID for forumCode: $forumCode")

                // 🔥 Always fetch the latest forumId from Firestore before opening edit screen
                db.collection("forums")
                    .whereEqualTo("code", forumCode) // ✅ Find forum by its code
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val document = documents.documents[0]
                            val forumId = document.id // ✅ Correctly fetch forum ID

                            // 🔥 Debugging Log
                            Log.d("Firestore", "Found Forum ID: $forumId for forumCode: $forumCode")

                            // ✅ Now open the edit screen with the correct forumId
                            val intent = Intent(this, ForumEditActivity::class.java)
                            intent.putExtra("FORUM_ID", forumId) // ✅ Pass forum ID
                            intent.putExtra("FORUM_CODE", forumCode) // ✅ Pass forum code
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "Forum not found!", Toast.LENGTH_SHORT).show()
                            Log.e("Firestore", "No forum found for code: $forumCode")
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error fetching forum: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("Firestore", "Error fetching forum: ${e.message}")
                    }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }



    private fun showSortPopup(anchor: View) {
        val popupMenu = PopupMenu(this, anchor) // Attach to clicked button
        popupMenu.menu.add(0, 0, 0, "Sort by Latest")
        popupMenu.menu.add(0, 1, 1, "Sort by Most Likes")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> loadPosts(orderByLikes = false) // Sort by latest
                1 -> loadPosts(orderByLikes = true) // Sort by most likes
            }
            true
        }
        popupMenu.show()
    }

    /** 🔹 Check if User is Admin */
    private fun checkIfAdmin() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val isAdmin = document.getBoolean("isAdmin") ?: false
                Log.d("FirebaseAuth", "User isAdmin: $isAdmin")

                // 🔥 Save isAdmin value for later use
                runOnUiThread {
                    invalidateOptionsMenu() // ✅ This will call onPrepareOptionsMenu()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchForumId() {
        db.collection("forums")
            .whereEqualTo("code", forumCode) // 🔥 Query using forumCode
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    forumId = document.id // ✅ Set the forumId correctly
                    Log.d("Firestore", "Forum ID found: $forumId")
                } else {
                    Log.e("Firestore", "Forum not found for code: $forumCode")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching forum ID: ${e.message}")
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


    private fun loadPosts(orderByLikes: Boolean = false) {
        if (forumCode.isEmpty()) {
            Toast.makeText(this, "Invalid forum!", Toast.LENGTH_SHORT).show()
            return
        }

        var query: Query = db.collection("posts")
            .whereEqualTo("forumCode", forumCode) // ✅ Ensure only posts from this forum are fetched

        // 🔥 Order results properly
        query = if (orderByLikes) {
            query.orderBy("likes", Query.Direction.DESCENDING)
        } else {
            query.orderBy("timestamp", Query.Direction.DESCENDING)
        }

        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                //Toast.makeText(this, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            posts.clear()
            for (doc in snapshot?.documents ?: emptyList()) {
                val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                if (post != null) {
                    posts.add(post) // ✅ Only add posts from this forum
                }
            }

            Log.d("ForumPosts", "Loaded ${posts.size} posts for forum: $forumCode") // Debugging

            adapter.notifyDataSetChanged()
        }
    }

}
