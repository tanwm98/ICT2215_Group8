package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName

data class User(
    @PropertyName("uid") val uid: String = "",
    @PropertyName("email") val email: String = "",
    @PropertyName("username") val username: String = "",
    @PropertyName("displayName") val displayName: String = "",
    @PropertyName("bio") val bio: String? = null,
    @PropertyName("contactDetails") val contactDetails: String? = null,
    @PropertyName("expertiseInterests") val expertiseInterests: String? = null,
    @PropertyName("availabilityStatus") val availabilityStatus: String = "",
    @PropertyName("profileImage") val profileImage: String? = null,
    @PropertyName("profilePicUrl") val profilePicUrl: String? = null,
    @PropertyName("isAdmin") val isAdmin: Boolean = false,
    @PropertyName("enrolledForum") val enrolledForum: List<String> = emptyList()
)
