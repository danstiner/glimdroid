package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)
