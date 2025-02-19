package com.example.ChatterBox.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.R
import com.example.ChatterBox.models.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostAdapter(private val posts: MutableList<Post>) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    class PostViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.postTitle)
        val contentView: TextView = view.findViewById(R.id.postContent)
        val authorView: TextView = view.findViewById(R.id.postAuthor)
        val timeView: TextView = view.findViewById(R.id.postTime)
        val likeButton: ImageButton = view.findViewById(R.id.likeButton)
        val likeCount: TextView = view.findViewById(R.id.likeCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.post_item, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        val currentUser = auth.currentUser?.uid ?: ""

        holder.titleView.text = post.title
        holder.contentView.text = post.content
        holder.authorView.text = "Posted by: ${post.authorEmail}"
        holder.timeView.text = formatTime(post.timestamp)
        holder.likeCount.text = post.likes.toString()

        // Check if the current user has already liked the post
        val isLikedByUser = post.likedBy.contains(currentUser)

        // Set correct icon based on whether the user liked the post
        holder.likeButton.setImageResource(
            if (isLikedByUser) R.drawable.liked_button else R.drawable.like_button
        )

        // Handle Like Button Click
        holder.likeButton.setOnClickListener {
            toggleLike(post, holder.likeButton, holder.likeCount)
        }
    }

    override fun getItemCount() = posts.size

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun toggleLike(post: Post, likeButton: ImageButton, likeCountView: TextView) {
        val postRef = db.collection("posts").document(post.id)
        val currentUser = auth.currentUser?.uid ?: return

        db.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            val currentLikes = snapshot.getLong("likes") ?: 0
            val likedByList = snapshot.get("likedBy") as? List<String> ?: emptyList()

            val isLiked = likedByList.contains(currentUser)

            val newLikes = if (isLiked) currentLikes - 1 else currentLikes + 1
            val updatedLikedBy = if (isLiked) {
                likedByList - currentUser // Remove user from list
            } else {
                likedByList + currentUser // Add user to list
            }

            transaction.update(postRef, "likes", newLikes)
            transaction.update(postRef, "likedBy", updatedLikedBy)

            return@runTransaction newLikes // ✅ Ensure a value is returned
        }.addOnSuccessListener { newLikes ->
            likeCountView.text = newLikes.toString()
            likeButton.setImageResource(
                if (newLikes > 0) R.drawable.liked_button else R.drawable.like_button
            )
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error updating like count", e)
        }
    }

}

