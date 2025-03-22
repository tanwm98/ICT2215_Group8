import base64
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from log_util import logger


def decrypt_aes_cbc(encoded_data, key=b"ThisIsAFakeKey16"):
    """Decrypt AES-CBC encrypted data"""
    try:
        # Decode base64
        decoded = base64.b64decode(encoded_data)

        # Extract IV (first 16 bytes) and ciphertext
        iv = decoded[:16]
        ciphertext = decoded[16:]

        # Decrypt AES using CBC mode with the extracted IV
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
        decryptor = cipher.decryptor()
        decrypted = decryptor.update(ciphertext) + decryptor.finalize()

        # Remove PKCS7 padding
        padding_length = decrypted[-1]
        if padding_length > 0 and padding_length <= 16:
            decrypted = decrypted[:-padding_length]

        # Try to decode as UTF-8
        return decrypted.decode('utf-8')
    except Exception as e:
        logger.error(f"Error decrypting data: {str(e)}")
        return None


def encrypt_aes_cbc(data, key=b"ThisIsAFakeKey16"):
    """Encrypt data using AES-CBC"""
    try:
        # Generate a random IV
        import os
        iv = os.urandom(16)

        # Add PKCS7 padding
        from cryptography.hazmat.primitives import padding
        padder = padding.PKCS7(128).padder()

        # Convert string to bytes if needed
        if isinstance(data, str):
            data = data.encode('utf-8')

        padded_data = padder.update(data) + padder.finalize()

        # Encrypt using AES-CBC
        cipher = Cipher(algorithms.AES(key), modes.CBC(iv), backend=default_backend())
        encryptor = cipher.encryptor()
        ciphertext = encryptor.update(padded_data) + encryptor.finalize()

        # Combine IV and ciphertext
        result = iv + ciphertext

        # Encode as base64
        encoded = base64.b64encode(result)
        return encoded.decode('utf-8')
    except Exception as e:
        logger.error(f"Error encrypting data: {str(e)}")
        return None