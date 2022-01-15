package tv.glimesh.android.ui.home

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList
import tv.glimesh.android.data.GlimeshDataSource
import tv.glimesh.apollo.type.ChannelStatus
import kotlin.math.ln

@RequiresApi(Build.VERSION_CODES.O)
class HomeViewModel(
    private val glimesh: GlimeshDataSource,
) : ViewModel() {

    private val _items = MutableLiveData<List<SectionedChannelAdapter.Item>>().apply {
        value = listOf()
    }
    val items: LiveData<List<SectionedChannelAdapter.Item>> = _items

    private var followedLiveChannels = listOf<Channel>()
    private var homepageLiveChannels = listOf<Channel>()
    private var allLiveChannels = listOf<Channel>()

    suspend fun fetchAsync() {
        withContext(Dispatchers.IO) {
            fetchHomeLiveChannels()
            fetchAllLiveChannels()
        }
    }

    private suspend fun fetchHomeLiveChannels() {
        val result = glimesh.homepageQuery()

        followedLiveChannels = result
            ?.myself
            ?.followingLiveChannels
            ?.edges
            ?.mapNotNull { edge -> edge?.node }
            ?.sortedBy { node -> node?.stream?.startedAt.toString() }
            ?.reversed()
            ?.map { node ->
                Channel(
                    id = node.id!!,
                    title = node?.title!!,
                    streamerDisplayName = node?.streamer?.displayname,
                    streamerAvatarUrl = node?.streamer?.avatarUrl,
                    streamId = node?.stream?.id,
                    streamThumbnailUrl = node?.stream?.thumbnailUrl,
                    matureContent = node?.matureContent ?: false,
                    language = node?.language,
                    category = Category(node?.category?.name!!),
                    tags = node?.tags?.mapNotNull { tag -> tag?.name?.let { Tag(it) } }
                        ?: listOf(),
                )
            } ?: listOf()

        homepageLiveChannels = result
            ?.homepageChannels
            ?.edges
            ?.mapNotNull { edge -> edge?.node }
            ?.filter { node -> node?.status == ChannelStatus.LIVE }
            ?.map { node ->
                Channel(
                    id = node.id!!,
                    title = node?.title!!,
                    streamerDisplayName = node?.streamer?.displayname,
                    streamerAvatarUrl = node?.streamer?.avatarUrl,
                    streamId = node?.stream?.id,
                    streamThumbnailUrl = node?.stream?.thumbnailUrl,
                    matureContent = node?.matureContent ?: false,
                    language = node?.language,
                    category = Category(node?.category?.name!!),
                    tags = node?.tags?.mapNotNull { tag -> tag?.name?.let { Tag(it) } } ?: listOf(),
                )
            } ?: listOf()

        updateListItems()
    }

    private suspend fun fetchAllLiveChannels() {
        allLiveChannels = glimesh.liveChannelsQuery()
            ?.channels
            ?.edges
            ?.mapNotNull { edge -> edge?.node }
            ?.filter { node -> node?.status == ChannelStatus.LIVE }
            ?.sortedBy { node -> node?.stream?.countViewers?.let { (ln(it.toDouble() + 1) + 1) * Math.random() } }
            ?.reversed()
            ?.map { node ->
                Channel(
                    id = node.id!!,
                    title = node?.title!!,
                    streamerDisplayName = node?.streamer?.displayname,
                    streamerAvatarUrl = node?.streamer?.avatarUrl,
                    streamId = node?.stream?.id,
                    streamThumbnailUrl = node?.stream?.thumbnailUrl,
                    matureContent = node?.matureContent ?: false,
                    language = node?.language,
                    category = Category(node?.category?.name!!),
                    tags = node?.tags?.mapNotNull { tag -> tag?.name?.let { Tag(it) } } ?: listOf(),
                )
            } ?: listOf()

        updateListItems()
    }

    private suspend fun updateListItems() {
        withContext(Dispatchers.Main) {
            _items.value =
                combineChannelLists(followedLiveChannels, homepageLiveChannels, allLiveChannels)
        }
    }

    private fun combineChannelLists(
        followingChannels: List<Channel>,
        featuredChannels: List<Channel>,
        liveChannels: List<Channel>
    ): List<SectionedChannelAdapter.Item> {
        var addedIds = mutableSetOf<String>()
        var items = mutableListOf<SectionedChannelAdapter.Item>()

        if (followingChannels.isNotEmpty()) {
            items.add(SectionedChannelAdapter.Item.Header("Followed"))
            for (channel in followingChannels) {
                items.add(SectionedChannelAdapter.Item.Channel(channel))
                addedIds.add(channel.id)
            }
        }

        val uniqueFeaturedChannels = featuredChannels.filterNot { it.id in addedIds }
        if (uniqueFeaturedChannels.isNotEmpty()) {
            items.add(SectionedChannelAdapter.Item.Header("Featured"))
            var addedLargeChannel = false
            for (channel in uniqueFeaturedChannels) {
                if (addedLargeChannel) {
                    items.add(SectionedChannelAdapter.Item.Channel(channel))
                } else {
                    items.add(SectionedChannelAdapter.Item.LargeChannel(channel))
                    addedLargeChannel = true
                }
                addedIds.add(channel.id)
            }
        }

        items.add(SectionedChannelAdapter.Item.Tagline())

        var uniqueLiveChannels = liveChannels.filterNot { it.id in addedIds }
        if (uniqueLiveChannels.isNotEmpty()) {
            for (channel in uniqueLiveChannels) {
                items.add(SectionedChannelAdapter.Item.Channel(channel))
                addedIds.add(channel.id)
            }
        }

        return items.toImmutableList()
    }

}
