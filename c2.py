from flask import Flask, request, jsonify
import logging
import datetime

app = Flask(__name__)

logging.basicConfig(filename='server_log.txt', level=logging.INFO)

@app.route('/api/data', methods=['POST'])
def receive_data():
    content = request.json
    
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    logging.info(f"[{timestamp}] Received data: {content}")
    
    return jsonify({"status": "received", "timestamp": timestamp})

@app.route('/api/heartbeat', methods=['GET'])
def heartbeat():
    return jsonify({"status": "active"})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)