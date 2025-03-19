package com.example.ChatterBox.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.R
import com.example.ChatterBox.models.User

class StudentAdapter(
    private val students: List<User>,
    private val selectedStudentIds: MutableList<String>
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

    class StudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.usernameText)
        val checkbox: CheckBox = view.findViewById(R.id.selectStudentCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.student_item, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        holder.nameText.text = student.username

        // Reduce unnecessary padding
        val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.setMargins(8, 2, 8, 2) // Reducing spacing between list items
        holder.itemView.layoutParams = layoutParams

        holder.checkbox.isChecked = selectedStudentIds.contains(student.uid)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedStudentIds.add(student.uid)
            } else {
                selectedStudentIds.remove(student.uid)
            }
        }
    }

    override fun getItemCount() = students.size
}
