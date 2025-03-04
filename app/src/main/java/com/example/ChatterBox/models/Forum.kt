package com.example.ChatterBox.models

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Forum(
    @get:Exclude var id: String = "",
    @PropertyName("name") val name: String = "",
    @PropertyName("code") val code: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("enrolledStudents") val enrolledStudents: List<String> = emptyList()
)
