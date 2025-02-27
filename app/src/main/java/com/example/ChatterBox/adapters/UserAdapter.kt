package com.example.ChatterBox.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.ProfileActivity
import com.example.ChatterBox.R
import com.example.ChatterBox.models.User

class UserAdapter(private val userList: List<User>) :
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

        // 🔹 Handle Click on User -> Open Profile
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, ProfileActivity::class.java)
            intent.putExtra("USER_ID", user.uid)  // Pass User ID to ProfileActivity
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = userList.size
}
