package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName

data class Forum(
    @PropertyName("name") val name: String = "",
    @PropertyName("code") val code: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("enrolledStudents") val enrolledStudents: List<String> = emptyList()
)
