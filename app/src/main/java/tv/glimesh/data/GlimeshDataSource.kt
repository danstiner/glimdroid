package tv.glimesh.data

import com.apollographql.apollo3.ApolloClient
import tv.glimesh.apollo.*
import tv.glimesh.data.model.ChannelId
import java.net.URL

private const val TAG = "GlimeshDataSource"

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
}

data class EdgeRoute(val id: String, val url: URL)
