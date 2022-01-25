package com.danielstiner.glimdroid.data

import com.danielstiner.glimdroid.apollo.fragment.MessageParts
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
                edge!!.node!!.messageParts
            }.map { message ->
                ChatMessage(
                    id = message.id,
                    displayname = message.user.displayname,
                    username = message.user.username,
                    avatarUrl = message.user.avatarUrl,
                    timestamp = message.insertedAt,
                    tokens = message.tokens!!.map(this::tokenize),
                )
            }.filter { it.timestamp.isAfter(oneHourAgo) }
    }

    suspend fun subscribe(channel: ChannelId): Subscription<ChatMessage> {
        return glimesh.messagesSubscription(channel).map { data ->
            val message = data.chatMessage!!.messageParts
            ChatMessage(
                id = message.id,
                displayname = message.user.displayname,
                username = message.user.username,
                avatarUrl = message.user.avatarUrl,
                timestamp = message.insertedAt,
                tokens = message.tokens!!.map(this::tokenize),
            )
        }
    }

    suspend fun sendMessage(channel: ChannelId, text: CharSequence) {
        glimesh.sendMessageMutation(channel, text)
    }

    private fun tokenize(token: MessageParts.Token?): ChatMessage.Token =
        when (token!!.__typename) {
            "TextToken" -> ChatMessage.Token.Text(token.onTextToken!!.text!!)
            "EmoteToken" -> ChatMessage.Token.Emote(
                token.onEmoteToken!!.text!!,
                token.onEmoteToken.src!!
            )
            "UrlToken" -> ChatMessage.Token.Url(token.onUrlToken!!.text!!, token.onUrlToken.url!!)
            else -> TODO("Unsupported chat message token, __typename:${token.__typename}")
        }

    companion object {
        private const val TAG = "ChatRepository"
        private val ONE_HOUR = Duration.ofHours(1)
    }
}