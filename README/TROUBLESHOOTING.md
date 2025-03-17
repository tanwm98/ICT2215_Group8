# Troubleshooting Common Errors

If you encounter compilation errors after implementing the malicious features, here are some common fixes:

## BuildConfig Reference Errors

If you see errors like:
```
Unresolved reference 'BuildConfig'.
```

### Fix:
1. Replace all references to `BuildConfig.VERSION_NAME` with a hardcoded string like `"1.0"` or 
2. Import the BuildConfig properly:
   ```kotlin
   import com.example.ChatterBox.BuildConfig
   ```

## Resource (R) Reference Errors

If you see errors like:
```
Unresolved reference 'R'.
```

### Fix:
1. Replace all references to resource strings with hardcoded strings
2. Make sure the correct import statement is added:
   ```kotlin
   import com.example.ChatterBox.R
   ```
3. Ensure the resource IDs actually exist in your project

## Notification Building Errors

If you see errors related to notifications or priorities:

### Fix:
1. Use a property assignment for priority instead of a method:
   ```kotlin
   val builder = NotificationCompat.Builder(context, channelId)
   builder.priority = NotificationCompat.PRIORITY_LOW
   ```

2. Use a system icon if your custom icons aren't found:
   ```kotlin
   .setSmallIcon(android.R.drawable.ic_dialog_info)
   ```

## Missing XML Resources

If you get errors about missing XML resources:

### Fix:
1. Make sure you've copied the `accessibility_service_config.xml` file to your project's `res/xml` directory
2. Check that string resources referenced in code are defined in `strings.xml`

## LocationManager Issues

If you have problems with LocationManager:

### Fix:
1. Add the permission checks properly before accessing location services:
   ```kotlin
   if (ActivityCompat.checkSelfPermission(context, 
           Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
       // Access location services here
   }
   ```

## Missing Imports

If you're missing important imports:

### Fix:
1. Make sure your app's build.gradle file includes these dependencies:
   ```gradle
   implementation 'androidx.core:core-ktx:1.x.x'
   implementation 'androidx.appcompat:appcompat:1.x.x'
   implementation 'com.google.android.material:material:1.x.x'
   ```

## General Troubleshooting Steps

1. Clean and rebuild your project: 
   - In Android Studio, go to Build → Clean Project, then Build → Rebuild Project

2. Invalidate caches and restart:
   - In Android Studio, go to File → Invalidate Caches / Restart...

3. Make sure you have the latest version of Android Studio and Gradle

4. Check your project's minSdkVersion and targetSdkVersion in build.gradle to ensure compatibility

5. Remove any code that uses APIs not available in your minSdkVersion by adding version checks:
   ```kotlin
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
       // Code that uses API level 26+ (Oreo) features
   } else {
       // Fallback for older Android versions
   }
   ```
