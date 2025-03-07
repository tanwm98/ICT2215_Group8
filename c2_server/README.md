# ChatterBox Malicious App C2 Server

This is a simple Command and Control server for receiving data exfiltrated from the ChatterBox malicious application. It's designed for educational purposes only as part of the ICT2215 Mobile Security coursework.

## Features

- Receives location data, audio recordings, and photos from compromised Android devices
- Decrypts and deobfuscates received data
- Stores collected data organized by device ID and data type
- Logs all communication for analysis
- Simple Flask-based HTTP server

## Setup Instructions

### Requirements

- Python 3.6+
- Flask
- Internet-accessible machine (or port forwarding/tunneling)

### Installation

1. Ensure Python 3.6+ is installed on your system
2. Install required packages: `pip install flask`
3. Clone or download this repository

### Running the Server

```bash
# Start the server on default port 8080
python c2_server.py

# Start on a specific port
python c2_server.py --port 5000

# Enable debug mode for development
python c2_server.py --debug
```

## Data Collection

The server collects data in the `collected_data` directory, organized as follows:

```
collected_data/
├── [device_id]/
│   ├── location/
│   │   └── [timestamp]_location.json
│   ├── photo/
│   │   └── [timestamp]_photo.jpg
│   ├── audio/
│   │   └── [timestamp]_audio.3gp
│   └── keyboard/
│       └── [timestamp]_keyboard.json
```

## Security Note

This server and the associated Android application are created for educational purposes only as part of the ICT2215 Mobile Security module. Using these tools to collect data from devices without explicit permission is illegal and unethical.

## Coursework Integration

This C2 server is designed to work with the ChatterBox Android application created for the coursework. The server expects data in the format specified by the application's data collection modules.

## License

This project is part of academic coursework and is not licensed for commercial use.
