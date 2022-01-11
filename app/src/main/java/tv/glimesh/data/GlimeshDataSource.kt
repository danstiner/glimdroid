package tv.glimesh.data

import com.apollographql.apollo3.ApolloClient
import tv.glimesh.apollo.ChannelByIdQuery
import tv.glimesh.apollo.HomepageQuery
import tv.glimesh.apollo.LiveChannelsQuery
import tv.glimesh.apollo.MyFollowingQuery
import tv.glimesh.data.model.ChannelId

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
}