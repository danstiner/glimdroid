package tv.glimesh.android.ui.channel

import android.util.Log
import kotlinx.coroutines.*
import org.webrtc.*
import tv.glimesh.android.data.JanusRestApi
import tv.glimesh.android.data.SessionId
import tv.glimesh.android.data.model.ChannelId
import java.net.URL
import kotlin.coroutines.CoroutineContext

class JanusRtcConnection(
    private val janus: JanusRestApi,
    private val session: SessionId,
    private val channel: ChannelId,
    private val peerConnection: WrappedPeerConnection,
    private val coroutineContext: CoroutineContext
) {
    val isClosed: Boolean
        get() = peerConnection.connectionState == PeerConnection.PeerConnectionState.CLOSED

    fun close() {
        Log.d(TAG, "Closing connection $peerConnection")
        peerConnection?.close()
        coroutineContext.cancel()
    }

    suspend fun loop() {
        // Long poll janus for events while the connection is alive
        while (peerConnection.connectionState != PeerConnection.PeerConnectionState.CLOSED) {
            Log.d(TAG, "Polling Janus; channel:$channel, state:${peerConnection.connectionState}")
            try {
                val events = janus.longPollSession(session)
                // TODO do something with events
            } catch (ex: java.io.FileNotFoundException) {
                Log.w(TAG, "Janus session done, closing connection: $ex")
                break
            }

            // Short wait to avoid spamming server, technically not
            // needed since this is a long poll request
            delay(1_000)
        }

        // TODO forcibly end stream
        Log.d(
            TAG,
            "Stream finished, letting it timeout on the janus side"
        )
        close()
    }

    companion object {
        // This is the list of servers janus.js uses
        private val ICE_SERVERS: List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        private val RTC_CONFIGURATION = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy =
                PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            enableDtlsSrtp = true
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }

        private val MEDIA_CONSTRAINTS = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }

        suspend fun create(
            janusBaseUrl: URL,
            channel: ChannelId,
            peerConnectionFactory: WrappedPeerConnectionFactory,
            onAddStream: (stream: MediaStream) -> Unit
        ): JanusRtcConnection {
            val coroutineContext = SupervisorJob() + Dispatchers.IO
            val coroutineScope = CoroutineScope(coroutineContext)

            val janus = JanusRestApi(janusBaseUrl)
            val session = janus.createSession()
            val plugin = janus.attachPlugin(session, "janus.plugin.ftl")

            janus.ftlWatchChannel(session, plugin, channel)

            // Wait for sdp offer
            val offer = SessionDescription(
                SessionDescription.Type.OFFER, janus.waitForSdpOffer(session)
            )

            val peerConnection = peerConnectionFactory.createPeerConnection(
                RTC_CONFIGURATION,
                { candidate: IceCandidate ->
                    coroutineScope.launch {
                        janus.trickleIceCandidate(
                            session,
                            plugin,
                            tv.glimesh.android.data.IceCandidate(
                                candidate = candidate.sdp,
                                sdpMid = candidate.sdpMid,
                                sdpMLineIndex = candidate.sdpMLineIndex
                            )
                        )
                    }
                },
                onAddStream,
            )
            val connection = JanusRtcConnection(
                janus, session, channel, peerConnection, coroutineContext,
            )

            coroutineScope.launch {
                // Setup peer connection
                peerConnection.setRemoteDescription(offer)
                val answer = peerConnection.createAnswer(MEDIA_CONSTRAINTS)
                peerConnection.setLocalDescription(answer)

                // Tell janus we are ready to start the stream
                janus.ftlStart(
                    session,
                    plugin,
                    answer.description
                )

                connection.loop()
            }

            return connection
        }
    }
}