package com.example.ChatterBox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.R
import com.example.ChatterBox.models.Comment
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(private val comments: List<Comment>) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val authorView: TextView = view.findViewById(R.id.commentAuthor)
        val contentView: TextView = view.findViewById(R.id.commentContent)
        val timestampView: TextView = view.findViewById(R.id.commentTimestamp)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.comment_item, parent, false)
        return CommentViewHolder(view)
    }


    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.authorView.text = comment.authorEmail
        holder.contentView.text = comment.content
        holder.timestampView.text = formatTime(comment.timestamp)
    }

    override fun getItemCount() = comments.size

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
