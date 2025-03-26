package com.example.ChatterBox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.R
import com.example.ChatterBox.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RoleEditUserAdapter(
    private val userList: List<User>,
    private val isAdmin: Boolean
) : RecyclerView.Adapter<RoleEditUserAdapter.UserViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.role_edit_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val displayNameText: TextView = itemView.findViewById(R.id.displayNameText)
        private val roleStatusText: TextView = itemView.findViewById(R.id.roleStatusText)
        private val roleButton: Button = itemView.findViewById(R.id.roleButton)

        fun bind(user: User) {
            usernameText.text = user.username
            displayNameText.text = user.displayName
            roleStatusText.text = if (user.isAdmin) "Teacher" else "Student"

            if (!isAdmin || user.uid == auth.currentUser?.uid) {
                roleButton.visibility = View.GONE
            } else {
                roleButton.visibility = View.VISIBLE
                roleButton.text = if (user.isAdmin) "Make Student" else "Make Teacher"

                roleButton.setOnClickListener {
                    val newRole = !user.isAdmin
                    db.collection("users").document(user.uid)
                        .update("isAdmin", newRole)
                        .addOnSuccessListener {
                            roleStatusText.text = if (newRole) "Teacher" else "Student"
                            roleButton.text = if (newRole) "Make Student" else "Make Teacher"
                            Toast.makeText(itemView.context, "Role updated!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(itemView.context, "Failed to update role", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }
}
