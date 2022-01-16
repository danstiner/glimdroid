package tv.glimesh.android.data

import android.util.Log
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Adapter
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.CustomScalarType
import com.apollographql.apollo3.api.json.JsonReader
import com.apollographql.apollo3.api.json.JsonWriter
import tv.glimesh.android.data.model.*
import tv.glimesh.apollo.*
import tv.glimesh.apollo.type.ChannelStatus
import tv.glimesh.apollo.type.ChatMessageInput
import java.net.URL
import java.time.Duration
import java.time.Instant
import kotlin.math.log10

class GlimeshDataSource(
    private val auth: AuthStateDataSource
) {
    private val apolloClient = ApolloClient.Builder()
        .serverUrl("https://glimesh.tv/api/graph")
        .addCustomScalarAdapter(
            CustomScalarType("NaiveDateTime", Instant::javaClass.name),
            GlimeshNaiveTimeToInstantAdapter
        )
        .build()

    suspend fun channelByIdQuery(channel: ChannelId): ChannelByIdQuery.Data = apolloClient.query(
        ChannelByIdQuery(
            com.apollographql.apollo3.api.Optional.Present(channel.id.toString())
        )
    )
        .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
        .execute()
        .dataAssertNoErrors

    suspend fun myHomepage(): Pair<List<Channel>, List<Channel>> {
        val data = myHomepageQuery()

        val homepageLiveChannels = data
            .homepageChannels!!
            .edges!!
            .filter { edge -> edge!!.node!!.status == ChannelStatus.LIVE }
            .map { edge ->
                val node = edge!!.node!!
                Channel(
                    id = ChannelId(node.id!!.toLong()),
                    title = node.title!!,
                    matureContent = node.matureContent ?: false,
                    language = node.language,
                    category = Category(node.category?.name!!),
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = Stream(
                        id = StreamId(node.stream!!.id!!.toLong()),
                        viewerCount = node.stream!!.countViewers ?: 0,
                        thumbnailUrl = node.stream!!.thumbnailUrl,
                        startedAt = node.stream!!.startedAt
                    )
                )
            }

        val followedLiveChannels = data
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
                    category = Category(node.category?.name!!),
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = Stream(
                        id = StreamId(node.stream!!.id!!.toLong()),
                        viewerCount = node.stream!!.countViewers ?: 0,
                        thumbnailUrl = node.stream!!.thumbnailUrl,
                        startedAt = node.stream!!.startedAt,
                    )
                )
            }
            .sortedByDescending { channel -> channel.stream!!.startedAt }

        return Pair(homepageLiveChannels, followedLiveChannels)
    }

    private suspend fun myHomepageQuery(): MyHomepageQuery.Data =
        apolloClient.query(MyHomepageQuery())
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors

    suspend fun homepageChannels() =
        homepageQuery()
            .homepageChannels!!
            .edges!!
            .filter { edge -> edge!!.node!!.status == ChannelStatus.LIVE }
            .map { edge ->
                val node = edge!!.node!!
                Channel(
                    id = ChannelId(node.id!!.toLong()),
                    title = node.title!!,
                    matureContent = node.matureContent ?: false,
                    language = node.language,
                    category = Category(node.category?.name!!),
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = Stream(
                        id = StreamId(node.stream!!.id!!.toLong()),
                        viewerCount = node.stream!!.countViewers ?: 0,
                        thumbnailUrl = node.stream!!.thumbnailUrl,
                        startedAt = node.stream!!.startedAt,
                    )
                )
            }

    private suspend fun homepageQuery(): HomepageQuery.Data =
        apolloClient.query(HomepageQuery())
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors

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
                tags = node.tags!!.mapNotNull { tag -> tag?.name?.let { Tag(it) } },
                streamer = Streamer(
                    username = node.streamer.username,
                    displayName = node.streamer.displayname,
                    avatarUrl = node.streamer.avatarUrl,
                ),
                stream = node.stream?.let { stream ->
                    Stream(
                        id = StreamId(stream.id!!.toLong()),
                        viewerCount = stream.countViewers ?: 0,
                        thumbnailUrl = stream.thumbnailUrl,
                        startedAt = node.stream!!.startedAt,
                    )
                }
            )
        }

    suspend fun myFollowingLiveQuery(): MyFollowingLiveQuery.Data =
        apolloClient.query(MyFollowingLiveQuery())
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors

    suspend fun liveChannels() =
        liveChannelsQuery()
            .channels!!
            .edges!!
            .filter { edge -> edge!!.node!!.status == ChannelStatus.LIVE }
            .map { edge -> Log.d(TAG, edge.toString()); edge }
            .randomizedSortByDescending { edge -> edge!!.node!!.stream!!.countViewers ?: 0 }
            .map { edge ->
                val node = edge!!.node!!
                Channel(
                    id = ChannelId(node.id!!.toLong()),
                    title = node.title!!,
                    matureContent = node.matureContent ?: false,
                    language = node.language,
                    category = Category(node.category!!.name!!),
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = node.stream?.let { stream ->
                        Stream(
                            id = StreamId(stream.id!!.toLong()),
                            viewerCount = stream.countViewers ?: 0,
                            thumbnailUrl = stream.thumbnailUrl,
                            startedAt = node.stream!!.startedAt,
                        )
                    }
                )
            }

    private suspend fun liveChannelsQuery(): LiveChannelsQuery.Data =
        apolloClient.query(LiveChannelsQuery())
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors

    suspend fun watchChannel(channel: ChannelId, countryCode: String): EdgeRoute {
        val data = apolloClient.mutation(WatchChannelMutation(channel.id.toString(), countryCode))
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors
            .watchChannel!!

        return EdgeRoute(data.id!!, URL(data.url!!))
    }

    suspend fun recentChatMessages(channel: ChannelId): List<ChatMessage> {
        val oneHourAgo = Instant.now().minus(ONE_HOUR)

        return apolloClient.query(RecentMessagesQuery(channel.id.toString()))
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
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
                    timestamp = message.insertedAt,
                )
            }.filter { it.timestamp.isAfter(oneHourAgo) }
    }

    suspend fun sendMessage(channel: ChannelId, text: CharSequence) {
        val message = ChatMessageInput(
            com.apollographql.apollo3.api.Optional.Present(
                text.toString()
            )
        )
        apolloClient.mutation(SendMessageMutation(channel.id.toString(), message))
            .addHttpHeader("Authorization", "Bearer ${auth.freshAccessToken()}")
            .execute()
            .dataAssertNoErrors
            .createChatMessage
    }

    suspend fun channel(channel: ChannelId): Channel = channelByIdQuery(channel)
        .channel!!
        .let { node ->
            Channel(
                id = ChannelId(node.id!!.toLong()),
                title = node.title!!,
                matureContent = node.matureContent ?: false,
                language = node.language,
                category = Category(node.category?.name!!),
                tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                streamer = Streamer(
                    username = node.streamer.username,
                    displayName = node.streamer.displayname,
                    avatarUrl = node.streamer.avatarUrl,
                ),
                stream = node.stream?.let { stream ->
                    Stream(
                        id = StreamId(stream.id!!.toLong()),
                        viewerCount = stream.countViewers ?: 0,
                        thumbnailUrl = stream.thumbnailUrl,
                        startedAt = stream.startedAt,
                    )
                }
            )
        }

    companion object {
        private const val TAG = "GlimeshDataSource"
        private val ONE_HOUR = Duration.ofHours(1)

    }
}

