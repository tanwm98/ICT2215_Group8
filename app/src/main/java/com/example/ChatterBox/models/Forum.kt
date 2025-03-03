package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName

data class Forum(
    var id: String = "", // 🔹 Add this field to store Firestore document ID
    @PropertyName("name") val name: String = "",
    @PropertyName("code") val code: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("enrolledStudents") val enrolledStudents: List<String> = emptyList()
)
