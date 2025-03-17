# C2 Server for Mobile Security Project

This is a Command and Control (C2) server for educational purposes, designed to demonstrate mobile security concepts. The server receives data from the mobile client and allows you to view it through a web interface.

## Files

- `c2.py` - The main C2 server application
- `test_client.py` - A test client to simulate data from a mobile device
- `server.crt` / `server.key` - SSL certificate files (generated automatically)

## Setup Instructions

1. Make sure you have Python 3.6+ installed
2. Install the required dependencies:
   ```
   pip install cryptography
   ```

## Running the Server

To start the C2 server:

```
python c2.py
```

This will:
- Start the C2 server on port 42069 (default)
- Start the web admin console on port 8080
- Create necessary directories for data storage
- Generate sample data for testing

### Additional Options

- `--port PORT` - Change the server port (default: 42069)
- `--no-ssl` - Disable SSL (not recommended for production)
- `--cert FILE` - Specify a custom SSL certificate file
- `--key FILE` - Specify a custom SSL key file

Example:
```
python c2.py --port 8888 --no-ssl
```

## Testing the Server

You can use the test client to simulate mobile device data:

```
python test_client.py
```

This will:
- Register a test device with the C2 server
- Send sample data of each type (credentials, keylog, location, etc.)
- Check for commands from the C2 server

### Test Client Options

- `--server URL` - Change the server URL (default: http://127.0.0.1:42069)
- `--skip-registration` - Skip device registration
- `--data-type TYPE` - Send only a specific data type
- `--commands` - Only check for commands

Examples:
```
python test_client.py --server https://your-server:port
python test_client.py --data-type credentials
python test_client.py --commands
```

## Accessing the Web Interface

The web admin console is available at:

```
http://localhost:8080
```

This interface allows you to:
- View connected devices
- Send commands to devices
- View exfiltrated data by type

## Mobile App Configuration

To configure the mobile app to connect to your C2 server:

1. Update the SERVER_URL in `C2Config.kt`:
   - For emulator: `https://10.0.2.2:42069` (points to host machine)
   - For physical device: Use your computer's IP address, e.g., `https://192.168.1.100:42069`

## Troubleshooting

If you encounter any issues:

1. Check the `c2_server.log` file for error messages
2. Ensure required ports (42069 and 8080) are not blocked by a firewall
3. For SSL issues, try running with `--no-ssl` option
4. Verify network connectivity between the mobile device and C2 server
