package tv.glimesh.android.data

import android.util.Log
import com.apollographql.apollo3.api.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tv.glimesh.android.data.model.ChannelId
import tv.glimesh.android.ui.channel.ChatMessage
import tv.glimesh.apollo.*
import tv.glimesh.apollo.type.ChatMessageInput
import tv.glimesh.phoenix.channels.Socket
import java.net.URI
import java.net.URL
import java.time.Instant

/**
 * Query and subscribe to data from glimesh.tv
 *
 * Opens a websocket connection to the Phoenix web frontend and sends regular heartbeats to stay
 * connected. If an authentication access token is available it will be used, otherwise it will
 * fall back to public access.
 * https://glimesh.github.io/api-docs/docs/api/live-updates/channels/
 * http://graemehill.ca/websocket-clients-and-phoenix-channels/
 */
class GlimeshWebsocketDataSource(
    private val auth: AuthStateDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: tv.glimesh.phoenix.absinthe.Connection? = null
    private var authenticatedConnection: tv.glimesh.phoenix.absinthe.Connection? = null

    suspend fun channelQuery(channel: ChannelId): ChannelByIdQuery.Data {
        return connection().query(
            ChannelByIdQuery(
                Optional.Present(
                    channel.id.toString()
                )
            )
        ).dataAssertNoErrors
    }

    suspend fun homepageQuery(): HomepageQuery.Data {
        return authenticatedConnection().query(
            HomepageQuery()
        ).dataAssertNoErrors
    }

    suspend fun myFollowingLiveQuery(): MyFollowingLiveQuery.Data {
        return authenticatedConnection().query(
            MyFollowingLiveQuery()
        ).dataAssertNoErrors
    }

    suspend fun liveChannelsQuery(): LiveChannelsQuery.Data {
        return connection().query(
            LiveChannelsQuery()
        ).dataAssertNoErrors
    }

    suspend fun watchChannel(channel: ChannelId, countryCode: String): EdgeRoute {
        val data = connection().mutation(
            WatchChannelMutation(channel.id.toString(), countryCode)
        ).dataAssertNoErrors.watchChannel!!

        return EdgeRoute(data.id!!, URL(data.url!!))
    }

    suspend fun sendMessage(channel: ChannelId, text: CharSequence) {
        authenticatedConnection().mutation(
            SendMessageMutation(
                channel.id.toString(), ChatMessageInput(
                    Optional.Present(
                        text.toString()
                    )
                )
            )
        ).dataAssertNoErrors.createChatMessage
    }

    suspend fun chatMessages(id: ChannelId): tv.glimesh.phoenix.absinthe.Subscription<ChatMessage> {
        return connection().subscription(
            MessagesSubscription(
                id.id.toString()
            )
        ).map { response ->
            Log.d("ChatMessage", response.data?.toString() ?: "null")
            val message = response.dataAssertNoErrors.chatMessage!!
            ChatMessage(
                id = message.id,
                message = message.message!!,
                displayname = message.user.displayname,
                username = message.user.username,
                avatarUrl = message.user.avatarUrl,
                timestamp = Instant.parse(message.insertedAt as String + "Z"),
            )
        }
    }

    private suspend fun connection(): tv.glimesh.phoenix.absinthe.Connection {
        if (auth.getCurrent().isAuthorized) {
            // TODO maybe close unauthenticated connection
            return authenticatedConnection()
        }

        if (connection == null) {
            val socket = Socket.open(
                URI.create("wss://glimesh.tv/api/socket/websocket?vsn=2.0.0&client_id=$CLIENT_ID"),
                scope = scope
            )
            connection = tv.glimesh.phoenix.absinthe.Connection.create(socket, scope)
        }

        return connection!!
    }

    private suspend fun authenticatedConnection(): tv.glimesh.phoenix.absinthe.Connection {
        if (authenticatedConnection == null) {
            val (accessToken, idToken) = auth.retrieveFreshTokens()
            val socket = Socket.open(
                URI.create("wss://glimesh.tv/api/socket/websocket?vsn=2.0.0&token=$accessToken"),
                scope = scope
            )
            authenticatedConnection = tv.glimesh.phoenix.absinthe.Connection.create(socket, scope)
        }
        return authenticatedConnection!!
    }
}
