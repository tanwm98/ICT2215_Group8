package com.example.ChatterBox.adapters

import android.app.AlertDialog
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
import android.widget.ImageView
import com.bumptech.glide.Glide
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
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        val postImageView: ImageView = view.findViewById(R.id.postImageView)
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

        // 🔹 Load Image (If Available)
        if (!post.imageUrl.isNullOrEmpty()) {
            holder.postImageView.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(post.imageUrl)
                .placeholder(R.drawable.ic_placeholder)
                .into(holder.postImageView)
        } else {
            holder.postImageView.visibility = View.GONE
        }

        postRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val likedByList = document.get("likedBy") as? List<String> ?: emptyList()
                val isLikedByUser = likedByList.contains(currentUser)

                holder.likeButton.setImageResource(
                    if (isLikedByUser) R.drawable.liked_button else R.drawable.like_button
                )
            }
        }

        holder.likeButton.setOnClickListener {
            toggleLike(post, holder.likeButton, holder.likeCount)
        }

        savedPostsRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                holder.bookmarkButton.setImageResource(R.drawable.bookmarked_button)
            } else {
                holder.bookmarkButton.setImageResource(R.drawable.bookmark_button)
            }
        }

        holder.bookmarkButton.setOnClickListener {
            toggleSavePost(post, holder.bookmarkButton, isFromSavedPosts = false)
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            holder.itemView.context.startActivity(intent)
        }

        if (post.authorId == currentUser) {
            holder.deleteButton.visibility = View.VISIBLE
        } else {
            holder.deleteButton.visibility = View.GONE
        }

        holder.deleteButton.setOnClickListener {
            deletePost(post, position, holder)
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
                likedByList.remove(userId)
            } else {
                likedByList.add(userId)
            }

            transaction.update(postRef, "likes", newLikes)
            transaction.update(postRef, "likedBy", likedByList)

            newLikes
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
                savedPostsRef.delete().addOnSuccessListener {
                    saveButton.setImageResource(R.drawable.bookmark_button)
                    Toast.makeText(saveButton.context, "Removed from saved", Toast.LENGTH_SHORT).show()

                    if (isFromSavedPosts && savedPostsList != null && adapter != null) {
                        savedPostsList.remove(post)
                        adapter.notifyDataSetChanged()
                    }

                    val intent = Intent("REFRESH_MAIN")
                    saveButton.context.sendBroadcast(intent)

                }.addOnFailureListener {
                    Toast.makeText(saveButton.context, "Error removing post", Toast.LENGTH_SHORT).show()
                }
            } else {
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

    private fun deletePost(post: Post, position: Int, holder: PostViewHolder) {
        val currentUser = auth.currentUser ?: return

        if (post.authorId != currentUser.uid) {
            Toast.makeText(holder.itemView.context, "You can only delete your own posts", Toast.LENGTH_SHORT).show()
            return
        }

        val postRef = db.collection("posts").document(post.id)

        AlertDialog.Builder(holder.itemView.context)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                postRef.delete().addOnSuccessListener {
                    posts.removeAt(position)
                    notifyItemRemoved(position)
                    Toast.makeText(holder.itemView.context, "Post deleted", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    Toast.makeText(holder.itemView.context, "Error deleting post: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }







}

