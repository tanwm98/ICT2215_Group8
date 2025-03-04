package com.example.ChatterBox

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ChatterBox.adapters.StudentAdapter
import com.example.ChatterBox.models.Forum
import com.example.ChatterBox.models.User
import com.google.android.material.navigation.NavigationView
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
    /** 🔹 Create forum and save to Firestore */
    private fun createForum() {
        val currentUser = auth.currentUser ?: return
        val userRef = db.collection("users").document(currentUser.uid)

        userRef.get().addOnSuccessListener { document ->
            val isAdmin = document.getBoolean("isAdmin") ?: false
            if (!isAdmin) {
                Toast.makeText(this, "You do not have permission to create forums", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val moduleName = moduleNameInput.text.toString().trim()
            val moduleCode = moduleCodeInput.text.toString().trim()
            val moduleDescription = moduleDescriptionInput.text.toString().trim()

            if (moduleName.isEmpty() || moduleCode.isEmpty() || moduleDescription.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()

            }

            // 🔥 Add the admin to the list of enrolled users
            if (!selectedStudentIds.contains(currentUser.uid)) {
                selectedStudentIds.add(currentUser.uid) // ✅ Admin is now enrolled
            }

            val forum = Forum(
                name = moduleName,
                code = moduleCode,
                description = moduleDescription,
                enrolledStudents = selectedStudentIds
            )

            db.collection("forums")
                .add(forum)
                .addOnSuccessListener { forumDocRef ->
                    Toast.makeText(this, "Forum created successfully!", Toast.LENGTH_SHORT).show()

                    // 🔹 Update enrolledForum field for admin and students
                    updateStudentsEnrolledForum(moduleCode)

                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error creating forum: ${e.message}")
                    Toast.makeText(this, "Failed to create forum: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Failed to check admin status: ${e.message}")
            Toast.makeText(this, "Error checking admin status", Toast.LENGTH_SHORT).show()
        }
    }


    /** 🔹 Update the enrolledForum list for selected students using module code */
    /** 🔹 Update the enrolledForum list for selected students (and admin) */
    private fun updateStudentsEnrolledForum(moduleCode: String) {
        if (selectedStudentIds.isEmpty()) {
            Log.e("Firestore", "No students selected for enrollment")
            return
        }

        for (studentId in selectedStudentIds) {
            val studentRef = db.collection("users").document(studentId)

            studentRef.get().addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.e("Firestore", "User document does not exist for $studentId")
                    return@addOnSuccessListener
                }

                val currentEnrolledForums = document.get("enrolledForum") as? List<String> ?: listOf()

                if (!currentEnrolledForums.contains(moduleCode)) {
                    val updatedForums = currentEnrolledForums + moduleCode

                    studentRef.update("enrolledForum", updatedForums)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Successfully updated enrolledForum for $studentId: $updatedForums")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Failed to update enrolledForum for $studentId: ${e.message}")
                        }
                } else {
                    Log.d("Firestore", "User $studentId is already enrolled in $moduleCode")
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Failed to fetch student data for $studentId: ${e.message}")
            }
        }
    }



}
