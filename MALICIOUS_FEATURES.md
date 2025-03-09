# Malicious Features - Educational Demonstration Only

**IMPORTANT NOTICE:** This code is for **EDUCATIONAL PURPOSES ONLY**. These features demonstrate common techniques used by malicious mobile applications. This code should only be run in controlled environments on test devices, never in production or on personal devices.

## Implemented Malicious Features

### 1. Credential Harvesting
- Intercepts login credentials during sign-in attempts
- Captures registration information including emails, passwords, and personal details
- Stores credentials in a hidden location on device storage

### 2. Background Surveillance
- **Keylogging**: Uses accessibility services to capture all text input across the device
- **Screen Recording**: Captures screen content every 30 seconds
- **Camera Access**: Takes photos using the device camera without user knowledge
- **Audio Recording**: Records audio from the device microphone periodically
- **Location Tracking**: Monitors and logs user location even when app is closed

### 3. Data Exfiltration
- Simulates transmitting collected data to a command & control server
- Encrypts and compresses harvested data
- Schedules regular data uploads in the background

### 4. Persistence
- Uses boot receiver to restart malicious services when device reboots
- Continues surveillance activities even after app is closed

## Code Implementation

### Main Components

1. **SurveillanceService.kt**
   - Foreground service that manages all surveillance activities
   - Schedules periodic collection of audio, photos, and screen content
   - Uses low-priority notifications to avoid user suspicion

2. **KeyloggerService.kt**
   - Accessibility service that captures text input and screen content
   - Monitors app navigation and sensitive fields
   - Logs input from other applications including passwords

3. **LocationTracker.kt**
   - Tracks user location in the background
   - Logs detailed location history including coordinates, accuracy, and altitude
   - Monitors when location services are enabled/disabled

4. **CredentialHarvester.kt**
   - Creates a structured storage system for stolen credentials
   - Associates user credentials with device information
   - Creates backup copies to prevent data loss

5. **ExfiltrationManager.kt**
   - Manages the transmission of harvested data
   - Simulates encryption and compression for data exfiltration
   - Schedules regular uploads to avoid detection

6. **BootReceiver.kt**
   - Ensures malicious services start when device boots
   - Re-establishes surveillance after device restart

## Permission Abuse

This demonstration shows how malicious apps can abuse the following permissions:

- `RECORD_AUDIO`: For unauthorized audio surveillance
- `CAMERA`: For covert photo and video capture
- `ACCESS_FINE_LOCATION`: For detailed location tracking
- `ACCESSIBILITY_SERVICE`: For keylogging and screen monitoring
- `READ/WRITE_EXTERNAL_STORAGE`: For storing harvested data
- `RECEIVE_BOOT_COMPLETED`: For persistence after device restart

## Educational Value

The code demonstrates:

1. How permissions can be abused beyond their intended purposes
2. Social engineering techniques to trick users into granting sensitive permissions
3. How legitimate app functionality can mask malicious behavior
4. Techniques for hiding malicious activities from user awareness
5. How mobile malware achieves persistence

## Warning Signs

For educational purposes, here are red flags that might indicate malicious behavior:

1. Apps requesting excessive permissions unrelated to their core functionality
2. Battery drain and increased data usage
3. Unexplained storage usage
4. Apps pressuring users to enable accessibility services
5. Services running in the background after app closure
6. Apps insisting on running at device startup

## Defense Recommendations

To protect against these types of attacks:

1. Only install apps from trusted sources
2. Review permissions carefully before granting them
3. Use security solutions that detect malicious behavior
4. Regularly audit installed apps and enabled permissions
5. Be cautious of apps requesting accessibility or device admin privileges
6. Keep devices updated with security patches

## FOR EDUCATIONAL USE ONLY

This implementation is purely for learning about mobile security vulnerabilities. Using this code for actual malicious purposes is illegal and unethical.
