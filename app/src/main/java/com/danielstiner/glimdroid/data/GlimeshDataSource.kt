package com.danielstiner.glimdroid.data

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.CustomScalarType
import com.danielstiner.glimdroid.apollo.MyFollowingLiveQuery
import com.danielstiner.glimdroid.data.model.*
import java.time.Instant

val GLIMESH_GRAPH_API_ENDPOINT: String =
    GLIMESH_BASE_URI.buildUpon().appendEncodedPath("api/graph").build().toString()

class GlimeshDataSource(
    private val auth: AuthStateDataSource
) {
    private val apolloClient = ApolloClient.Builder()
        .serverUrl(GLIMESH_GRAPH_API_ENDPOINT)
        .addCustomScalarAdapter(
            CustomScalarType("NaiveDateTime", Instant::javaClass.name),
            GlimeshNaiveTimeToInstantAdapter
        )
        .build()

    suspend fun myFollowedLiveChannels() = myFollowingLiveQuery()
        .myself!!
        .followingLiveChannels!!
        .edges!!
        .map { edge ->
            val node = edge!!.node!!
            Channel(
                id = ChannelId(node.id!!.toLong()),
                title = node.title!!,
                matureContent = node.matureContent ?: false,
                language = node.language,
                category = Category(node.category!!.name!!),
                subcategory = node.subcategory?.name?.let { Subcategory(it) },
                tags = node.tags!!.mapNotNull { tag -> tag?.name?.let { Tag(it) } },
                streamer = Streamer(
                    username = node.streamer.username,
                    displayName = node.streamer.displayname,
                    avatarUrl = node.streamer.avatarUrl,
                ),
                stream = node.stream?.let { stream ->
                    Stream(
                        id = StreamId(stream.id!!.toLong()),
                        viewerCount = stream.countViewers,
                        thumbnailUrl = stream.thumbnailUrl,
                        startedAt = node.stream.startedAt,
                    )
                }
            )
        }

    suspend fun myFollowingLiveQuery(): MyFollowingLiveQuery.Data =
        apolloClient.query(MyFollowingLiveQuery())
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors

}
