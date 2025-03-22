import firebase_admin
import json
import log_util
import os
from firebase_admin import credentials
from firebase_admin import db
from log_util import logger

FIREBASE_CREDS = "C:\\Users\\tanwm\\Desktop\\databse-7d740-firebase-adminsdk-fbsvc-7aba2c03f2.json"

def initialize_firebase(cred_file_path="C:\\Users\\tanwm\\Desktop\\databse-7d740-firebase-adminsdk-fbsvc-7aba2c03f2.json"):
    """Initialize Firebase with credentials file or environment variable"""
    try:
        # Try to use environment variable first
        firebase_cred_json = os.environ.get('FIREBASE_CREDS')

        if firebase_cred_json:
            # Parse JSON from environment variable
            cred_dict = json.loads(firebase_cred_json)
            cred = credentials.Certificate(cred_dict)
        elif cred_file_path and os.path.exists(cred_file_path):
            # Use provided file path
            cred = credentials.Certificate(cred_file_path)
        else:
            logger.error(f"Firebase credentials not found")
            return False

        firebase_admin.initialize_app(cred, {
            'databaseURL': 'https://databse-7d740-default-rtdb.asia-southeast1.firebasedatabase.app/'
        })
        logger.info("Firebase initialized successfully")
        return True
    except Exception as e:
        logger.error(f"Firebase initialization error: {str(e)}")
        return False


def get_reference(path):
    """Get a Firebase database reference"""
    return db.reference(path)


def push_data(path, data):
    """Push data to Firebase at the specified path"""
    try:
        ref = get_reference(path)
        return ref.push().set(data)
    except Exception as e:
        logger.error(f"Error pushing data to Firebase: {str(e)}")
        return None


def update_data(path, data):
    """Update data in Firebase at the specified path"""
    try:
        ref = get_reference(path)
        return ref.update(data)
    except Exception as e:
        logger.error(f"Error updating data in Firebase: {str(e)}")
        return None


def get_data(path):
    """Get data from Firebase at the specified path"""
    try:
        ref = get_reference(path)
        return ref.get()
    except Exception as e:
        logger.error(f"Error getting data from Firebase: {str(e)}")
        return None


def set_data(path, data):
    """Set data in Firebase at the specified path"""
    try:
        ref = get_reference(path)
        return ref.set(data)
    except Exception as e:
        logger.error(f"Error setting data in Firebase: {str(e)}")
        return None