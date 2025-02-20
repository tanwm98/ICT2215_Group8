package com.example.ChatterBox.models

import com.google.firebase.firestore.PropertyName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Post(
    @PropertyName("id") val id: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("content") val content: String = "",
    @PropertyName("authorId") val authorId: String = "",
    @PropertyName("authorEmail") val authorEmail: String = "",
    @PropertyName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @PropertyName("likes") val likes: Int = 0,
    @PropertyName("likedBy") val likedBy: List<String> = emptyList()
) : Parcelable
