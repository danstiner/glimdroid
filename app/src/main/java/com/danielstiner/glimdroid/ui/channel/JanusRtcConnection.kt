package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import kotlinx.coroutines.*
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import kotlin.coroutines.CoroutineContext

class JanusRtcConnection(
    private val session: JanusFtlSession,
    private val peerConnection: WrappedPeerConnection,
    private val coroutineContext: CoroutineContext
) {

    val coroutineScope = CoroutineScope(coroutineContext)
    val isClosed: Boolean
        get() = peerConnection.connectionState == PeerConnection.PeerConnectionState.CLOSED

    fun close() {
        Log.d(TAG, "Closing connection $peerConnection")
        peerConnection.close()
        coroutineContext.cancel()
    }


    fun start() {
        coroutineScope.launch {
            startAsync()
        }
    }

    private suspend fun startAsync() {
        peerConnection.setRemoteDescription(session.getSdpOffer())

        val answer = peerConnection.createAnswer(MEDIA_CONSTRAINTS)
        peerConnection.setLocalDescription(answer)

        if (!session.isStarted) {
            // Tell janus we are ready to start the stream
            session.start(answer, coroutineScope)
        }
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

        fun create(
            session: JanusFtlSession,
            peerConnectionFactory: WrappedPeerConnectionFactory,
            coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.IO,
            onAddStream: (stream: MediaStream) -> Unit
        ): JanusRtcConnection {
            val coroutineScope = CoroutineScope(coroutineContext)

            val peerConnection = peerConnectionFactory.createPeerConnection(
                RTC_CONFIGURATION,
                coroutineScope,
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