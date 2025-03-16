package com.example.ChatterBox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ChatterBox.R
import com.example.ChatterBox.models.Message

class MessageAdapter(private val messages: MutableList<Message>, private val currentUserId: String) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // If the current user sent the message, display it on the right
        if (message.senderId == currentUserId) {
            holder.userMessage.text = message.text
            holder.userMessage.visibility = View.VISIBLE
            holder.otherUserMessage.visibility = View.GONE
        } else {
            holder.otherUserMessage.text = message.text
            holder.otherUserMessage.visibility = View.VISIBLE
            holder.userMessage.visibility = View.GONE
        }

        if (!message.imageUrl.isNullOrEmpty()) {
            holder.messageImage.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(message.imageUrl)
                .into(holder.messageImage)
        } else {
            holder.messageImage.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessage: TextView = itemView.findViewById(R.id.userMessage)
        val otherUserMessage: TextView = itemView.findViewById(R.id.otherUserMessage)
        val messageImage: ImageView = itemView.findViewById(R.id.messageImage) // Add this line if you add an ImageView
    }
}
