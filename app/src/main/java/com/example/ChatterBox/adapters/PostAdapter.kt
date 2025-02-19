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
import android.widget.Toast

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
        val bookmarkButton: ImageButton = view.findViewById(R.id.bookmarkButton)
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

        // 🔹 Set correct like button icon
        val isLikedByUser = post.likedBy.contains(currentUser)
        holder.likeButton.setImageResource(
            if (isLikedByUser) R.drawable.liked_button else R.drawable.like_button
        )

        // 🔹 Handle Like Button Click
        holder.likeButton.setOnClickListener {
            toggleLike(post, holder.likeButton, holder.likeCount)
        }

        // 🔹 Firestore reference for saved posts
        val userRef = FirebaseFirestore.getInstance().collection("users").document(currentUser)
        val savedPostsRef = userRef.collection("savedPosts").document(post.id)

        // 🔹 Check if post is already saved in Firestore
        savedPostsRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                holder.bookmarkButton.setImageResource(R.drawable.bookmarked_button) // ✅ Set as bookmarked
            } else {
                holder.bookmarkButton.setImageResource(R.drawable.bookmark_button) // ✅ Set as unbookmarked
            }
        }

        // 🔹 Handle Bookmark Button Click
        holder.bookmarkButton.setOnClickListener {
            toggleSavePost(post, holder.bookmarkButton)
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

    private fun toggleSavePost(post: Post, saveButton: ImageButton) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)
        val savedPostsRef = userRef.collection("savedPosts").document(post.id)

        savedPostsRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // 🔹 Remove from saved posts
                savedPostsRef.delete().addOnSuccessListener {
                    saveButton.setImageResource(R.drawable.bookmark_button) // ✅ Change to unbookmarked
                    Toast.makeText(saveButton.context, "Removed from saved", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(saveButton.context, "Error removing post", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 🔹 Save the post with full data
                val postData = hashMapOf(
                    "id" to post.id,  // 🔥 Ensure post ID is stored
                    "title" to post.title,
                    "content" to post.content,
                    "authorId" to post.authorId,
                    "authorEmail" to post.authorEmail,
                    "timestamp" to post.timestamp,
                    "likes" to post.likes,
                    "likedBy" to post.likedBy
                )

                savedPostsRef.set(postData).addOnSuccessListener {
                    saveButton.setImageResource(R.drawable.bookmarked_button) // ✅ Change to bookmarked
                    Toast.makeText(saveButton.context, "Post saved!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(saveButton.context, "Error saving post", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }





}

