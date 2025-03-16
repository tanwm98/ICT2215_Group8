package com.example.ChatterBox

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.models.Comment
import com.example.ChatterBox.models.Post
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView
import com.bumptech.glide.Glide
import com.example.ChatterBox.adapters.CommentAdapter


class PostDetailActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var commentsRecyclerView: RecyclerView
    private val comments = mutableListOf<Comment>()
    private lateinit var postId: String  // ID of the post being viewed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        postId = intent.getStringExtra("POST_ID") ?: ""
        if (postId.isEmpty()) {
            Toast.makeText(this, "Error loading post", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🔹 Initialize RecyclerView and Adapter
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView)
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)
        commentsRecyclerView.adapter = CommentAdapter(comments) // ✅ Ensure adapter is set

        // 🔹 Load Data
        loadPostDetails()
        loadComments()

        // 🔹 Handle Add Comment Button Click
        findViewById<FloatingActionButton>(R.id.addCommentButton).setOnClickListener {
            showAddCommentDialog()
        }
    }


    /** 🔹 Show Dialog to Add Comment */
    private fun showAddCommentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val commentInput = dialogView.findViewById<EditText>(R.id.commentInput)

        AlertDialog.Builder(this)
            .setTitle("Add Comment")
            .setView(dialogView)
            .setPositiveButton("Post") { _, _ ->
                val commentText = commentInput.text.toString().trim()
                if (commentText.isNotEmpty()) {
                    postComment(commentText)
                } else {
                    Toast.makeText(this, "Comment cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    /** 🔹 Save the Comment in Firebase */
    private fun postComment(commentText: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login to comment", Toast.LENGTH_SHORT).show()
            return
        }

        val comment = Comment(
            authorId = currentUser.uid,
            authorEmail = currentUser.email ?: "Anonymous",
            content = commentText,
            timestamp = System.currentTimeMillis()
        )

        db.collection("posts").document(postId)
            .collection("comments")
            .add(comment)
            .addOnSuccessListener {
                Toast.makeText(this, "Comment added!", Toast.LENGTH_SHORT).show()
                loadComments() // ✅ Refresh comments instantly
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error posting comment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    /** 🔹 Load Post Details */
    private fun loadPostDetails() {
        db.collection("posts").document(postId)
            .get()
            .addOnSuccessListener { document ->
                val post = document.toObject(Post::class.java)
                if (post != null) {
                    findViewById<TextView>(R.id.postTitle).text = post.title
                    findViewById<TextView>(R.id.postContent).text = post.content
                    findViewById<TextView>(R.id.postAuthor).text = "Posted by: ${post.authorEmail}"

                    val postImageView = findViewById<ImageView>(R.id.postImage)

                    // 🔥 Check if image URL exists and load image using Glide
                    if (!post.imageUrl.isNullOrEmpty()) {
                        postImageView.visibility = View.VISIBLE
                        Glide.with(this)
                            .load(post.imageUrl)
                            .placeholder(R.drawable.ic_placeholder) // Show placeholder while loading
                            .into(postImageView)
                    } else {
                        postImageView.visibility = View.GONE // Hide if no image
                    }
                }
            }
    }



    /** 🔹 Load Comments from Firebase */
    private fun loadComments() {
        db.collection("posts").document(postId)
            .collection("comments")
            .orderBy("timestamp") // ✅ Ensures newest comments appear last
            .get()
            .addOnSuccessListener { snapshot ->
                comments.clear()
                for (doc in snapshot.documents) {
                    val comment = doc.toObject(Comment::class.java)
                    if (comment != null) {
                        comments.add(comment)
                    }
                }
                commentsRecyclerView.adapter?.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading comments: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
