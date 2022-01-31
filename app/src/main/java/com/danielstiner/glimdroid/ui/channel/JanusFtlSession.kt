package com.danielstiner.glimdroid.ui.channel

import android.util.Log
import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.janus.JanusApi
import com.danielstiner.janus.PluginId
import com.danielstiner.janus.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class JanusFtlSession(
    private val janus: JanusApi,
    private val session: SessionId,
    private val plugin: PluginId,
    internal val channel: ChannelId,
) {
    @Volatile
    var isStarted: Boolean = false
    private var offer: SessionDescription? = null

    @Volatile
    private var destroyed = false

    fun getSdpOffer(): SessionDescription {

        if (offer == null) {
            // Wait for sdp offer
            offer = SessionDescription(
                SessionDescription.Type.OFFER, janus.waitForSdpOffer(session)
            )
        }

        return offer!!
    }

    suspend fun start(answer: SessionDescription, coroutineScope: CoroutineScope) {
        assert(answer.type == SessionDescription.Type.ANSWER)
        assert(!destroyed)
        assert(!isStarted)

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

    fun trickleIceCandidate(candidate: IceCandidate) =
        janus.trickleIceCandidate(
            session,
            plugin,
            com.danielstiner.janus.IceCandidate(
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        )

    fun destroy() {
        destroyed = true
        try {
            janus.destroy(session)
        } catch (ex: JanusApi.NoSuchSessionException) {
            Log.w(TAG, "No such session to destroy; channel:$channel")
        }
    }

    private suspend fun loop() {
        // Long poll janus for events while the connection is alive
        while (!destroyed) {
            Log.d(TAG, "Polling Janus; channel:$channel")
            try {
                for (event in janus.longPollSession(session)) {
                    // TODO do something with events
                }
            } catch (ex: JanusApi.NoSuchSessionException) {
                Log.d(TAG, "Janus session done, closing connection: $ex")
                TODO("Handle Janus session closed, should try start a new session")
                destroyed = true
                break
            }

            // Short wait to avoid spamming server, technically not
            // needed since this is a long poll request
            delay(1_000)
        }

        Log.d(TAG, "Looper stopped for janus session; channel:$channel")
    }

    companion object {
        private const val TAG = "JanusFtlSession"

        fun create(
            janusBaseUrl: HttpUrl,
            channel: ChannelId
        ): JanusFtlSession {
            val janus = JanusApi(janusBaseUrl)

            val session = janus.createSession()
            val plugin = janus.attachPlugin(session, "janus.plugin.ftl")
            janus.ftlWatchChannel(session, plugin, channel)

            return JanusFtlSession(janus, session, plugin, channel)
        }
    }
}
