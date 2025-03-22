#!/usr/bin/env python3
import argparse
import log_util
from log_util import setup_logging
from http_server import run_c2_server
from firebase_client import initialize_firebase
import ssl

# Constants
DEFAULT_PORT = 42069

import ssl


def main():
    parser = argparse.ArgumentParser(description="Server")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT,
                        help=f"Port to listen on (default: {DEFAULT_PORT})")
    parser.add_argument("--no-ssl", action="store_true",
                        help="Disable SSL (not recommended)")
    parser.add_argument("--admin-port", type=int, default=8080,
                        help="Port for admin console (default: 8080)")
    parser.add_argument("--firebase-cred", type=str,
                        default="C:\\Users\\tanwm\\Desktop\\databse-7d740-firebase-adminsdk-fbsvc-7aba2c03f2.json",
                        help="Path to Firebase credentials file")
    parser.add_argument("--cert-path", type=str, default="/etc/letsencrypt/live/group8.mooo.com/fullchain.pem",
                        help="Path to SSL certificate")
    parser.add_argument("--key-path", type=str, default="/etc/letsencrypt/live/group8.mooo.com/privkey.pem",
                        help="Path to SSL private key")
    args = parser.parse_args()

    logger = setup_logging()

    if not initialize_firebase(args.firebase_cred):
        logger.error("Failed to initialize Firebase, exiting.")
        return

    run_c2_server(args.port, args.admin_port, not args.no_ssl, args.cert_path, args.key_path)


if __name__ == "__main__":
    main()