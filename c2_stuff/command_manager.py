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