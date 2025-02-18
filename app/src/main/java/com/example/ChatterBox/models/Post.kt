package com.example.ChatterBox.models

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)