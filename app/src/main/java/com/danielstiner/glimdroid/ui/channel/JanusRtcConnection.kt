package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import kotlin.coroutines.CoroutineContext

class JanusRtcConnection(
    private val session: JanusFtlSession,
    private val peerConnection: WrappedPeerConnection,
    coroutineContext: CoroutineContext
) {
    private val coroutineScope = CoroutineScope(coroutineContext)

    fun close() {
        Log.d(TAG, "Closing connection $peerConnection for channel:${session.channel}")
        peerConnection.close()
    }

    suspend fun start() {
        // Negotiate parameters of our media session with Janus
        try {
            // First set the session description Janus offered for potential parameters
            peerConnection.setRemoteDescription(session.getSdpOffer())

            // Then create an answer of what we'd actually like to receive
            val answer = peerConnection.createAnswer(MEDIA_CONSTRAINTS)

            // Assume Janus will accept our answer and immediate set it locally
            peerConnection.setLocalDescription(answer)

            // Finally send the answer to Janus so it starts to send media
            session.start(answer, coroutineScope)

        } catch (ex: WrappedPeerConnection.SdpException) {
            Log.w(
                TAG,
                "Failed to set SDP description, connection already closed: ${ex.message}",
                ex
            )
            return
        }
    }

    companion object {
        private const val TAG = "JanusRtcConnection"

        // This is the list of servers janus.js uses
        private val ICE_SERVERS: List<PeerConnection.IceServer> = listOf()

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