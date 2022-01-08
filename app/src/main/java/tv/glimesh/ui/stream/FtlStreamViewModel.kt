package tv.glimesh.ui.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription




class FtlStreamViewModel(val janus: JanusViewModel) : ViewModel() {
    fun watch() {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionId = janus.createSession()
            val pluginId = janus.attachPlugin(sessionId, "janus.plugin.ftl")
            janus.ftlWatchChannel(sessionId, pluginId, 7)

            // Wait for sdp offer

            // Create sdp answer
            val offer = SessionDescription(
                SessionDescription.Type.OFFER, TODO("sdpString")
            )

            val factory = PeerConnectionFactory.builder()
                .createPeerConnectionFactory()

            val peerConnection = factory.createPeerConnection(TODO() as PeerConnection.RTCConfiguration, TODO() as PeerConnection.Observer)

//            peerConnection.setRemoteDescription(TODO(), offer)

            // Wait for webrtcup
//            while (true) {
//                var events = janus.longPollSession(sessionId)
//                // TODO handle events
//                delay(1000)
//            }
            delay(3000)

//            janus.ftlStart(sessionId, pluginId, sdp)
        }
    }
}