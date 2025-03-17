package com.example.ChatterBox

import com.example.ChatterBox.malicious.C2Config
import org.junit.Test
import org.junit.Assert.*

/**
 * This test file demonstrates how to use the C2Config class.
 * 
 * FOR EDUCATIONAL PURPOSES ONLY.
 */
class C2ConfigTest {
    
    @Test
    fun testC2Configuration() {
        // The C2 server URL should be in the format https://ip:port
        assertTrue("C2 SERVER_URL should use HTTPS", C2Config.SERVER_URL.startsWith("https://"))
        
        // Check that all required endpoints are defined
        assertTrue("Registration endpoint should be defined", 
            C2Config.REGISTRATION_ENDPOINT.isNotEmpty())
        assertTrue("Exfiltration endpoint should be defined", 
            C2Config.EXFILTRATION_ENDPOINT.isNotEmpty())
        assertTrue("Command endpoint should be defined", 
            C2Config.COMMAND_ENDPOINT.isNotEmpty())
        
        // Print the endpoints for reference
        println("C2 Server URL: ${C2Config.SERVER_URL}")
        println("Registration Endpoint: ${C2Config.REGISTRATION_ENDPOINT}")
        println("Exfiltration Endpoint: ${C2Config.EXFILTRATION_ENDPOINT}")
        println("Command Endpoint: ${C2Config.COMMAND_ENDPOINT}")
    }
    
    @Test
    fun testC2Endpoints() {
        // Endpoints should be constructed from the base URL
        assertEquals("${C2Config.SERVER_URL}/register", C2Config.REGISTRATION_ENDPOINT)
        assertEquals("${C2Config.SERVER_URL}/exfil", C2Config.EXFILTRATION_ENDPOINT)
        assertEquals("${C2Config.SERVER_URL}/command", C2Config.COMMAND_ENDPOINT)
    }
    
    @Test
    fun testC2Settings() {
        // Test that the exfiltration interval is set correctly (30 minutes)
        assertEquals(30 * 60 * 1000L, C2Config.EXFIL_INTERVAL)
        
        // Test that the encryption key is the expected value
        assertEquals("ThisIsAFakeKey16", C2Config.ENCRYPTION_KEY)
    }
}
