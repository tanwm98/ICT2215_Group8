import logging
import os


def setup_logging():
    # Configure logging
    logging.basicConfig(
        level=logging.DEBUG,  # Change from INFO to DEBUG for more verbose output
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        handlers=[
            logging.FileHandler("c2_server.log"),
            logging.StreamHandler()
        ]
    )
    logger = logging.getLogger("C2Server")
    return logger

# Create a global logger instance
logger = setup_logging()