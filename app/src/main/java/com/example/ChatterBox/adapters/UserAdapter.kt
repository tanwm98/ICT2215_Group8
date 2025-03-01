package com.example.ChatterBox.adapters

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.MessageActivity
import com.example.ChatterBox.R
import com.example.ChatterBox.models.User

class UserAdapter(private val userList: List<User>, private val onClick: (User) -> Unit) :
    RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameText: TextView = view.findViewById(R.id.usernameText)
        val displayNameText: TextView = view.findViewById(R.id.displayNameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.user_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.usernameText.text = user.username
        holder.displayNameText.text = user.displayName

        // ✅ Handle user click to start chat
        holder.itemView.setOnClickListener {
            Log.d("UserAdapter", "Clicked on: ${user.displayName}") // ✅ Debugging Clicks
            onClick(user)
        }
    }

    override fun getItemCount() = userList.size
}
