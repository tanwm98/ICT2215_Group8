from Crypto.Cipher import AES
from Crypto.Random import get_random_bytes
import base64

def pad(data):
    # PKCS5Padding (same as PKCS7 with 16-byte block)
    padding_length = 16 - (len(data) % 16)
    return data + bytes([padding_length] * padding_length)

def encrypt_ip(ip, key):
    key_bytes = key.encode('utf-8')  # Must be 16, 24, or 32 bytes for AES
    iv = get_random_bytes(16)

    cipher = AES.new(key_bytes, AES.MODE_CBC, iv)
    padded_ip = pad(ip.encode('utf-8'))
    encrypted = cipher.encrypt(padded_ip)

    # Prepend IV to encrypted data
    encrypted_with_iv = iv + encrypted

    # Base64 encode
    encoded = base64.b64encode(encrypted_with_iv).decode('utf-8')
    return encoded

ip_address = "http://192.168.251.254:42069/api/" # Edit this and run the file
aes_key = "ThisIsAFakeKey16"  # Must match your Android key in CloudUploader.kt

# 🚀 Run it
encrypted_base64 = encrypt_ip(ip_address, aes_key)
print("Encrypted Base64 for Android:") # Copy paste this on ENCRYPTED_API_ENDPOINT
print(encrypted_base64)
