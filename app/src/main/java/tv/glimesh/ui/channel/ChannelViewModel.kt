package tv.glimesh.ui.channel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.*
import tv.glimesh.data.GlimeshDataSource
import tv.glimesh.data.JanusRestApi
import tv.glimesh.data.model.ChannelId
import java.net.URL
import java.util.concurrent.ExecutorService

const val TAG = "StreamViewModel"

class ChannelViewModel(
    private val janus: JanusRestApi,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val executor: ExecutorService,
    private val glimesh: GlimeshDataSource,
) : ViewModel() {

    private val _title = MutableLiveData<String>()
    val title: LiveData<String> = _title

    private val _streamerDisplayname = MutableLiveData<String>()
    val streamerDisplayname: LiveData<String> = _streamerDisplayname

    private val _streamerUsername = MutableLiveData<String>()
    val streamerUsername: LiveData<String> = _streamerUsername

    private val _streamerAvatarUrl = MutableLiveData<URL?>()
    val streamerAvatarUrl: LiveData<URL?> = _streamerAvatarUrl

    private val _viewerCount = MutableLiveData<Int?>()
    val viewerCount: LiveData<Int?> = _viewerCount

    private val _videoTrack = MutableLiveData<VideoTrack>()
    val videoTrack: LiveData<VideoTrack> = _videoTrack

    var currentPeerConnection: PeerConnection? = null
    var currentChannel: ChannelId? = null

    fun watch(channel: ChannelId) {
        viewModelScope.launch(Dispatchers.IO) {

            if (currentChannel == channel) {
                return@launch
            }

            // Close previous connection, if any
            currentPeerConnection?.close()
            currentChannel = channel

            fetchChannelInfo(channel)
            val session = janus.createSession()
            val plugin = janus.attachPlugin(session, "janus.plugin.ftl")

            janus.ftlWatchChannel(session, plugin, channel)

            val iceServers: List<PeerConnection.IceServer> = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
            )

            val rtcConfiguration = PeerConnection.RTCConfiguration(iceServers).apply {
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED

                continualGatheringPolicy =
                    PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                enableDtlsSrtp = true
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            }

            val mediaConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
                optional.add(MediaConstraints.KeyValuePair("googIPv6", "true"))
            }

            // Wait for sdp offer
            val sdpOfferDescription = janus.waitForSdpOffer(session)

            // Create sdp answer
            val offer = SessionDescription(
                SessionDescription.Type.OFFER, sdpOfferDescription
            )

            executor.execute {
                currentPeerConnection = peerConnectionFactory.createPeerConnection(
                    rtcConfiguration,
                    mediaConstraints,
                    object : PeerConnection.Observer {
                        override fun onSignalingChange(state: PeerConnection.SignalingState) {
                            Log.d(TAG, "onSignalingChange: $state")
                        }

                        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                            Log.d(TAG, "onIceConnectionReceivingChange: $state")
                        }

                        override fun onIceConnectionReceivingChange(receiving: Boolean) {
                            Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
                        }

                        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                            Log.d(TAG, "onIceGatheringChange: $state")
                        }

                        override fun onIceCandidate(candidate: IceCandidate?) {
                            Log.d(TAG, "onIceCandidate: $candidate")
                            viewModelScope.launch(Dispatchers.IO) {
                                if (candidate != null) {
                                    janus.trickleIceCandidate(
                                        session,
                                        plugin,
                                        tv.glimesh.data.IceCandidate(
                                            candidate = candidate.sdp,
                                            sdpMid = candidate.sdpMid,
                                            sdpMLineIndex = candidate.sdpMLineIndex
                                        )
                                    )
                                }
                            }
                        }

                        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {
                            Log.d("onIceCandidatesRemoved", candidates.toString())
                        }

                        override fun onAddStream(stream: MediaStream) {
                            Log.d(TAG, "stream: $stream")

                            viewModelScope.launch(Dispatchers.Main) {
                                assert(stream.videoTracks.size == 1)
                                _videoTrack.value = stream.videoTracks[0]
                            }
                        }

                        override fun onRemoveStream(stream: MediaStream) {
                            TODO("Not yet implemented")
                        }

                        override fun onDataChannel(channel: DataChannel) {
                            TODO("Not yet implemented")
                        }

                        override fun onRenegotiationNeeded() {
                            TODO("Not yet implemented")
                        }

                        override fun onAddTrack(
                            receiver: RtpReceiver,
                            streams: Array<out MediaStream>
                        ) {
                            Log.d(TAG, "onAddTrack")
                        }
                    }
                )

                currentPeerConnection!!.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) {
                        Log.d(TAG, "setRemoteDescription: onCreateSuccess")
                    }

                    override fun onSetSuccess() {
                        Log.d(TAG, "setRemoteDescription: onSetSuccess")
                        executor.execute {
                            currentPeerConnection!!.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(answer: SessionDescription) {
                                    Log.d(TAG, "createAnswer:onCreateSuccess: $answer")
                                    viewModelScope.launch(Dispatchers.IO) {
                                        janus.ftlStart(
                                            session,
                                            plugin,
                                            answer.description,
                                            arrayOf()
                                        )

                                        while (true) {
                                            delay(30_000)
                                            val events = janus.longPollSession(session)
                                            // TODO do something with events
                                        }
                                    }
                                    currentPeerConnection!!.setLocalDescription(object :
                                        SdpObserver {
                                        override fun onCreateSuccess(description: SessionDescription) {
                                            Log.d(
                                                TAG,
                                                "setLocalDescription:onCreateSuccess: $description"
                                            )
                                        }

                                        override fun onSetSuccess() {
                                            Log.d(TAG, "setLocalDescription:onSetSuccess")
                                        }

                                        override fun onCreateFailure(p0: String?) {
                                            TODO("Not yet implemented")
                                        }

                                        override fun onSetFailure(p0: String?) {
                                            TODO("Not yet implemented")
                                        }

                                    }, answer)
                                }

                                override fun onSetSuccess() {
                                    TODO("Not yet implemented")
                                }

                                override fun onCreateFailure(error: String) {
                                    TODO("Not yet implemented")
                                }

                                override fun onSetFailure(error: String) {
                                    TODO("Not yet implemented")
                                }

                            }, mediaConstraints)
                        }
                    }

                    override fun onCreateFailure(error: String) {
                        TODO("Not yet implemented")
                    }

                    override fun onSetFailure(error: String) {
                        TODO("Not yet implemented")
                    }
                }, offer)

            }

            // Wait for webrtcup?
            delay(10000)
        }
    }

    override fun onCleared() {
        currentPeerConnection?.close()
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
        }
    }
}