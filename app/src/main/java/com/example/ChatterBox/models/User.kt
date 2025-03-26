package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String? = null,
    val contactDetails: String? = null,
    val expertiseInterests: String? = null,
    val availabilityStatus: String = "",
    val profileImage: String? = null,
    val profilePicUrl: String? = null,
    val isAdmin: Boolean = false,
    val enrolledForum: List<String> = emptyList()
)
