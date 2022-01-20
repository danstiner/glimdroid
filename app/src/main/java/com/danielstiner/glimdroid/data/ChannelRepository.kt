package com.danielstiner.glimdroid.data

import com.danielstiner.glimdroid.apollo.LiveChannelsQuery
import com.danielstiner.glimdroid.apollo.type.ChannelStatus
import com.danielstiner.glimdroid.data.model.*
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10

class ChannelRepository(
    val glimesh: GlimeshSocketDataSource
) {
    private val channelRandom = ConcurrentHashMap<ChannelId, Double>()

    suspend fun get(channel: ChannelId): Channel = glimesh.channelQuery(channel)
        .channel!!
        .let { node ->
            Channel(
                id = ChannelId(node.id!!.toLong()),
                title = node.title!!,
                matureContent = node.matureContent ?: false,
                language = node.language,
                category = Category(node.category?.name!!),
                subcategory = node.subcategory?.name?.let { Subcategory(it) },
                tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
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
                        startedAt = stream.startedAt,
                    )
                }
            )
        }

    suspend fun watch(channel: ChannelId, countryCode: String): EdgeRoute {
        val data = glimesh.watchChannel(channel, countryCode)

        return EdgeRoute(data.id!!, URL(data.url!!))
    }

    suspend fun myFollowedAndHomepage(): Pair<List<Channel>, List<Channel>> {
        val data = glimesh.myHomepageQuery()

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
                    subcategory = node.subcategory?.name?.let { Subcategory(it) },
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = Stream(
                        id = StreamId(node.stream!!.id!!.toLong()),
                        viewerCount = node.stream.countViewers,
                        thumbnailUrl = node.stream.thumbnailUrl,
                        startedAt = node.stream.startedAt
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
                    subcategory = node.subcategory?.name?.let { Subcategory(it) },
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = Stream(
                        id = StreamId(node.stream!!.id!!.toLong()),
                        viewerCount = node.stream.countViewers,
                        thumbnailUrl = node.stream.thumbnailUrl,
                        startedAt = node.stream.startedAt,
                    )
                )
            }
            .sortedByDescending { channel -> channel.stream!!.startedAt }

        return Pair(followedLiveChannels, homepageLiveChannels)
    }


    suspend fun homepage() =
        glimesh.homepageQuery()
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
                    subcategory = node.subcategory?.name?.let { Subcategory(it) },
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                    streamer = Streamer(
                        username = node.streamer.username,
                        displayName = node.streamer.displayname,
                        avatarUrl = node.streamer.avatarUrl,
                    ),
                    stream = Stream(
                        id = StreamId(node.stream!!.id!!.toLong()),
                        viewerCount = node.stream.countViewers,
                        thumbnailUrl = node.stream.thumbnailUrl,
                        startedAt = node.stream.startedAt,
                    )
                )
            }

    suspend fun myFollowedLiveChannels() = glimesh.myFollowingLiveQuery()
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

    suspend fun live() =
        glimesh.liveChannelsQuery()
            .channels!!
            .edges!!
            .filter { edge -> edge!!.node!!.status == ChannelStatus.LIVE }
            .randomizedChannelOrder { edge -> edge!!.node!! }
            .map { edge ->
                val node = edge!!.node!!
                Channel(
                    id = ChannelId(node.id!!.toLong()),
                    title = node.title!!,
                    matureContent = node.matureContent ?: false,
                    language = node.language,
                    category = Category(node.category!!.name!!),
                    subcategory = node.subcategory?.name?.let { Subcategory(it) },
                    tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
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

    companion object {
        private const val TAG = "ChannelRepository"
    }

    /**
     * Randomize order of the channel list, but with two twists:
     * - Order is stable because this repository memoize the random factor chosen for each channel
     * - There is a small bias based on viewers, but is logarithmic to be more equitable than linear
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
    private inline fun <T> Iterable<T>.randomizedChannelOrder(crossinline selector: (T) -> LiveChannelsQuery.Node): List<T> {
        return sortedByDescending {
            val node = selector(it)
            val channel = ChannelId(node.id!!.toLong())
            val randomFactor = channelRandom.getOrPut(channel, { Math.random() })
            val viewers = node.stream?.countViewers ?: 0
            return@sortedByDescending randomFactor * magnitude(viewers)
        }
    }

    private fun magnitude(n: Int): Double = log10(n + 1.0) + 1.0
}