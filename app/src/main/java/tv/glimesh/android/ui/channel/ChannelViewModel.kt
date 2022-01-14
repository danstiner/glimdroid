package tv.glimesh.android.ui.channel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.VideoTrack
import tv.glimesh.android.data.GlimeshDataSource
import tv.glimesh.android.data.GlimeshWebsocketDataSource
import tv.glimesh.android.data.model.ChannelId
import tv.glimesh.android.ui.home.Category
import tv.glimesh.android.ui.home.Channel
import tv.glimesh.android.ui.home.Tag
import java.net.URL
import java.time.Instant


const val TAG = "ChannelViewModel"

data class ChatMessage(
    val id: String,
    val message: String,
    val displayname: String,
    val username: String,
    val avatarUrl: String?,
    val timestamp: Instant
)

@RequiresApi(Build.VERSION_CODES.O)
class ChannelViewModel(
    private val peerConnectionFactory: WrappedPeerConnectionFactory,
    private val glimesh: GlimeshDataSource,
    private val glimeshSocket: GlimeshWebsocketDataSource,
    private val countryCode: String
) : ViewModel() {

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

    val isWatching: Boolean get() = connection != null && (connection?.isClosed != true)

    fun watch(channel: ChannelId) {
        if (currentChannel == channel) {
            return
        }
        currentChannel = channel

        viewModelScope.launch(Dispatchers.IO) { connectRtc(channel) }
        viewModelScope.launch(Dispatchers.IO) { fetchChannelInfo(channel) }
        viewModelScope.launch(Dispatchers.IO) { subscribeToChats(channel) }
    }

    fun sendMessage(text: CharSequence?) {
        viewModelScope.launch(Dispatchers.IO) {
            glimesh.sendMessage(currentChannel!!, text!!)
        }
    }

    fun stopWatching() {
        currentChannel = null
        connection?.close()
    }

    private suspend fun connectRtc(channel: ChannelId) {
        // Close previous connection, if any
        connection?.close()

        // TODO, need to use the websocket connection here, keeping it open keeps presence
        val route = glimesh.watchChannel(channel, countryCode)

        connection =
            JanusRtcConnection.create(route.url, channel, peerConnectionFactory) { stream ->
                viewModelScope.launch(Dispatchers.Main) {
                    assert(stream.videoTracks.size == 1)
                    _videoTrack.value = stream.videoTracks[0]
                }
            }
    }

    private suspend fun subscribeToChats(channel: ChannelId) {
        withContext(Dispatchers.Main) {
            _messages.value = listOf()
        }
        val recentMessages = glimesh.recentChatMessages(channel)

        withContext(Dispatchers.Main) {
            _messages.value = recentMessages
        }
        glimeshSocket.chatMessages(channel).collect { message ->
            Log.d(TAG, "New message: $message")
            withContext(Dispatchers.Main) {
                _messages.value = mutableListOf<ChatMessage>().apply {
                    addAll(_messages.value!!)
                    add(message)
                }
            }
        }
    }

    override fun onCleared() {
        stopWatching()

        super.onCleared()
    }

    private suspend fun fetchChannelInfo(channel: ChannelId) {
        val info = glimesh.channelByIdQuery(channel)
        withContext(Dispatchers.Main) {
            val node = info?.channel!!
            val channel = Channel(
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
            _title.value = channel.title
            _matureContent.value = channel.matureContent
            _language.value = channel.language
            _category.value = channel.category
            _tags.value = channel.tags
            _streamerDisplayname.value = channel.streamerDisplayName
            _streamerUsername.value = info?.channel?.streamer?.username
            _streamerAvatarUrl.value = URL(channel.streamerAvatarUrl)
            _viewerCount.value = info?.channel?.stream?.countViewers
            _videoThumbnailUrl.value = URL(channel.streamThumbnailUrl)
        }
    }
}