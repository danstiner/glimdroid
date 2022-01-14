package tv.glimesh.ui.channel

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
import tv.glimesh.data.GlimeshDataSource
import tv.glimesh.data.GlimeshWebsocketDataSource
import tv.glimesh.data.model.ChannelId
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
            _title.value = info?.channel?.title ?: ""
            _streamerDisplayname.value = info?.channel?.streamer?.displayname ?: ""
            _streamerUsername.value = info?.channel?.streamer?.username ?: ""
            _streamerAvatarUrl.value = info?.channel?.streamer?.avatarUrl?.let { URL(it) }
            _viewerCount.value = info?.channel?.stream?.countViewers
            _videoThumbnailUrl.value = info?.channel?.stream?.thumbnailUrl?.let { URL(it) }
        }
    }
}