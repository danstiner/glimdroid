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
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                .createIceServer(),
        )

        // See https://github.com/meetecho/janus-gateway/blob/master/html/janus.js
        private val RTC_CONFIGURATION = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            // plan-b still works but has been deprecated in favor of unified-plan
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Start gathering ICE candidates immediately to optimize time to connect
            // See https://chromestatus.com/feature/4973817285836800
            iceCandidatePoolSize = 1
            networkPreference = PeerConnection.AdapterType.WIFI
        }

        private val MEDIA_CONSTRAINTS = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
            optional.add(MediaConstraints.KeyValuePair("googIPv6", "true"))
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