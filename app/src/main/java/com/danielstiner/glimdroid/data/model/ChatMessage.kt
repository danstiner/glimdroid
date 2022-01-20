package com.danielstiner.glimdroid.data.model

import java.time.Instant

data class ChatMessage(
    val id: String,
    val message: String,
    val displayname: String,
    val username: String,
    val avatarUrl: String?,
    val timestamp: Instant,
)