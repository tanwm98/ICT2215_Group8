package com.example.ChatterBox

import android.content.Intent
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ForumEditActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var forumId: String
    private lateinit var forumCode: String
    private lateinit var forumNameInput: EditText
    private lateinit var forumCodeInput: EditText
    private lateinit var forumDescriptionInput: EditText
    private lateinit var updateForumButton: Button
    private lateinit var addStudentButton: Button
    private lateinit var removeStudentButton: Button
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var studentAdapter: StudentAdapter

    private val enrolledStudentIds = mutableListOf<String>()
    private val allStudents = mutableListOf<User>()
    private val selectedStudents = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum_edit)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 🔥 Retrieve forumId and forumCode from intent
        forumId = intent.getStringExtra("FORUM_ID") ?: ""
        forumCode = intent.getStringExtra("FORUM_CODE") ?: ""

        Log.d("ForumEditActivity", "Received Forum ID: $forumId, Forum Code: $forumCode")
        if (forumId.isEmpty()) {
            Toast.makeText(this, "Invalid forum!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 🔹 Initialize UI components
        forumNameInput = findViewById(R.id.forumNameInput)
        forumCodeInput = findViewById(R.id.forumCodeInput)
        forumDescriptionInput = findViewById(R.id.forumDescriptionInput)
        updateForumButton = findViewById(R.id.updateForumButton)
        addStudentButton = findViewById(R.id.addStudentButton)
        removeStudentButton = findViewById(R.id.removeStudentButton)
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)

        studentsRecyclerView.layoutManager = LinearLayoutManager(this)
        studentAdapter = StudentAdapter(allStudents, selectedStudents)
        studentsRecyclerView.adapter = studentAdapter

        fetchForumDetails()
        fetchStudents()

        updateForumButton.setOnClickListener {
            updateForum()
        }

        addStudentButton.setOnClickListener {
            addStudentsToForum()
        }

        removeStudentButton.setOnClickListener {
            removeSelectedStudentsFromForum()
        }
    }

    /** 🔹 Fetch forum details and populate UI */
    private fun fetchForumDetails() {
        db.collection("forums").document(forumId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val forum = document.toObject(Forum::class.java)
                    forumNameInput.setText(forum?.name)
                    forumCodeInput.setText(forum?.code)
                    forumDescriptionInput.setText(forum?.description)
                    enrolledStudentIds.clear()
                    enrolledStudentIds.addAll(forum?.enrolledStudents ?: emptyList())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching forum: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** 🔹 Fetch all students and separate enrolled vs non-enrolled */
    private fun fetchStudents() {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                allStudents.clear()
                for (doc in snapshot.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        allStudents.add(user)
                    }
                }
                studentAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load students", Toast.LENGTH_SHORT).show()
            }
    }

    /** 🔹 Update forum details in Firestore */
    private fun updateForum() {
        val updatedName = forumNameInput.text.toString().trim()
        val updatedCode = forumCodeInput.text.toString().trim()
        val updatedDescription = forumDescriptionInput.text.toString().trim()

        if (updatedName.isEmpty() || updatedCode.isEmpty() || updatedDescription.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("forums").document(forumId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentEnrolledStudents = document.get("enrolledStudents") as? List<String> ?: emptyList()

                    val updatedData = mapOf(
                        "name" to updatedName,
                        "code" to updatedCode,
                        "description" to updatedDescription,
                        "enrolledStudents" to currentEnrolledStudents // ✅ Keep students enrolled
                    )

                    db.collection("forums").document(forumId)
                        .update(updatedData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Forum updated successfully!", Toast.LENGTH_SHORT).show()
                            updateUsersEnrolledForums(forumId, updatedCode) // ✅ Update sidebar data
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update forum: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch current enrolled students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUsersEnrolledForums(forumId: String, newForumCode: String) {
        db.collection("forums").document(forumId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val enrolledStudents = document.get("enrolledStudents") as? List<String> ?: emptyList()

                    for (studentId in enrolledStudents) {
                        val userRef = db.collection("users").document(studentId)

                        userRef.get().addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val currentForums = userDoc.get("enrolledForum") as? List<String> ?: emptyList()

                                // 🔥 Replace old forum code with the new one
                                val updatedForums = currentForums.map { if (it == forumCode) newForumCode else it }

                                userRef.update("enrolledForum", updatedForums)
                                    .addOnSuccessListener {
                                        Log.d("Firestore", "Updated enrolledForum for $studentId")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("Firestore", "Failed to update enrolledForum for $studentId: ${e.message}")
                                    }
                            }
                        }.addOnFailureListener { e ->
                            Log.e("Firestore", "Failed to fetch user data for $studentId: ${e.message}")
                        }
                    }
                }
            }
    }



    /** 🔹 Add new students to the forum */
    private fun addStudentsToForum() {
        val newStudents = selectedStudents.filter { !enrolledStudentIds.contains(it) }

        if (newStudents.isEmpty()) {
            Toast.makeText(this, "All selected students are already enrolled!", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 Update Firestore: Add new students to `enrolledStudents`
        val updatedEnrolledStudents = enrolledStudentIds + newStudents

        db.collection("forums").document(forumId)
            .update("enrolledStudents", updatedEnrolledStudents)
            .addOnSuccessListener {
                Toast.makeText(this, "New students added successfully!", Toast.LENGTH_SHORT).show()

                // ✅ Update UI List
                enrolledStudentIds.addAll(newStudents)
                updateUsersWithNewForum(newStudents, forumCode) // 🔥 Update user data
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    /** 🔹 Update users' enrolledForum list after adding them */
    private fun updateUsersWithNewForum(newStudents: List<String>, forumCode: String) {
        for (studentId in newStudents) {
            val userRef = db.collection("users").document(studentId)

            userRef.get().addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val currentForums = userDoc.get("enrolledForum") as? List<String> ?: emptyList()

                    if (!currentForums.contains(forumCode)) {
                        val updatedForums = currentForums + forumCode

                        userRef.update("enrolledForum", updatedForums)
                            .addOnSuccessListener {
                                Log.d("Firestore", "Updated enrolledForum for $studentId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Failed to update enrolledForum for $studentId: ${e.message}")
                            }
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Failed to fetch user data for $studentId: ${e.message}")
            }
        }
    }



    /** 🔹 Remove selected students from the forum */
    private fun removeSelectedStudentsFromForum() {
        if (selectedStudents.isEmpty()) {
            Toast.makeText(this, "No students selected for removal!", Toast.LENGTH_SHORT).show()
            return
        }

        val remainingStudents = enrolledStudentIds.filter { it !in selectedStudents }

        // 🔥 Update Firestore: Remove selected students from `enrolledStudents`
        db.collection("forums").document(forumId)
            .update("enrolledStudents", remainingStudents)
            .addOnSuccessListener {
                Toast.makeText(this, "Students removed successfully!", Toast.LENGTH_SHORT).show()

                // ✅ Update UI List
                enrolledStudentIds.clear()
                enrolledStudentIds.addAll(remainingStudents)
                studentAdapter.notifyDataSetChanged() // 🔥 Refresh the student list UI

                // 🔥 Update user records to remove the forum
                removeForumFromUsers(selectedStudents, forumCode)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to remove students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** 🔹 Remove the forum from the user's enrolledForum list */
    private fun removeForumFromUsers(removedStudents: List<String>, forumCode: String) {
        for (studentId in removedStudents) {
            val userRef = db.collection("users").document(studentId)

            userRef.get().addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val currentForums = userDoc.get("enrolledForum") as? List<String> ?: emptyList()

                    if (currentForums.contains(forumCode)) {
                        val updatedForums = currentForums.filter { it != forumCode }

                        userRef.update("enrolledForum", updatedForums)
                            .addOnSuccessListener {
                                Log.d("Firestore", "Removed forum from user $studentId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Failed to update enrolledForum for $studentId: ${e.message}")
                            }
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("Firestore", "Failed to fetch user data for $studentId: ${e.message}")
            }
        }
    }


}
