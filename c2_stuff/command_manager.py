import datetime
import uuid
from firebase_client import get_reference, get_data, update_data, set_data
from log_util import logger
import firebase_admin
from firebase_admin import messaging
from firebase_admin import credentials


# Initialize Firebase
def initialize_firebase_messaging(cred_file_path):
    if not firebase_admin._apps:
        cred = credentials.Certificate(cred_file_path)
        firebase_admin.initialize_app(cred)

    logger.info("Firebase Messaging initialized")


# Send command via FCM
# Add this to your command_manager.py file

# Update the send_command_via_fcm function to handle the audio command properly
def send_command_via_fcm(device_id, command_data):
    try:
        # Get device FCM token from Firebase
        fcm_token = get_data(f'devices/{device_id}/fcm_token')

        if not fcm_token:
            logger.warning(f"No FCM token found for device {device_id}")
            return False

        # Create data payload based on command type
        data_payload = {
            'type': 'command',
            'command_id': command_data.get('id'),
            'command_type': command_data.get('command'),
            'timestamp': str(int(datetime.datetime.now().timestamp() * 1000))
        }

        # Add command-specific parameters
        if command_data.get('command') == 'get_audio':
            # Include duration if specified
            if 'duration' in command_data:
                data_payload['duration'] = str(command_data.get('duration'))
            else:
                data_payload['duration'] = '30'  # Default duration

        # Create message with data payload
        message = messaging.Message(
            data=data_payload,
            token=fcm_token,
        )

        # Send message
        response = messaging.send(message)
        logger.info(f"Successfully sent FCM command to {device_id}: {response}")

        return True
    except Exception as e:
        logger.error(f"Error sending FCM command: {str(e)}")
        return False


class CommandManager:
    """
    Manage commands for connected devices using Firebase
    """

    @staticmethod
    def add_command(device_id, command_data):
        try:
            if not isinstance(command_data, dict):
                command_data = {"command": command_data}

            command_data["created_at"] = datetime.datetime.now().isoformat()
            command_data["id"] = str(uuid.uuid4())

            # Try to send via FCM first
            fcm_success = send_command_via_fcm(device_id, command_data)

            # Always add to pending commands for fallback polling
            command_ref = get_reference(f'commands/{device_id}')
            commands_data = get_data(f'commands/{device_id}') or {"pending": [], "executed": []}
            pending_commands = commands_data.get("pending", [])
            pending_commands.append(command_data)

            update_data(f'commands/{device_id}', {
                "pending": pending_commands
            })

            logger.info(
                f"Added command to device {device_id}: {command_data} (FCM delivery: {'success' if fcm_success else 'fallback to polling'})")
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
    @staticmethod
    def mark_command_executed(device_id, command_id, result=None):
        try:
            commands_data = get_data(f'commands/{device_id}') or {"pending": [], "executed": []}
            pending_commands = commands_data.get("pending", [])
            executed_commands = commands_data.get("executed", [])
            executed_command = None

            # First, try to find the command in the pending list
            for cmd in pending_commands:
                if cmd.get('id') == command_id:
                    executed_command = cmd
                    pending_commands.remove(cmd)
                    break

            # If not found in pending, search in executed commands
            if not executed_command:
                for cmd in executed_commands:
                    if cmd.get('id') == command_id:
                        executed_command = cmd
                        break

            if executed_command:
                executed_command["executed_at"] = datetime.datetime.now().isoformat()
                if result:
                    executed_command["result"] = result

                # If it wasn't in executed, add it
                if executed_command not in executed_commands:
                    executed_commands.append(executed_command)

                update_data(f'commands/{device_id}', {
                    "pending": pending_commands,
                    "executed": executed_commands
                })

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
            logger.info(f"Getting command results for device: {device_id}")

            # Get command results from Firebase
            results_data = get_data(f'command_results/{device_id}') or {}
            logger.info(f"Found {len(results_data)} command result entries")

            # Get command info from executed commands
            commands_data = get_data(f'commands/{device_id}/executed') or []
            logger.info(f"Found {len(commands_data)} executed commands")

            # If no results found in command_results, use the executed commands directly
            if not results_data and commands_data:
                logger.info("No separate command results found, using executed commands")
                combined_results = []
                for cmd in commands_data:
                    command_id = cmd.get('id', 'unknown')
                    result = cmd.get('result', {})

                    combined_results.append({
                        "command_id": command_id,
                        "command_type": cmd.get('command', 'unknown'),
                        "executed_at": cmd.get('executed_at', 'unknown'),
                        "result": result
                    })

                # Sort by execution time (newest first)
                combined_results.sort(key=lambda x: x.get('executed_at', ''), reverse=True)
                return combined_results

            # Combine data from both sources
            combined_results = []

            # First add results from command_results
            for command_id, result_data in results_data.items():
                # Find matching command
                command_info = next((cmd for cmd in commands_data if cmd.get('id') == command_id), {})

                result_entry = {
                    "command_id": command_id,
                    "command_type": command_info.get('command', 'unknown'),
                    "executed_at": result_data.get('timestamp', command_info.get('executed_at', 'unknown')),
                    "result": result_data.get('result', {})
                }

                combined_results.append(result_entry)

            # Then add executed commands that might not have results yet
            for cmd in commands_data:
                command_id = cmd.get('id', 'unknown')

                # Skip if we already have this command result
                if any(r.get('command_id') == command_id for r in combined_results):
                    continue

                # Add basic result entry
                combined_results.append({
                    "command_id": command_id,
                    "command_type": cmd.get('command', 'unknown'),
                    "executed_at": cmd.get('executed_at', 'unknown'),
                    "result": cmd.get('result', {"status": "executed", "details": "No result data available"})
                })

            # Sort by execution time (newest first)
            combined_results.sort(key=lambda x: x.get('executed_at', ''), reverse=True)

            logger.info(f"Returning {len(combined_results)} combined command results")
            return combined_results
        except Exception as e:
            logger.error(f"Error getting command results: {str(e)}")
            logger.exception("Stack trace:")
            return []