/**
 * The Glimesh GraphQL endpoint says it's NaiveDateTime is ISO8601, but it's not actually, hence the
 * custom adapter
 *
 * The timezone identifier is missing, e.g. '2011-12-03T10:15:30'
 */
object GlimeshNaiveTimeToInstantAdapter : Adapter<Instant> {
    override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters): Instant {
        return Instant.parse(reader.nextString()!! + "Z")
    }

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        value: Instant
    ) {
        TODO()
    }
}

/**
 * Randomize the order of the list, but with a slight bias
 *
 * For example, for a list with thirty items where one is value 100 and the others are value 0:
 * - The 100 item has a 68% chance of being the first item, it's random value is in the range [0, 3)
 * - Each  0 item has a ~1% chance of being the first item, it's random value is in the range [0, 1)
 * - Each  0 item has a ~3% chance of being the second item (same for third position, fourth, etc)
 *
 * This gives a slight bias towards higher values while still giving lower values a decent chance
 * of being first. The base10 log ensures even for higher values like 10,000 the bias does not
 * change very much, there is still a distinct chance of a 0 item sorting first.
 */
private inline fun <T> Iterable<T>.randomizedSortByDescending(crossinline selector: (T) -> Int): List<T> {
    return sortedByDescending { Math.random() * magnitude(selector(it).toDouble()) }
}

private inline fun magnitude(n: Double): Double = log10(n + 1.0) + 1.0
