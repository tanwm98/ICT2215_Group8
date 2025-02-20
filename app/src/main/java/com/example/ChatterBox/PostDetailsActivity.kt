package com.example.ChatterBox

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ChatterBox.models.Post

class PostDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_details)

        // 🔹 Get the post object from Intent
        val post = intent.getParcelableExtra<Post>("post")

        // 🔹 Populate the UI with post data
        findViewById<TextView>(R.id.postTitle).text = post?.title
        findViewById<TextView>(R.id.postContent).text = post?.content
        findViewById<TextView>(R.id.postAuthor).text = "Posted by: ${post?.authorEmail}"
    }
}
