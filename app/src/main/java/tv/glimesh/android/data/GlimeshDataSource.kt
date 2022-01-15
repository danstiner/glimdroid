package tv.glimesh.android.data

import com.apollographql.apollo3.ApolloClient
import tv.glimesh.android.data.model.ChannelId
import tv.glimesh.android.ui.channel.ChatMessage
import tv.glimesh.apollo.*
import tv.glimesh.apollo.type.ChatMessageInput
import java.net.URL
import java.time.Duration
import java.time.Instant

private const val TAG = "GlimeshDataSource"

class GlimeshDataSource(
    private val auth: AuthStateDataSource
) {
    private val apolloClient = ApolloClient.Builder()
        .serverUrl("https://glimesh.tv/api/graph")
        .build()

    suspend fun channelByIdQuery(id: ChannelId): ChannelByIdQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        return apolloClient.query(
            ChannelByIdQuery(
                com.apollographql.apollo3.api.Optional.Present(
                    id.id.toString()
                )
            )
        )
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun homepageQuery(): HomepageQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        return apolloClient.query(HomepageQuery())
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun myFollowingLiveQuery(): MyFollowingLiveQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        return apolloClient.query(MyFollowingLiveQuery())
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun liveChannelsQuery(): LiveChannelsQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        return apolloClient.query(LiveChannelsQuery())
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun watchChannel(channel: ChannelId, countryCode: String): EdgeRoute {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        val data = apolloClient.mutation(WatchChannelMutation(channel.id.toString(), countryCode))
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
            .watchChannel!!

        return EdgeRoute(data.id!!, URL(data.url!!))
    }

    suspend fun recentChatMessages(channel: ChannelId): List<ChatMessage> {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        val oneHourAgo = Instant.now().minus(Duration.ofHours(1))

        return apolloClient.query(RecentMessagesQuery(channel.id.toString()))
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
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
                    timestamp = Instant.parse(message.insertedAt as String + "Z"),
                )
            }.filter { it.timestamp.isAfter(oneHourAgo) }
    }

    suspend fun sendMessage(channel: ChannelId, text: CharSequence) {
        // TODO handle authorization exceptions
        var (accessToken, idToken) = auth.retrieveFreshTokens()

        val message = ChatMessageInput(
            com.apollographql.apollo3.api.Optional.Present(
                text.toString()
            )
        )
        apolloClient.mutation(SendMessageMutation(channel.id.toString(), message))
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
            .createChatMessage
    }
}

data class EdgeRoute(val id: String, val url: URL)
