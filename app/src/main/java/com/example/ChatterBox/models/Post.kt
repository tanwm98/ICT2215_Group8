package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName

data class Post(
    @PropertyName("id") val id: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("content") val content: String = "",
    @PropertyName("authorId") val authorId: String = "",
    @PropertyName("authorEmail") val authorEmail: String = "",
    @PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis()
)