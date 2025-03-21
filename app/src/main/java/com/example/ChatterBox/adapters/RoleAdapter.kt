package com.example.ChatterBox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ChatterBox.R
import com.example.ChatterBox.models.User
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

class RoleAdapter(
    private val userList: MutableList<User>
) : RecyclerView.Adapter<RoleAdapter.RoleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.role_edit_item, parent, false)
        return RoleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoleViewHolder, position: Int) {
        val user = userList[position]

        holder.usernameText.text = user.username
        holder.displayNameText.text = user.displayName

        // Show current role
        val roleLabel = if (user.isAdmin) "Teacher" else "Student"
        holder.roleStatusText.text = roleLabel

        // Load profile picture
        Glide.with(holder.itemView.context)
            .load(user.profilePicUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .into(holder.profileImageView)

        // Update button text
        holder.roleButton.text = if (user.isAdmin) "Make Student" else "Make Teacher"

        holder.roleButton.setOnClickListener {
            val newIsAdmin = !user.isAdmin
            val db = FirebaseFirestore.getInstance()

            db.collection("users").document(user.uid)
                .update("isAdmin", newIsAdmin)
                .addOnSuccessListener {
                    val updatedUser = user.copy(isAdmin = newIsAdmin)
                    userList[position] = updatedUser
                    notifyItemChanged(position)

                    val msg = if (newIsAdmin) "Promoted to Teacher" else "Demoted to Student"
                    Toast.makeText(holder.itemView.context, "${user.displayName} $msg", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(holder.itemView.context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun getItemCount(): Int = userList.size

    class RoleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val usernameText: TextView = view.findViewById(R.id.usernameText)
        val displayNameText: TextView = view.findViewById(R.id.displayNameText)
        val profileImageView: CircleImageView = view.findViewById(R.id.profileImageView)
        val roleStatusText: TextView = view.findViewById(R.id.roleStatusText)
        val roleButton: Button = view.findViewById(R.id.roleButton)
    }
}
