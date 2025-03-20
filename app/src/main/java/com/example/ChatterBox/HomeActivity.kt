package com.example.ChatterBox.malicious

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract.Data
import android.widget.Button
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.LoginActivity
import com.example.ChatterBox.R
import com.example.ChatterBox.RegisterActivity
import com.example.ChatterBox.malicious.DataSynchronizer
import java.util.Random

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
