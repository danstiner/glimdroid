package tv.glimesh.android.ui.channel

import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.VideoTrack
import tv.glimesh.android.data.ChatMessage
import tv.glimesh.android.data.GlimeshDataSource
import tv.glimesh.android.data.GlimeshWebsocketDataSource
import tv.glimesh.android.data.model.Category
import tv.glimesh.android.data.model.Channel
import tv.glimesh.android.data.model.ChannelId
import tv.glimesh.android.data.model.Tag
import tv.glimesh.phoenix.absinthe.Subscription
import java.net.URL


const val TAG = "ChannelViewModel"

class ChannelViewModel(
    private val peerConnectionFactory: WrappedPeerConnectionFactory,
    private val glimesh: GlimeshDataSource,
    private val glimeshSocket: GlimeshWebsocketDataSource,
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

    private val _videoTrack = MutableLiveData<VideoTrack>()
    val videoTrack: LiveData<VideoTrack> = _videoTrack

    var connection: JanusRtcConnection? = null
    var currentChannel: ChannelId? = null

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
            glimesh.sendMessage(channel, text!!)
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
            chatSub?.cancel()
            channelSub?.cancel()
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
        val route = glimesh.watchChannel(channel, countryCode)

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
        updateChannelLiveData(glimesh.channel(channel))
    }

    private suspend fun updateChannelLiveData(channel: Channel) {
        withContext(Dispatchers.Main) {
            mutex.let {
                if (currentChannel == channel.id) {
                    _channel.value = channel
                    _title.value = channel.title
                    _matureContent.value = channel.matureContent
                    _language.value = channel.language
                    _category.value = channel.category
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
        val recentMessages = glimesh.recentChatMessages(channel)

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
        val sub = glimeshSocket.chatMessages(channel).apply {
            data.collect { message ->
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
        while (currentChannel == channel) {
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
        stopWatching()

        super.onCleared()
    }
}