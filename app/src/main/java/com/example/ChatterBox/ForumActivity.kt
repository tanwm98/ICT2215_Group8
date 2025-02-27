package com.example.ChatterBox

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.StudentAdapter
import com.example.ChatterBox.models.Forum
import com.example.ChatterBox.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ForumActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var moduleNameInput: EditText
    private lateinit var moduleCodeInput: EditText
    private lateinit var moduleDescriptionInput: EditText
    private lateinit var createForumButton: Button
    private lateinit var studentsRecyclerView: RecyclerView
    private val studentList = mutableListOf<User>()
    private lateinit var studentAdapter: StudentAdapter

    private val selectedStudentIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        moduleNameInput = findViewById(R.id.moduleNameInput)
        moduleCodeInput = findViewById(R.id.moduleCodeInput)
        moduleDescriptionInput = findViewById(R.id.moduleDescriptionInput)
        createForumButton = findViewById(R.id.createForumButton)
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)

        studentsRecyclerView.layoutManager = LinearLayoutManager(this)
        studentAdapter = StudentAdapter(studentList, selectedStudentIds)
        studentsRecyclerView.adapter = studentAdapter

        fetchStudents()

        createForumButton.setOnClickListener {
            createForum()
        }
    }

    /** 🔹 Fetch all students (isAdmin = false) */
    private fun fetchStudents() {
        db.collection("users")
            .whereEqualTo("isAdmin", false) // 🔹 Only fetch students
            .get()
            .addOnSuccessListener { snapshot ->
                studentList.clear()
                for (doc in snapshot.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) studentList.add(user)
                }
                studentAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show()
            }
    }

    /** 🔹 Create forum and save to Firestore */
    private fun createForum() {
        val moduleName = moduleNameInput.text.toString().trim()
        val moduleCode = moduleCodeInput.text.toString().trim()
        val moduleDescription = moduleDescriptionInput.text.toString().trim()

        if (moduleName.isEmpty() || moduleCode.isEmpty() || moduleDescription.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        val forum = Forum(
            name = moduleName,
            code = moduleCode,
            description = moduleDescription,
            enrolledStudents = selectedStudentIds
        )

        db.collection("forums")
            .add(forum)
            .addOnSuccessListener {
                Toast.makeText(this, "Forum created successfully!", Toast.LENGTH_SHORT).show()
                finish() // ✅ Close activity after creation
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to create forum", Toast.LENGTH_SHORT).show()
            }
    }
}
