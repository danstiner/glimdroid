package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.*
import kotlin.coroutines.CoroutineContext

class JanusRtcConnection(
    private val session: JanusFtlSession,
    private val peerConnection: WrappedPeerConnection,
    coroutineContext: CoroutineContext
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    @Volatile
    private var isClosed: Boolean = false

    fun close() {
        Log.d(TAG, "Closing connection $peerConnection for channel:${session.channel}")
        isClosed = true
        peerConnection.close()
    }

    suspend fun start() {

        val answer: SessionDescription

        try {
            if (isClosed) {
                return
            }
            peerConnection.setRemoteDescription(session.getSdpOffer())

            if (isClosed) {
                return
            }
            answer = peerConnection.createAnswer(MEDIA_CONSTRAINTS)

            if (isClosed) {
                return
            }
            peerConnection.setLocalDescription(answer)

            if (isClosed) {
                return
            }
        } catch (ex: WrappedPeerConnection.SdpException) {
            Log.w(
                TAG,
                "Failed to set SDP description, connection already closed: ${ex.message}",
                ex
            )
            return
        }

        if (!session.isStarted) {
            // Tell janus we are ready to start the stream
            session.start(answer, coroutineScope)
        }
    }

    companion object {
        private const val TAG = "JanusRtcConnection"

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

        fun create(
            session: JanusFtlSession,
            peerConnectionFactory: WrappedPeerConnectionFactory,
            coroutineContext: CoroutineContext,
            onAddStream: (stream: MediaStream) -> Unit
        ): JanusRtcConnection {
            val coroutineScope = CoroutineScope(coroutineContext)

            val peerConnection = peerConnectionFactory.createPeerConnection(
                RTC_CONFIGURATION,
                { candidate: IceCandidate ->
                    coroutineScope.launch {
                        // todo check for cleared?
                        session.trickleIceCandidate(candidate)
                    }
                },
                // todo check for cleared
                onAddStream,
            )

            return JanusRtcConnection(session, peerConnection, coroutineContext)
        }
    }
}