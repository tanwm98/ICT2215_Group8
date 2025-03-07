package com.example.ChatterBox.models

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val imageUrl: String? = null,  // to store the image for location
    val timestamp: Long = 0
)
