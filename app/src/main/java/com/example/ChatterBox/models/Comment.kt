package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName

data class Comment(
    @PropertyName("authorId") val authorId: String = "",
    @PropertyName("authorEmail") val authorEmail: String = "",
    @PropertyName("content") val content: String = "",
    @PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
