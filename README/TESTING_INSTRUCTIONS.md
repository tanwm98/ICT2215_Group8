# Mobile Security Project - Enhanced C2 Testing Instructions

## Overview of Fixes Applied

1. **C2 Server Improvements**:
   - Added proper handling of data endpoints (/data/*)
   - Created directories for all data types on server startup
   - Added sample data generation for testing
   - Enhanced error handling and logging

2. **Mobile Client Improvements**:
   - Fixed C2Client with better error handling and extended timeouts
   - Added multiple fallback URLs for better connectivity
   - Added verbose logging for easier debugging
   - Ensured SurveillanceService starts automatically
   - Added a debug button to manually test C2 communication
   - Updated endpoint handling to try multiple server addresses

## Testing the System

### Step 1: Start the C2 Server

```
cd C:\Users\joelg\Desktop\mob\c2_stuff
python c2.py
```

The server will:
- Start the C2 server on port 42069
- Start the admin console on port 8080
- Create necessary directories and sample data

### Step 2: Verify the Admin Console

Open a browser and go to: `http://localhost:8080`

You should see:
- The admin panel with connected devices (including a sample device)
- Sample data in various categories

### Step 3: Run the Mobile App

Launch the app in an emulator or on a physical device. 

1. The app will automatically start the SurveillanceService in the background
2. The service will attempt to connect to the C2 server using multiple URLs
3. Look for notifications showing successful or failed communication attempts
4. Check the log output in Android Studio for detailed connection information

### Step 4: Manual Testing 

In the app, there is a hidden test button (labeled "Help") that will:
- Trigger a direct test of the C2 connection
- Send test data to the C2 server
- Check for commands from the C2 server

This is useful for debugging if the automated connections aren't working.

### Step 5: Check the Logs

Monitor the server logs to see incoming connections:
- Check `c2_server.log` for server-side information
- Check Android logcat for app-side logs (filter by "C2Client" or "Surveillance")

### Step 6: Troubleshooting Network Issues

If you're still experiencing connectivity issues:

1. **Check Firewall Settings**:
   - Ensure port 42069 is open on your computer
   - Try disabling the firewall temporarily for testing

2. **Check IP Addresses**:
   - If using an emulator, 10.0.2.2 should point to the host machine
   - If using a physical device, you need to use your actual computer's IP address
   - Update the IP addresses in C2Config.kt as needed

3. **Test Direct Connection**:
   - Use the test_client.py script to verify the server works correctly:
     ```
     python test_client.py --server http://127.0.0.1:42069
     ```

4. **Network Debugging**:
   - Use tools like Wireshark to monitor network traffic
   - Check if packets are being sent and received properly

## Key Files Modified

1. **C2 Server-side**:
   - `c2.py` - Added proper data handling for web admin console
   - `test_client.py` - Created for testing server connections

2. **Android App-side**:
   - `C2Client.kt` - Improved connection handling and error logging
   - `C2Config.kt` - Added multiple server URLs for better connectivity
   - `SurveillanceService.kt` - Enhanced to handle test commands and show status
   - `HomeActivity.kt` - Modified to start service and provide test button

## Sending Commands from the Admin Console

1. Open the admin console at `http://localhost:8080`
2. When a device connects, it will show up in the "Connected Devices" list
3. Use the buttons under each device to send commands:
   - "Collect Device Info" - Get basic device information
   - "Capture Screenshot" - Simulates taking a screenshot
   - "Get Location" - Tries to get device location

## Common Error Scenarios

1. **404 Errors** - These are now fixed by properly handling the /data/* endpoints
2. **Connection Refused** - Check your firewall and make sure the server is running
3. **Connection Timeout** - May indicate network issues between device and server
4. **SSL Errors** - The app should try both HTTPS and HTTP connections

## Test Data Visualization

After successful connection and data exfiltration, you can view the collected data in the admin console. Click on any data type button (Credentials, Keylog, Location, etc.) to view collected information.
