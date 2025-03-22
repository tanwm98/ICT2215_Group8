import datetime
import log_util
from firebase_client import get_reference, get_data, update_data, set_data
from log_util import logger


class DeviceManager:
    """
    Manage device registration and tracking
    """

    @staticmethod
    def register_device(device_id, device_info=None):
        """Register a new device or update existing device info"""
        try:
            if not device_id:
                return False

            # Validate and ensure device_info is properly structured
            if device_info is None:
                device_info = {}

            # Ensure we have a valid device model
            if not device_info.get('model'):
                device_info['model'] = 'Unknown Device'

            # Create device data structure
            device_data = {
                "device_id": device_id,
                "device_info": device_info,
                "registration_time": datetime.datetime.now().isoformat(),
                "last_seen": datetime.datetime.now().isoformat(),
                "status": "active"
            }

            # Check if device already exists
            existing_device = get_data(f'devices/{device_id}')
            if existing_device:
                # Update last seen and device info if provided
                update_data(f'devices/{device_id}', {
                    "last_seen": device_data["last_seen"],
                    "device_info": device_info or existing_device.get("device_info", {}),
                    "status": "active"
                })
            else:
                # Create new device entry
                set_data(f'devices/{device_id}', device_data)

                # Initialize command node
                set_data(f'commands/{device_id}', {
                    "pending": [],
                    "executed": []
                })

            logger.info(f"Device registered/updated: {device_id}")
            return True
        except Exception as e:
            logger.error(f"Error registering device: {str(e)}")
            return False

    @staticmethod
    def update_device_last_seen(device_id):
        """Update the last seen timestamp for a device"""
        try:
            if not device_id or device_id == "unknown_device":
                return False

            # Get current device data
            device_data = get_data(f'devices/{device_id}')

            current_time = datetime.datetime.now().isoformat()

            if device_data:
                # Update last seen
                update_data(f'devices/{device_id}', {
                    "last_seen": current_time,
                    "status": "active"
                })

                # Calculate and update device statistics
                try:
                    last_seen_iso = device_data.get("last_seen")
                    if last_seen_iso:
                        last_seen = datetime.datetime.fromisoformat(last_seen_iso)
                        current = datetime.datetime.fromisoformat(current_time)
                        time_diff = (current - last_seen).total_seconds()

                        stats = device_data.get("statistics", {})
                        if not stats:
                            stats = {"check_ins": 0, "avg_interval": 0}

                        check_ins = stats.get("check_ins", 0) + 1
                        avg_interval = stats.get("avg_interval", 0)

                        # Calculate new average interval
                        if check_ins > 1:
                            avg_interval = ((avg_interval * (check_ins - 1)) + time_diff) / check_ins

                        # Update statistics
                        update_data(f'devices/{device_id}/statistics', {
                            "check_ins": check_ins,
                            "avg_interval": avg_interval,
                            "last_interval": time_diff
                        })
                except Exception as stat_error:
                    logger.error(f"Error updating device statistics: {str(stat_error)}")

                return True
            else:
                # Device doesn't exist yet
                return DeviceManager.register_device(device_id)

        except Exception as e:
            logger.error(f"Error updating device last seen: {str(e)}")
            return False

    @staticmethod
    def get_device(device_id):
        """Get device information"""
        try:
            device_data = get_data(f'devices/{device_id}')

            # Check if device is offline based on last_seen timestamp
            if device_data and 'last_seen' in device_data:
                try:
                    last_seen = datetime.datetime.fromisoformat(device_data['last_seen'])
                    current_time = datetime.datetime.now()
                    time_diff = (current_time - last_seen).total_seconds()

                    # Mark as offline if not seen in 15 minutes
                    if time_diff > 900:  # 15 minutes
                        device_data['status'] = 'offline'
                        # Update status in database
                        update_data(f'devices/{device_id}', {
                            "status": "offline"
                        })
                except Exception as e:
                    logger.error(f"Error checking device status: {str(e)}")

            return device_data
        except Exception as e:
            logger.error(f"Error getting device info: {str(e)}")
            return None

    @staticmethod
    def get_all_devices():
        """Get all registered devices"""
        try:
            devices_data = get_data('devices') or {}
            current_time = datetime.datetime.now()

            # Convert to list with device_id included
            devices = []
            for device_id, device_data in devices_data.items():
                # Add device_id to the data
                device_data['device_id'] = device_id

                # Check and update device online status
                if 'last_seen' in device_data:
                    try:
                        last_seen = datetime.datetime.fromisoformat(device_data['last_seen'])
                        time_diff = (current_time - last_seen).total_seconds()

                        # Update status based on last seen time
                        if time_diff > 900:  # 15 minutes
                            device_data['status'] = 'offline'
                        else:
                            device_data['status'] = 'active'
                    except Exception as e:
                        logger.error(f"Error calculating device status: {str(e)}")
                        device_data['status'] = 'unknown'
                else:
                    device_data['status'] = 'unknown'

                # Ensure device_info exists
                if 'device_info' not in device_data:
                    device_data['device_info'] = {
                        'model': 'Unknown Device',
                        'android_version': 'Unknown'
                    }

                devices.append(device_data)

            return devices
        except Exception as e:
            logger.error(f"Error getting all devices: {str(e)}")
            return []