package com.danielstiner.glimdroid.data.model

import java.time.Instant

data class ChatMessage(
    val id: String,
    val displayname: String,
    val username: String,
    val avatarUrl: String?,
    val timestamp: Instant,
    val tokens: List<Token>,
) {
    sealed class Token {
        data class Text(val text: String) : Token()
        data class Emote(val text: String, val src: String) : Token()
        data class Url(val text: String, val url: String) : Token()
    }
}