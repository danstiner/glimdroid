package tv.glimesh.android.ui.channel

import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class WrappedPeerConnection(private val connection: PeerConnection) {

    val connectionState: PeerConnection.PeerConnectionState
        get() = connection.connectionState()

    fun close() {
        connection.close()
    }

    suspend fun createAnswer(constraints: MediaConstraints): SessionDescription =
        suspendCoroutine { continuation ->
            connection.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description != null) {
                        continuation.resume(description)
                    } else {
                        continuation.resumeWithException(SdpException("createAnswer returned null session description"))
                    }
                }

                override fun onSetSuccess() {
                    continuation.resumeWithException(IllegalStateException("onSetSuccess callback should be unreachable from createAnswer"))
                }

                override fun onCreateFailure(error: String?) {
                    continuation.resumeWithException(SdpException(error))
                }

                override fun onSetFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException("onSetFailure callback should be unreachable from createAnswer"))
                }
            }, constraints)
        }

    suspend fun setRemoteDescription(sdp: SessionDescription) =
        suspendCoroutine<Unit> { continuation ->
            connection.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    continuation.resumeWithException(IllegalStateException("onCreateSuccess callback should be unreachable from setRemoteDescription"))
                }

                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException("onCreateFailure callback should be unreachable from setRemoteDescription"))
                }

                override fun onSetFailure(error: String?) {
                    continuation.resumeWithException(SdpException(error))
                }
            }, sdp)
        }

    suspend fun setLocalDescription(sdp: SessionDescription) =
        suspendCoroutine<Unit> { continuation ->
            connection.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    continuation.resumeWithException(IllegalStateException("onCreateSuccess callback should be unreachable from setLocalDescription"))
                }

                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException("onCreateFailure callback should be unreachable from setLocalDescription"))
                }

                override fun onSetFailure(error: String?) {
                    continuation.resumeWithException(SdpException(error))
                }
            }, sdp)
        }

    class SdpException(error: String?) : RuntimeException(error)
}