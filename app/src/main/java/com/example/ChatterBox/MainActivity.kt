package com.example.ChatterBox

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.PostAdapter
import com.example.ChatterBox.models.Post
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PostAdapter
    private lateinit var drawerLayout: DrawerLayout
    private val posts = mutableListOf<Post>()

    private lateinit var refreshReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        setContentView(R.layout.activity_main)

        db = Firebase.firestore
        auth = Firebase.auth

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupDrawer()
        setupRecyclerView()
        setupFab()
//        checkAdminStatus()
        loadPosts()
        loadUserProfile()
        checkIfAdmin()

        // 🔹 Initialize the BroadcastReceiver
        refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                loadPosts() // ✅ Refresh posts when a bookmark is changed
            }
        }

        // 🔹 Register the receiver with explicit export settings
        val filter = IntentFilter("REFRESH_MAIN")
        registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(refreshReceiver) // ✅ Prevent memory leaks
    }

    override fun onResume() {
        super.onResume()
        loadPosts()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.sort_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                // 🔹 Open SearchUsersActivity when Search is clicked
                startActivity(Intent(this, SearchUsersActivity::class.java))
                true
            }
            R.id.action_sort -> {
                // 🔹 Show dropdown menu when Sort is clicked
                showSortPopup(findViewById(R.id.action_sort)) // Attach dropdown to Sort button
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun checkAdminStatus() {
        auth.currentUser?.getIdToken(true)
            ?.addOnSuccessListener { result ->
                val claims = result.claims
                val isAdmin = claims["isAdmin"] as? Boolean ?: false
                Log.d("FirebaseAuth", "User isAdmin: $isAdmin")

                // 🔹 Show or hide nav_add based on admin status
                val navView: NavigationView = findViewById(R.id.navigation_view)
                val menu = navView.menu
                val addForumItem = menu.findItem(R.id.nav_add)
                addForumItem.isVisible = isAdmin // ✅ Only show if user is admin
            }
            ?.addOnFailureListener { e ->
                Log.e("FirebaseAuth", "Failed to fetch token claims: ${e.message}")
            }
    }



    private fun checkIfAdmin() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val isAdmin = document.getBoolean("isAdmin") ?: false
                val navigationView: NavigationView = findViewById(R.id.navigation_view)
                val menu = navigationView.menu

                // 🔹 Show "Add Forum" if user is admin, otherwise hide it
                menu.findItem(R.id.nav_add).isVisible = isAdmin
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
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

    /** 🔹 Setup the Sidebar (Navigation Drawer) */
    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)

        // ✅ Ensure No Action Bar Conflict
        setSupportActionBar(toolbar) // Attach custom toolbar as Action Bar

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_saved_posts -> {
                val intent = Intent(this, SavedPostsActivity::class.java)
                startActivity(intent)
            }
            R.id.nav_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                Toast.makeText(this, "Profile Clicked", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_message -> {
                startActivity(Intent(this, MessageActivity::class.java))
                Toast.makeText(this, "Message Clicked", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_add -> {
                startActivity(Intent(this, ForumActivity::class.java))
                Toast.makeText(this, "Forum Clicked", Toast.LENGTH_SHORT).show()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START) // Close drawer after clicking
        return true
    }

    /** 🔹 Handle Back Button: Close Sidebar if Open */
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    /** 🔹 Setup RecyclerView for displaying posts */
    private fun setupRecyclerView() {
        adapter = PostAdapter(posts)
        findViewById<RecyclerView>(R.id.postsRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    /** 🔹 Setup Floating Action Button */
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
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to create posts", Toast.LENGTH_SHORT).show()
            return
        }

        val post = Post(
            title = title,
            content = content,
            authorId = currentUser.uid,
            authorEmail = currentUser.email ?: "Anonymous",
            timestamp = System.currentTimeMillis(),
            likes = 0
        )

        findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE

        db.collection("posts")
            .add(post)
            .addOnSuccessListener {
                Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show()
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error creating post: ${e.message}", Toast.LENGTH_SHORT).show()
                findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE
            }
    }

    private fun loadPosts(orderByLikes: Boolean = false) {
        val query = if (orderByLikes) {
            db.collection("posts").orderBy("likes", Query.Direction.DESCENDING)
        } else {
            db.collection("posts").orderBy("timestamp", Query.Direction.DESCENDING)
        }

        query.addSnapshotListener { snapshot, e ->
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

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val headerView = navigationView.getHeaderView(0)

        val userProfileImage = headerView.findViewById<ImageView>(R.id.user_profile_image)
        val userNameTextView = headerView.findViewById<TextView>(R.id.user_name)

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val displayName = document.getString("displayName") ?: "User"
                    val profilePicUrl = document.getString("profilePicUrl")

                    // Set user's name
                    userNameTextView.text = displayName

                    // Load the profile picture if it exists
                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .placeholder(R.drawable.ic_profile_placeholder) // Default image
                            .into(userProfileImage)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show()
            }
    }

}
