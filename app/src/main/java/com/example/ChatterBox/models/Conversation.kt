package com.example.ChatterBox.models

data class Conversation(
    val conversationId: String = "",
    val participants: List<String> = listOf(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0
)
