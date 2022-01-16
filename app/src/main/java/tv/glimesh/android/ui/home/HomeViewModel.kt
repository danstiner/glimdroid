package tv.glimesh.android.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList
import tv.glimesh.android.data.GlimeshDataSource
import tv.glimesh.android.data.GlimeshWebsocketDataSource
import tv.glimesh.android.data.model.Channel
import tv.glimesh.android.data.model.ChannelId

class HomeViewModel(
    private val glimesh: GlimeshDataSource,
    private val glimeshSocket: GlimeshWebsocketDataSource,
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
        val (homepage, followed) = glimesh.myHomepage()

        homepageLiveChannels = homepage
        followedLiveChannels = followed

        updateListItems()
    }

    private suspend fun fetchAllLiveChannels() {
        allLiveChannels = glimesh.liveChannels()
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
        var addedIds = mutableSetOf<ChannelId>()
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
