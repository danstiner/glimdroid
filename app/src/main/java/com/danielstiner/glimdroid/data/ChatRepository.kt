package com.danielstiner.glimdroid.data

import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.glimdroid.data.model.ChatMessage
import com.danielstiner.phoenix.absinthe.Subscription
import java.time.Duration
import java.time.Instant

class ChatRepository(
    val glimesh: GlimeshSocketDataSource
) {
    suspend fun recentMessages(channel: ChannelId): List<ChatMessage> {
        val oneHourAgo = Instant.now().minus(ONE_HOUR)

        return glimesh.recentMessagesQuery(channel)
            .channel!!
            .chatMessages!!
            .edges!!
            .map { edge ->
                edge!!.node!!
            }.map { message ->
                ChatMessage(
                    id = message.id,
                    message = message.message ?: "",
                    displayname = message.user.displayname,
                    username = message.user.username,
                    avatarUrl = message.user.avatarUrl,
                    timestamp = message.insertedAt,
                )
            }.filter { it.timestamp.isAfter(oneHourAgo) }
    }

    suspend fun subscribe(channel: ChannelId): Subscription<ChatMessage> {
        return glimesh.messagesSubscription(channel).map { data ->
            val message = data.chatMessage!!
            ChatMessage(
                id = message.id,
                message = message.message!!,
                displayname = message.user.displayname,
                username = message.user.username,
                avatarUrl = message.user.avatarUrl,
                timestamp = message.insertedAt,
            )
        }
    }


    suspend fun sendMessage(channel: ChannelId, text: CharSequence) {
        glimesh.sendMessageMutation(channel, text)
    }

    companion object {
        private const val TAG = "ChatRepository"
        private val ONE_HOUR = Duration.ofHours(1)

    }
}