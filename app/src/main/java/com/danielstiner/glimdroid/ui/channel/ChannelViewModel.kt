package com.danielstiner.glimdroid.ui.channel

import android.net.Uri
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
import java.util.concurrent.atomic.AtomicReference


const val TAG = "ChannelViewModel"

class ChannelViewModel(
    private val channels: ChannelRepository,
    private val chats: ChatRepository,
    private val countryCode: String,
) : ViewModel() {
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

    private val _streamerAvatarUri = MutableLiveData<Uri?>()
    val streamerAvatarUri: LiveData<Uri?> = _streamerAvatarUri

    private val _messagesMutable: MutableList<ChatMessage> = mutableListOf()
    private val _messages = MutableLiveData<List<ChatMessage>>().apply {
        value = _messagesMutable
    }
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _viewerCount = MutableLiveData<Int?>()
    val viewerCount: LiveData<Int?> = _viewerCount

    private val _videoTrack = MutableLiveData<VideoTrack?>()
    val videoTrack: LiveData<VideoTrack?> = _videoTrack

    private val _watchSession = MutableLiveData<WatchSession>()
    val watchSession: LiveData<WatchSession> = _watchSession

    private var current = AtomicReference<ChannelSubscriptions?>(null)

    val isWatching: Boolean get() = current.get() != null

    val currentChannel: ChannelId? get() = current.get()?.channel

    @MainThread
    fun watch(channel: ChannelId) {
        val currentChannel = current.get()?.channel
        Log.d(TAG, "Watch $channel, current:${currentChannel}")

        if (currentChannel == channel) {
            return
        }

        current.getAndSet(ChannelSubscriptions(channel))?.clear()
    }

    @MainThread
    fun stopWatching() {
        val oldSub = current.getAndSet(null)
        val currentChannel = oldSub?.channel
        Log.d(TAG, "Stop watching, channel:$currentChannel")
        oldSub?.clear()
    }

    @MainThread
    fun sendMessage(text: CharSequence?) = current.get()!!.sendMessage(text!!)

    override fun onCleared() {
        super.onCleared()
        stopWatching()
    }

    /**
     * Container for subscriptions to channel and stream metadata.
     *
     * Having a container makes it easy to cleanly stop subscriptions when the channel is changed.
     */
    inner class ChannelSubscriptions(val channel: ChannelId) {

        private var chatSubscription: Subscription<ChatMessage>? = null

        @Volatile
        private var closed = false

        private val mutex = Mutex()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                // Kick off loading critical in parallel to get user visible content on screen fast
                joinAll(
                    launch { janus(channel) },
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

        fun sendMessage(text: CharSequence) {
            viewModelScope.launch(Dispatchers.IO) {
                chats.sendMessage(channel, text)
            }
        }

        private suspend fun janus(channel: ChannelId) {
            val edgeRoute = channels.watch(channel, countryCode)

            withContext(Dispatchers.Main) {
                if (!closed) {
                    _watchSession.value = WatchSession(channel, edgeRoute)
                }
            }
        }

        private suspend fun fetchChannelInfo(channel: ChannelId) {
            updateChannelLiveData(channels.get(channel))
        }

        private suspend fun updateChannelLiveData(channel: Channel) {
            withContext(Dispatchers.Main) {
                if (!closed) {
                    _title.value = channel.title
                    _matureContent.value = channel.matureContent
                    _language.value = channel.language
                    _category.value = channel.category
                    _subcategory.value = channel.subcategory
                    _tags.value = channel.tags
                    _streamerDisplayname.value = channel.streamer.displayName
                    _streamerUsername.value = channel.streamer.username
                    _streamerAvatarUri.value = channel.streamer.avatarUrl?.let { Uri.parse(it) }
                    _viewerCount.value = channel.stream?.viewerCount
                }
            }
        }

        private suspend fun fetchRecentChatHistory(channel: ChannelId) {
            withContext(Dispatchers.Main) {
                if (!closed) {
                    _messages.value = listOf()
                } else {
                    Log.w(TAG, "Channel changed before before chats could be cleared")
                }
            }
            val recentMessages = chats.recentMessages(channel)

            withContext(Dispatchers.Main) {
                if (!closed) {
                    _messages.value = recentMessages
                } else {
                    Log.w(TAG, "Channel changed before recent chats could be fetched")
                }
            }
        }

        private suspend fun watchChats(channel: ChannelId) {
            val sub = chats.subscribe(channel).apply {
                this.data.collect { message ->
                    withContext(Dispatchers.Main) {
                        if (!closed) {
                            _messages.value = buildList {
                                addAll(_messages.value!!)
                                add(message)
                            }
                        } else {
                            Log.w(TAG, "Channel changed before this chat arrived, ignoring")
                        }
                    }
                }
            }

            mutex.withLock {
                if (!closed) {
                    chatSubscription = sub
                } else {
                    Log.w(TAG, "Channel changed before chat subscription started, cancelling")
                    sub.cancel()
                }
            }
        }

        private suspend fun watchChannelUpdates(channel: ChannelId) {
            // Simplistic approach because websocket subscriptions for a channel do not receive
            // updates for stream metadata like number of viewers
            while (!closed) {
                delay(30_000)
                fetchChannelInfo(channel)
            }
        }

        fun clear() {
            viewModelScope.launch(Dispatchers.IO) {
                mutex.withLock {
                    chatSubscription?.cancel()
                    closed = true
                }
            }
        }
    }

    data class WatchSession(val channel: ChannelId, val edgeRoute: EdgeRoute)
}