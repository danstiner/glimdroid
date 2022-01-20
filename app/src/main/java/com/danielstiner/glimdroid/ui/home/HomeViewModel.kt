package com.danielstiner.glimdroid.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.danielstiner.glimdroid.data.ChannelRepository
import com.danielstiner.glimdroid.data.model.Channel
import com.danielstiner.glimdroid.data.model.ChannelId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList

class HomeViewModel(
    private val channels: ChannelRepository,
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
        val (followed, homepage) = channels.myFollowedAndHomepage()

        followedLiveChannels = followed
        homepageLiveChannels = homepage

        updateListItems()
    }

    private suspend fun fetchAllLiveChannels() {
        allLiveChannels = channels.live()
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
