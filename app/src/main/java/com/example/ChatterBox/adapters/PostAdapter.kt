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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.ChatterBox.PostDetailActivity


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

        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(currentUser)
        val savedPostsRef = userRef.collection("savedPosts").document(post.id)
        val postRef = db.collection("posts").document(post.id)

        // 🔹 Check if post is liked
        postRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val likedByList = document.get("likedBy") as? List<String> ?: emptyList()
                val isLikedByUser = likedByList.contains(currentUser)

                holder.likeButton.setImageResource(
                    if (isLikedByUser) R.drawable.liked_button else R.drawable.like_button
                )
            }
        }

        // 🔹 Handle Like Button Click
        holder.likeButton.setOnClickListener {
            toggleLike(post, holder.likeButton, holder.likeCount)
        }

        // 🔹 Check if the post is saved in Firestore and update UI
        savedPostsRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                holder.bookmarkButton.setImageResource(R.drawable.bookmarked_button)
            } else {
                holder.bookmarkButton.setImageResource(R.drawable.bookmark_button)
            }
        }

        // 🔹 Handle Bookmark Button Click
        holder.bookmarkButton.setOnClickListener {
            toggleSavePost(post, holder.bookmarkButton, isFromSavedPosts = false)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            holder.itemView.context.startActivity(intent)

        }
    }



    override fun getItemCount() = posts.size

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun toggleLike(post: Post, likeButton: ImageButton, likeCountView: TextView) {
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("posts").document(post.id)
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid

        db.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)

            val currentLikes = snapshot.getLong("likes") ?: 0
            val likedByList = snapshot.get("likedBy") as? MutableList<String> ?: mutableListOf()

            val isLiked = likedByList.contains(userId)

            val newLikes = if (isLiked) currentLikes - 1 else currentLikes + 1

            if (isLiked) {
                likedByList.remove(userId) // Remove user from liked list
            } else {
                likedByList.add(userId) // Add user to liked list
            }

            // 🔹 Update Firestore `posts/`
            transaction.update(postRef, "likes", newLikes)
            transaction.update(postRef, "likedBy", likedByList)

            newLikes // Return updated like count
        }.addOnSuccessListener { newLikes ->
            likeCountView.text = newLikes.toString()
            likeButton.setImageResource(
                if (newLikes > 0) R.drawable.liked_button else R.drawable.like_button
            )
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error updating like count", e)
        }
    }


    private fun toggleSavePost(
        post: Post,
        saveButton: ImageButton,
        savedPostsList: MutableList<Post>? = null,
        adapter: PostAdapter? = null,
        isFromSavedPosts: Boolean = false
    ) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)
        val savedPostsRef = userRef.collection("savedPosts").document(post.id)

        savedPostsRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // 🔹 Remove from saved posts
                savedPostsRef.delete().addOnSuccessListener {
                    saveButton.setImageResource(R.drawable.bookmark_button)
                    Toast.makeText(saveButton.context, "Removed from saved", Toast.LENGTH_SHORT).show()

                    // ✅ Remove from "Saved Posts" list only (NOT Main Page)
                    if (isFromSavedPosts && savedPostsList != null && adapter != null) {
                        savedPostsList.remove(post)
                        adapter.notifyDataSetChanged()
                    }

                    // 🔹 Notify MainActivity to refresh bookmark icons
                    val intent = Intent("REFRESH_MAIN")
                    saveButton.context.sendBroadcast(intent)

                }.addOnFailureListener {
                    Toast.makeText(saveButton.context, "Error removing post", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 🔹 Save the post
                val postData = hashMapOf(
                    "id" to post.id,
                    "title" to post.title,
                    "content" to post.content,
                    "authorId" to post.authorId,
                    "authorEmail" to post.authorEmail,
                    "timestamp" to post.timestamp,
                    "likes" to post.likes,
                    "likedBy" to post.likedBy
                )

                savedPostsRef.set(postData).addOnSuccessListener {
                    saveButton.setImageResource(R.drawable.bookmarked_button)
                    Toast.makeText(saveButton.context, "Post saved!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(saveButton.context, "Error saving post", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }








}

