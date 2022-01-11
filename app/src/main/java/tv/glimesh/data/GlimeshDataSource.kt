package tv.glimesh.data

import android.os.Build
import androidx.annotation.RequiresApi
import com.apollographql.apollo3.ApolloClient
import tv.glimesh.apollo.*
import tv.glimesh.data.model.ChannelId
import tv.glimesh.ui.channel.ChatMessage
import java.net.URL
import java.time.Duration
import java.time.Instant

private const val TAG = "GlimeshDataSource"

@RequiresApi(Build.VERSION_CODES.O)
class GlimeshDataSource(
    private val authState: AuthStateDataSource
) {
    private val apolloClient = ApolloClient.Builder()
        .serverUrl("https://glimesh.tv/api/graph")
        .build()

    suspend fun channelByIdQuery(id: ChannelId): ChannelByIdQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken, ex) = authState.retrieveFreshTokens()

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
        var (accessToken, idToken, ex) = authState.retrieveFreshTokens()

        return apolloClient.query(HomepageQuery())
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun myFollowingQuery(): MyFollowingQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken, ex) = authState.retrieveFreshTokens()

        return apolloClient.query(MyFollowingQuery())
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun liveChannelsQuery(): LiveChannelsQuery.Data {
        // TODO handle authorization exceptions
        var (accessToken, idToken, ex) = authState.retrieveFreshTokens()

        return apolloClient.query(LiveChannelsQuery())
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
    }

    suspend fun watchChannel(channel: ChannelId, countryCode: String): EdgeRoute {
        // TODO handle authorization exceptions
        var (accessToken, idToken, ex) = authState.retrieveFreshTokens()

        val data = apolloClient.mutation(WatchChannelMutation(channel.id.toString(), countryCode))
            .addHttpHeader("Authorization", "Bearer $accessToken")
            .execute()
            .dataAssertNoErrors
            .watchChannel!!

        return EdgeRoute(data.id!!, URL(data.url!!))
    }


    suspend fun chats(channel: ChannelId, countryCode: String): EdgeRoute {

        return TODO()
    }

    suspend fun recentChatMessages(channel: ChannelId): List<ChatMessage> {
        // TODO handle authorization exceptions
        var (accessToken, idToken, ex) = authState.retrieveFreshTokens()

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
}

data class EdgeRoute(val id: String, val url: URL)
