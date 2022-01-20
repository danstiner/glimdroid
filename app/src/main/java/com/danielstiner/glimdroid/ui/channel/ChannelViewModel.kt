package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielstiner.glimdroid.data.ChannelRepository
import com.danielstiner.glimdroid.data.ChatRepository
import com.danielstiner.glimdroid.data.model.*
import com.danielstiner.phoenix.absinthe.Subscription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.VideoTrack
import java.net.URL


const val TAG = "ChannelViewModel"

class ChannelViewModel(
    private val peerConnectionFactory: WrappedPeerConnectionFactory,
    private val channels: ChannelRepository,
    private val chats: ChatRepository,
    private val countryCode: String
) : ViewModel() {
    private val _channel = MutableLiveData<Channel>()
    val channel: LiveData<Channel> = _channel

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    private val _matureContent = MutableLiveData<Boolean>()
    val matureContent: LiveData<Boolean> = _matureContent

    private val _language = MutableLiveData<String?>()
    val language: LiveData<String?> = _language

    private val _category = MutableLiveData<Category>()
    val category: LiveData<Category> = _category

    private val _subcategory = MutableLiveData<Subcategory?>()
    val subcategory: LiveData<Subcategory?> = _subcategory

    private val _tags = MutableLiveData<List<Tag>>()
    val tags: LiveData<List<Tag>> = _tags

    private val _streamerDisplayname = MutableLiveData<String>()
    val streamerDisplayname: LiveData<String> = _streamerDisplayname

    private val _streamerUsername = MutableLiveData<String>()
    val streamerUsername: LiveData<String> = _streamerUsername

    private val _streamerAvatarUrl = MutableLiveData<URL?>()
    val streamerAvatarUrl: LiveData<URL?> = _streamerAvatarUrl

    private val _messagesMutable: MutableList<ChatMessage> = mutableListOf()
    private val _messages = MutableLiveData<List<ChatMessage>>().apply {
        value = _messagesMutable
    }
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _viewerCount = MutableLiveData<Int?>()
    val viewerCount: LiveData<Int?> = _viewerCount

    private val _videoThumbnailUrl = MutableLiveData<URL?>()
    val videoThumbnailUrl: LiveData<URL?> = _videoThumbnailUrl

    private val _videoTrack = MutableLiveData<VideoTrack?>()
    val videoTrack: LiveData<VideoTrack?> = _videoTrack

    @Volatile
    private var connection: JanusRtcConnection? = null

    @Volatile
    internal var currentChannel: ChannelId? = null

    @Volatile
    private var cleared = false

    private var chatSubscription: Subscription<ChatMessage>? = null
    private var channelSubscription: Subscription<Channel>? = null

    val isWatching: Boolean get() = connection != null && (connection?.isClosed != true)

    val mutex = Mutex()

    @MainThread
    fun watch(channel: ChannelId) {
        Log.d(TAG, "Watch $channel, current:$currentChannel")

        if (currentChannel == channel) {
            return
        }

        stopWatching()
        startWatching(channel)
    }

    @MainThread
    fun sendMessage(text: CharSequence?) {
        val channel = currentChannel!!
        viewModelScope.launch(Dispatchers.IO) {
            chats.sendMessage(channel, text!!)
        }
    }

    @MainThread
    fun stopWatching() {
        Log.d(TAG, "Stop watching, current:$currentChannel")
        currentChannel = null

        // Close previous connection, if any
        connection?.close()

        // Capture subscriptions in thread-safe way for async cancellation
        val chatSub = chatSubscription
        val channelSub = channelSubscription

        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                chatSub?.cancel()
                channelSub?.cancel()
            }
        }
    }

    @MainThread
    private fun startWatching(channel: ChannelId) {
        currentChannel = channel

        viewModelScope.launch(Dispatchers.IO) {
            // Kick off loading critical in parallel to get user visible content on screen fast
            joinAll(
                launch { connectRtc(channel) },
                launch {
                    fetchChannelInfo(channel)
                    // Kept separate from fetchChannelInfo because fetching chat messages is slow,
                    // likely due to inefficient joins on the server
                    fetchRecentChatHistory(channel)
                },
            )

            // After initial fetch is done, start subscriptions and background work
            joinAll(
                launch { watchChats(channel) },
                launch { watchChannelUpdates(channel) },
            )
        }
    }

    private suspend fun connectRtc(channel: ChannelId) {
        // TODO, need to use the websocket connection here, keeping it open keeps presence
        val route = channels.watch(channel, countryCode)

        val con = JanusRtcConnection.create(route.url, channel, peerConnectionFactory) { stream ->
            viewModelScope.launch(Dispatchers.Main) {
                assert(stream.videoTracks.size == 1)
                mutex.withLock {
                    if (currentChannel == channel) {
                        _videoTrack.value = stream.videoTracks[0]
                    } else {
                        Log.w(
                            TAG,
                            "Channel changed out from under us before RTC video track arrived"
                        )
                    }
                }

            }
        }

        mutex.withLock {
            if (currentChannel == channel) {
                val oldConnection = connection
                connection = con
                oldConnection?.close()
            } else {
                Log.w(TAG, "Channel changed out from under us before RTC connection opened")
                con.close()
            }
        }
    }

    private suspend fun fetchChannelInfo(channel: ChannelId) {
        updateChannelLiveData(channels.get(channel))
    }

    private suspend fun updateChannelLiveData(channel: Channel) {
        withContext(Dispatchers.Main) {
            mutex.withLock {
                if (currentChannel == channel.id) {
                    _channel.value = channel
                    _title.value = channel.title
                    _matureContent.value = channel.matureContent
                    _language.value = channel.language
                    _category.value = channel.category
                    _subcategory.value = channel.subcategory
                    _tags.value = channel.tags
                    _streamerDisplayname.value = channel.streamer.displayName
                    _streamerUsername.value = channel.streamer.username
                    _streamerAvatarUrl.value = channel.streamer.avatarUrl?.let { URL(it) }
                    _viewerCount.value = channel.stream?.viewerCount
                    _videoThumbnailUrl.value = channel.stream?.thumbnailUrl?.let { URL(it) }
                }
            }
        }
    }

    private suspend fun fetchRecentChatHistory(channel: ChannelId) {
        withContext(Dispatchers.Main) {
            mutex.withLock {
                if (currentChannel == channel) {
                    _messages.value = listOf()
                } else {
                    Log.w(TAG, "Channel changed out from under us before chats cleared")
                }
            }
        }
        val recentMessages = chats.recentMessages(channel)

        withContext(Dispatchers.Main) {
            mutex.withLock {
                if (currentChannel == channel) {
                    _messages.value = recentMessages
                } else {
                    Log.w(TAG, "Channel changed out from under us before recent chats fetched")
                }
            }
        }
    }

    private suspend fun watchChats(channel: ChannelId) {
        val sub = chats.subscribe(channel).apply {
            this.data.collect { message ->
                withContext(Dispatchers.Main) {
                    mutex.withLock {
                        if (currentChannel == channel) {
                            _messages.value = buildList {
                                addAll(_messages.value!!)
                                add(message)
                            }
                        } else {
                            Log.w(TAG, "Channel changed out from under us by time chat arrived")
                        }
                    }
                }
            }
        }

        mutex.withLock {
            if (currentChannel == channel) {
                val oldSubscription = chatSubscription
                chatSubscription = sub
                oldSubscription?.cancel()
            } else {
                Log.w(TAG, "Channel changed out from under us before chat watch started")
                sub.cancel()
            }
        }
    }

    private suspend fun watchChannelUpdates(channel: ChannelId) {
        // Simplistic approach because websocket subscriptions for a channel do not receive updates
        // for stream metadata updates like number of viewers
        while (currentChannel == channel && !cleared) {
            delay(30_000)
            fetchChannelInfo(channel)
        }

//        val sub = glimeshSocket.channelUpdates(channel).apply {
//            data.collect { channel ->
//                Log.d(TAG, "Channel update: $channel")
//                updateChannelLiveData(channel)
//            }
//        }
//
//        mutex.withLock {
//            if (currentChannel == channel) {
//                channelSubscription = sub
//            } else {
//                Log.w(TAG, "Channel changed out from under us before chat watch started")
//                sub.cancel()
//            }
//        }
    }

    override fun onCleared() {
        super.onCleared()
        cleared = true
        stopWatching()
    }
}