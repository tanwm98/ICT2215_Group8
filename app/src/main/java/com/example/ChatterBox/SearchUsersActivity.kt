package com.example.ChatterBox

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SearchUsersActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_users)

        val searchInput = findViewById<EditText>(R.id.searchInput)
        val cancelButton = findViewById<Button>(R.id.cancelButton)
        val searchResultsRecyclerView = findViewById<RecyclerView>(R.id.searchResultsRecyclerView)

        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)

        // 🔹 Cancel Button Returns to Main Page
        cancelButton.setOnClickListener {
            finish() // ✅ Closes the search page and returns to MainActivity
        }
    }
}
