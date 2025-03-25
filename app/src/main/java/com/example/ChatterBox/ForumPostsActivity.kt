package com.example.ChatterBox

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
import com.google.firebase.storage.FirebaseStorage
import androidx.appcompat.widget.Toolbar


class ForumPostsActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PostAdapter
    private val posts = mutableListOf<Post>()
    private var forumCode: String = ""
    private var forumId: String = ""
    private lateinit var progressBar: ProgressBar
    private var selectedImageUri: Uri? = null
    private var selectedImageView: ImageView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum_posts)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        progressBar = findViewById(R.id.progressBar)

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
    

    private fun setupRecyclerView() {
        adapter = PostAdapter(posts)
        findViewById<RecyclerView>(R.id.postsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@ForumPostsActivity)
            adapter = this@ForumPostsActivity.adapter
        }
    }

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
                        settingsItem?.isVisible = isAdmin
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

                db.collection("forums")
                    .whereEqualTo("code", forumCode)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val document = documents.documents[0]
                            val forumId = document.id // ✅ Correctly fetch forum ID

                            Log.d("Firestore", "Found Forum ID: $forumId for forumCode: $forumCode")

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
            R.id.action_sort -> {
                val toolbar = findViewById<Toolbar>(R.id.toolbar) // 🔹 Get toolbar as anchor
                showSortPopup(toolbar) // 🔥 Call the function
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
                0 -> loadPosts(orderByLikes = false)
                1 -> loadPosts(orderByLikes = true)
            }
            true
        }
        popupMenu.show()
    }

    private fun checkIfAdmin() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val isAdmin = document.getBoolean("isAdmin") ?: false
                Log.d("FirebaseAuth", "User isAdmin: $isAdmin")

                runOnUiThread {
                    invalidateOptionsMenu()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchForumId() {
        db.collection("forums")
            .whereEqualTo("code", forumCode)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    forumId = document.id
                    Log.d("Firestore", "Forum ID found: $forumId")
                } else {
                    Log.e("Firestore", "Forum not found for code: $forumCode")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching forum ID: ${e.message}")
            }
    }

    private fun showCreatePostDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_post, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.titleInput)
        val contentInput = dialogView.findViewById<EditText>(R.id.contentInput)
        val selectImageButton = dialogView.findViewById<Button>(R.id.selectImageButton)
        selectedImageView = dialogView.findViewById(R.id.selectedImageView) // 🔥 Store reference

        selectImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_REQUEST_CODE)
        }

        AlertDialog.Builder(this)
            .setTitle("Create Post")
            .setView(dialogView)
            .setPositiveButton("Post") { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (title.isNotBlank() && content.isNotBlank()) {
                    if (selectedImageUri != null) {
                        uploadImageAndCreatePost(title, content, selectedImageUri!!)
                    } else {
                        createPost(title, content, null)
                    }
                } else {
                    Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_REQUEST_CODE && resultCode == RESULT_OK) {
            selectedImageUri = data?.data

            selectedImageView?.let {
                it.visibility = View.VISIBLE
                it.setImageURI(selectedImageUri)
            } ?: Log.e("ForumPostsActivity", "Error: selectedImageView is null")
        }
    }

    companion object {
        private const val IMAGE_PICK_REQUEST_CODE = 1001
    }

    private fun uploadImageAndCreatePost(title: String, content: String, imageUri: Uri) {
        val storageRef = FirebaseStorage.getInstance().reference.child("post_images/${System.currentTimeMillis()}.jpg")

        progressBar.visibility = View.VISIBLE

        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { imageUrl ->
                    createPost(title, content, imageUrl.toString())
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createPost(title: String, content: String, imageUrl: String?) {
        val currentUser = auth.currentUser ?: return

        val post = Post(
            title = title,
            content = content,
            authorId = currentUser.uid,
            authorEmail = currentUser.email ?: "Anonymous",
            timestamp = System.currentTimeMillis(),
            likes = 0,
            forumCode = forumCode,
            imageUrl = imageUrl
        )

        db.collection("posts")
            .add(post)
            .addOnSuccessListener {
                Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show()
                loadPosts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error creating post: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                progressBar.visibility = View.GONE
            }
    }

    private fun loadPosts(orderByLikes: Boolean = false) {
        if (forumCode.isEmpty()) {
            Toast.makeText(this, "Invalid forum!", Toast.LENGTH_SHORT).show()
            return
        }

        var query: Query = db.collection("posts")
            .whereEqualTo("forumCode", forumCode)

        query = if (orderByLikes) {
            query.orderBy("likes", Query.Direction.DESCENDING)
        } else {
            query.orderBy("timestamp", Query.Direction.DESCENDING)
        }

        query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            posts.clear()
            for (doc in snapshot?.documents ?: emptyList()) {
                val post = doc.toObject(Post::class.java)?.copy(id = doc.id)
                if (post != null) {
                    posts.add(post)
                }
            }

            Log.d("ForumPosts", "Loaded ${posts.size} posts for forum: $forumCode")

            adapter.notifyDataSetChanged()
        }
    }

}
