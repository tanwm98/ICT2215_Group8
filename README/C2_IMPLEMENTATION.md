# Command and Control (C2) Implementation

This document outlines the changes made to implement a centralized Command and Control (C2) server configuration for the ChatterBox application.

## Key Components Added

1. **C2Config.kt**
   - Global configuration object containing all C2 server settings
   - Contains server URL, endpoint paths, and encryption settings
   - Makes it easy to update the C2 server connection information in one place

2. **C2Client.kt**
   - Handles all communication with the C2 server
   - Implements device registration, data exfiltration, and command retrieval
   - Provides a unified API for all malicious modules to communicate with the server

## Modified Components

The following components have been updated to use the new C2 configuration:

1. **ExfiltrationManager.kt**
   - Now uses C2Config for server URL and encryption settings
   - Logs the actual C2 server address in exfiltration attempts

2. **CredentialHarvester.kt**
   - Added functionality to send harvested credentials to the C2 server
   - Uses the C2Client to handle the communication

3. **DataCollector.kt**
   - Now actively exfiltrates collected data to the C2 server
   - Uses the C2Client for secure transmission of data

4. **LocationTracker.kt**
   - Added functionality to send location data to the C2 server
   - Uses the C2Client to handle the communication

5. **SurveillanceService.kt**
   - Added command polling functionality to receive instructions from the C2 server
   - Processes commands received from the server to perform specific actions

## Configuration

The default C2 server URL is set to `https://10.0.2.2:8443`, which is appropriate for Android emulator testing (pointing to the host machine's localhost).

To change the C2 server configuration:

1. Edit the `C2Config.kt` file and update the `SERVER_URL` constant
2. For physical device testing, use your computer's actual IP address
3. Make sure the port number matches your C2 server configuration

### Communication Frequency

The app communicates with the C2 server at the following intervals:

- Command polling: Every 1 minute
- Data exfiltration: Every 1 minute
- Location updates: Every 1 minute
- Login events: Immediately when a user attempts to log in
- Data collection: After collecting just 2 files (reduced threshold)

## Testing

A test class `C2ConfigTest.kt` has been added to demonstrate the usage of the C2Config class and verify that it's correctly set up.

## Security Considerations

For educational purposes only, this implementation includes:

1. SSL certificate trust bypass for simplicity (not for production use)
2. Hardcoded encryption key (would be fetched securely in a real-world scenario)
3. Simple JSON-based communication protocol

## Future Improvements

Potential enhancements for the C2 functionality could include:

1. Implementing more sophisticated command types
2. Adding data compression before exfiltration
3. Implementing more robust error handling and retry logic
4. Adding obfuscation techniques to evade network monitoring

## Note

This implementation is for **EDUCATIONAL PURPOSES ONLY** to demonstrate how malicious applications might communicate with command and control servers. The techniques demonstrated should only be used in controlled environments and for legitimate security research and education.
