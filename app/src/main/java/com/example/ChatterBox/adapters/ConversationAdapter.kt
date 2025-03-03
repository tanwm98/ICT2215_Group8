package com.example.ChatterBox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.R
import com.example.ChatterBox.models.Conversation

class ConversationAdapter(private val conversations: MutableList<Conversation>, private val onClick: (Conversation) -> Unit) :
    RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.lastMessageTextView.text = conversation.lastMessage
        holder.itemView.setOnClickListener { onClick(conversation) }
    }

    override fun getItemCount(): Int = conversations.size

    class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val lastMessageTextView: TextView = itemView.findViewById(R.id.lastMessageTextView)
    }
}
