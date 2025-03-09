# Malicious Functionality - EDUCATIONAL PURPOSE ONLY

This code demonstrates various malicious techniques that can be used to compromise user privacy and security in Android applications. This code is for **EDUCATIONAL PURPOSES ONLY** and should NEVER be used in real applications or with intent to harm users.

## Components

### 1. KeyloggerService

An `AccessibilityService` that:
- Logs all text input from the user
- Captures text displayed on screen
- Monitors app navigation
- Writes captured data to external storage

### 2. SurveillanceService

A background service that:
- Takes periodic screenshots
- Captures photos using the camera
- Records audio using the microphone
- Logs the user's location
- Stores all collected data locally

### 3. ExfiltrationManager

A utility that:
- Collects stored surveillance data
- Compresses and encrypts it
- Simulates sending to a command & control server

### 4. BootReceiver

A broadcast receiver that:
- Starts malicious services when the device boots
- Ensures persistence of surveillance

## Warning Signs (for educational purposes)

These are red flags that may indicate malicious behavior in apps:

1. **Excessive Permissions**: Requesting permissions unrelated to app functionality
2. **Background Services**: Services that run even when the app is closed
3. **Accessibility Services**: These can read sensitive data from other apps
4. **Boot Receivers**: Components that activate when device starts
5. **Obfuscated Code**: Code that is intentionally difficult to read/analyze
6. **Hidden Activities**: UI elements not visible to the user
7. **Unusual Network Traffic**: Especially to unknown servers
8. **Battery/Resource Drain**: Malicious activities often consume resources

## Defensive Measures

To protect against such threats:
- Only install apps from trusted sources
- Review permissions before granting them
- Use security solutions that detect malicious behavior
- Keep your device updated with security patches
- Be wary of apps requesting accessibility services

## Code Objectives

This code was created for a mobile security class to demonstrate:
1. How malicious code can be integrated into legitimate-looking apps
2. Common techniques used by spyware and surveillanceware
3. How permissions can be abused
4. Warning signs of potentially malicious apps

**DO NOT USE THIS CODE FOR ANY MALICIOUS PURPOSE**
