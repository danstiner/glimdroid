package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import com.danielstiner.glimdroid.data.JanusRestApi
import com.danielstiner.glimdroid.data.PluginId
import com.danielstiner.glimdroid.data.SessionId
import com.danielstiner.glimdroid.data.model.ChannelId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.net.URL

class JanusFtlSession(
    private val janus: JanusRestApi,
    private val session: SessionId,
    private val plugin: PluginId,
    private val channel: ChannelId,
) {
    @Volatile
    var isStarted: Boolean = false
    private var answer: SessionDescription? = null
    private var offer: SessionDescription? = null

    @Volatile
    private var destroyed = false

    suspend fun getSdpOffer(): SessionDescription {

        if (offer == null) {
            // Wait for sdp offer
            offer = SessionDescription(
                SessionDescription.Type.OFFER, janus.waitForSdpOffer(session)
            )
        }

        return offer!!
    }

    fun getSdpAnswer(): SessionDescription {
        return answer!!
    }

    suspend fun start(answer: SessionDescription, coroutineScope: CoroutineScope) {
        assert(answer.type == SessionDescription.Type.ANSWER)

        this.answer = answer

        // Tell janus we are ready to start the stream
        janus.ftlStart(
            session,
            plugin,
            answer.description
        )

        isStarted = true

        coroutineScope.launch {
            loop()
        }
    }

    suspend fun trickleIceCandidate(candidate: IceCandidate) =
        janus.trickleIceCandidate(
            session,
            plugin,
            com.danielstiner.glimdroid.data.IceCandidate(
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        )

    suspend fun destroy() {
        destroyed = true
        janus.destroy(session)
    }

    private suspend fun loop() {
        // Long poll janus for events while the connection is alive
        while (!destroyed) {
            Log.d(TAG, "Polling Janus; channel:$channel")
            try {
                janus.longPollSession(session)
                // TODO do something with events
            } catch (ex: java.io.FileNotFoundException) {
                Log.w(TAG, "Janus session done, closing connection: $ex")
                // TODO inform RTC connection the session has closed prematurely
                destroyed = true
                break
            }

            // Short wait to avoid spamming server, technically not
            // needed since this is a long poll request
            delay(1_000)
        }
    }

    companion object {
        private const val TAG = "JanusFtlSession"

        suspend fun create(
            janusBaseUrl: URL,
            channel: ChannelId
        ): JanusFtlSession {
            val janus = JanusRestApi(janusBaseUrl)

            val session = janus.createSession()
            val plugin = janus.attachPlugin(session, "janus.plugin.ftl")
            janus.ftlWatchChannel(session, plugin, channel)

            return JanusFtlSession(janus, session, plugin, channel)
        }
    }
}