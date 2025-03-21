import re

def sanitize_key(key):
    # Replace characters that Firebase doesn't allow with an underscore
    return re.sub(r'[.#$\[\]]', '_', key)

def sanitize_dict(data):
    """
    Recursively sanitize keys in a dictionary.
    """
    if isinstance(data, dict):
        sanitized = {}
        for key, value in data.items():
            new_key = sanitize_key(key)
            sanitized[new_key] = sanitize_dict(value)
        return sanitized
    elif isinstance(data, list):
        return [sanitize_dict(item) for item in data]
    else:
        return data
