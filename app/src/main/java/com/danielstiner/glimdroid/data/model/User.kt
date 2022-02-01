package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: UserId,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val following: List<Follow>,
)
