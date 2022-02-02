package com.danielstiner.janus

import android.util.Log
import com.danielstiner.glimdroid.data.model.ChannelId
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration
import java.util.*

fun transactionId(): String = UUID.randomUUID().toString()

@Serializable
data class SessionId(val id: Long)

@Serializable
data class PluginId(val id: Long)

@Serializable
data class JanusError(val code: Int, val reason: String)

@Serializable
data class IceCandidate(val candidate: String, val sdpMid: String, val sdpMLineIndex: Int)

@Serializable
data class CreateSessionRequest(
    @Required val janus: String = "create",
    val transaction: String = transactionId()
)

@Serializable
data class CreateSessionResponse(
    val data: SessionId,
    val janus: String,
    val transaction: String,
    val error: JanusError? = null
)

/*
Examples:
[
    {
          "janus": "event",
          "session_id": 7415323739446857,
          "sender": 4830019477610560,
          "plugindata": {
             "plugin": "janus.plugin.ftl",
             "data": {
                "streaming": "event",
                "result": {
                   "status": "preparing"
                }
             }
          },
          "jsep": {
             "type": "offer",
             "sdp": "v=0\r\no=- 1641772499772226 1 IN IP4 104.131.103.240\r\ns=tv.glimesh.phoenix.channel.Channel 10552\r\nt=0 0\r\na=group:BUNDLE audio video\r\na=msid-semantic: WMS janus\r\nm=audio 9 UDP/TLS/RTP/SAVPF 97\r\nc=IN IP4 104.131.103.240\r\na=sendonly\r\na=mid:audio\r\na=rtcp-mux\r\na=ice-ufrag:WWOA\r\na=ice-pwd:gLNRMGY9vXZ7oYlgb9QlBN\r\na=ice-options:trickle\r\na=fingerprint:sha-256 47:91:B2:76:9A:15:8E:A2:78:BB:64:39:31:41:BF:C9:6B:91:B4:B9:FD:0C:B2:3E:6F:5D:96:BB:0F:07:DB:09\r\na=setup:actpass\r\na=rtpmap:97 opus/48000/2\r\na=extmap:1 urn:ietf:params:rtp-hdrext:sdes:mid\r\na=msid:janus janusa0\r\na=ssrc:3424953280 cname:janus\r\na=ssrc:3424953280 msid:janus janusa0\r\na=ssrc:3424953280 mslabel:janus\r\na=ssrc:3424953280 label:janusa0\r\na=candidate:1 1 udp 2015363327 104.131.103.240 52932 typ host\r\na=candidate:2 1 udp 2015363583 10.17.0.7 42154 typ host\r\na=candidate:3 1 udp 2015363839 10.108.0.4 34552 typ host\r\na=end-of-candidates\r\nm=video 9 UDP/TLS/RTP/SAVPF 96 97\r\nc=IN IP4 104.131.103.240\r\na=sendonly\r\na=mid:video\r\na=rtcp-mux\r\na=ice-ufrag:WWOA\r\na=ice-pwd:gLNRMGY9vXZ7oYlgb9QlBN\r\na=ice-options:trickle\r\na=fingerprint:sha-256 47:91:B2:76:9A:15:8E:A2:78:BB:64:39:31:41:BF:C9:6B:91:B4:B9:FD:0C:B2:3E:6F:5D:96:BB:0F:07:DB:09\r\na=setup:actpass\r\na=rtpmap:96 H264/90000\r\na=fmtp:96 profile-level-id=42e01f;packetization-mode=1;\r\na=rtcp-fb:96 nack\r\na=rtcp-fb:96 nack pli\r\na=extmap:1 urn:ietf:params:rtp-hdrext:sdes:mid\r\na=rtpmap:97 rtx/90000\r\na=fmtp:97 apt=96\r\na=ssrc-group:FID 44610768 2245469848\r\na=msid:janus janusv0\r\na=ssrc:44610768 cname:janus\r\na=ssrc:44610768 msid:janus janusv0\r\na=ssrc:44610768 mslabel:janus\r\na=ssrc:44610768 label:janusv0\r\na=ssrc:2245469848 cname:janus\r\na=ssrc:2245469848 msid:janus janusv0\r\na=ssrc:2245469848 mslabel:janus\r\na=ssrc:2245469848 label:janusv0\r\na=candidate:1 1 udp 2015363327 104.131.103.240 52932 typ host\r\na=candidate:2 1 udp 2015363583 10.17.0.7 42154 typ host\r\na=candidate:3 1 udp 2015363839 10.108.0.4 34552 typ host\r\na=end-of-candidates\r\n"
          }
       },
       {
          "janus": "webrtcup",
          "session_id": 7415323739446857,
          "sender": 4830019477610560
       },
       {
          "janus": "slowlink",
          "session_id": 7415323739446857,
          "sender": 4830019477610560,
          "media": "video",
          "uplink": true,
          "lost": 6
       },
       {
          "janus": "keepalive"
       }
]
 */
@Serializable
data class SessionEvent(
    val janus: String,
    val plugindata: SessionEventPluginData? = null,
    val jsep: Jsep? = null,
    val sender: Long? = null,
    val transaction: String? = null,
    val session_id: Long? = null,
    val media: String? = null,
    val uplink: Boolean? = null,
    val lost: Int? = null,
    val reason: String? = null,
)

@Serializable
data class SessionEventPluginData(val plugin: String, val data: JsonObject)

@Serializable
data class Jsep(val type: String, val sdp: String)

@Serializable
data class AttachRequest(
    val plugin: String,
    @Required val janus: String = "attach",
    val transaction: String = transactionId()
)

@Serializable
data class AttachResponse(
    val data: PluginId? = null,
    val janus: String,
    val transaction: String,
    val session_id: Long? = null,
    val error: JanusError? = null
)

@Serializable
data class FtlWatchRequest(
    val body: FtlWatchRequestBody,
    @Required val janus: String = "message",
    val transaction: String = transactionId()
)

@Serializable
data class FtlWatchRequestBody(val channelId: Long, @Required val request: String = "watch")

@Serializable
data class WatchResponse(
    val janus: String,
    val transaction: String? = null,
    val session_id: Long? = null,
    val error: JanusError? = null
)

@Serializable
data class FtlStartRequest(
    val jsep: Jsep,
    @Required val body: FtlStartRequestBody = FtlStartRequestBody(),
    @Required val janus: String = "message",
    val transaction: String = transactionId()
)

@Serializable
data class FtlStartRequestBody(@Required val request: String = "start")

@Serializable
data class FtlStartResponse(
    val janus: String,
    val transaction: String,
    val session_id: Long,
    val error: JanusError? = null
)

@Serializable
data class TrickleRequest(
    val candidate: IceCandidate,
    @Required val janus: String = "trickle",
    val transaction: String = transactionId()
)

@Serializable
data class TrickleResponse(
    val janus: String,
    val transaction: String,
    val session_id: Long,
    val error: JanusError? = null,
)

@Serializable
data class DestroyRequest(
    @Required val janus: String = "destroy",
    val transaction: String = transactionId()
)

@Serializable
data class DestroyResponse(
    val janus: String,
    val transaction: String,
    val session_id: Long? = null,
    val error: JanusError? = null,
)

class JanusApi(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient = OkHttpClient()
) {

    // Session events are read by long-polling with a parameter-less GET request to the session
    // endpoint. This returns as soon as events are available, or after a 30 second timeout. If
    // there were no events to report, a simple keep-alive event message will be returned instead.
    private val pollClient = client.newBuilder().readTimeout(Duration.ofSeconds(60)).build()

    fun createSession(): SessionId {
        val request = CreateSessionRequest()
        val response: CreateSessionResponse = post(baseUrl, request)

        if (response.error != null) {
            throw JanusErrorException(response.error.code, response.error.reason)
        }

        assert(response.janus == "success")
        assert(response.transaction == request.transaction)
        return response.data
    }

    fun attachPlugin(session: SessionId, plugin: String): PluginId {
        try {
            val sessionUri = baseUrl.newBuilder()
                .addPathSegment("${session.id}")
                .build()

            val request = AttachRequest(plugin)
            val response: AttachResponse = post(sessionUri, request)

            if (response.error != null) {
                throw JanusErrorException(response.error.code, response.error.reason)
            }

            assert(response.janus == "success")
            assert(response.transaction == request.transaction)
            assert(response.session_id == session.id)
            return response.data!!
        } catch (ex: RequestFailedException) {
            if (ex.code == 404) {
                throw NoSuchSessionException(ex)
            } else {
                throw ex
            }
        }
    }

    fun ftlWatchChannel(session: SessionId, plugin: PluginId, channel: ChannelId) {
        try {
            val pluginUri = baseUrl.newBuilder()
                .addPathSegment("${session.id}")
                .addPathSegment("${plugin.id}")
                .build()

            val request = FtlWatchRequest(FtlWatchRequestBody(channel.id))
            val response: WatchResponse = post(pluginUri, request)

            if (response.error != null) {
                throw JanusErrorException(response.error.code, response.error.reason)
            }

            assert(response.janus == "ack")
            assert(response.transaction == request.transaction)
            assert(response.session_id == session.id)
        } catch (ex: RequestFailedException) {
            if (ex.code == 404) {
                throw NoSuchSessionException(ex)
            } else {
                throw ex
            }
        }
    }

    fun ftlStart(
        session: SessionId,
        plugin: PluginId,
        sdpAnswer: String
    ) {
        /* TODO
        // HACK: Workaround for Chromium not enabling stereo audio by default
        // https://bugs.chromium.org/p/webrtc/issues/detail?id=8133
        if (jsep.sdp.indexOf("stereo=1") == -1) {
            jsep.sdp = jsep.sdp.replace("useinbandfec=1", "useinbandfec=1;stereo=1");
        } */

        try {
            val pluginUri = baseUrl.newBuilder()
                .addPathSegment("${session.id}")
                .addPathSegment("${plugin.id}")
                .build()

            val request = FtlStartRequest(Jsep(type = "answer", sdp = sdpAnswer))
            val response: FtlStartResponse = post(pluginUri, request)

            if (response.error != null) {
                throw JanusErrorException(response.error.code, response.error.reason)
            }

            assert(response.janus == "ack")
            assert(response.transaction == request.transaction)
            assert(response.session_id == session.id)
        } catch (ex: RequestFailedException) {
            if (ex.code == 404) {
                throw NoSuchSessionException(ex)
            } else {
                throw ex
            }
        }
    }

    fun trickleIceCandidate(session: SessionId, plugin: PluginId, candidate: IceCandidate) {
        try {
            val pluginUri = baseUrl.newBuilder()
                .addPathSegment("${session.id}")
                .addPathSegment("${plugin.id}")
                .build()

            val request = TrickleRequest(candidate)
            val response: TrickleResponse = post(pluginUri, request)

            if (response.error != null) {
                throw JanusErrorException(response.error.code, response.error.reason)
            }

            assert(response.janus == "ack")
            assert(response.transaction == request.transaction)
            assert(response.session_id == session.id)
        } catch (ex: RequestFailedException) {
            if (ex.code == 404) {
                throw NoSuchSessionException(ex)
            } else {
                throw ex
            }
        }
    }

    fun longPollSession(session: SessionId): List<SessionEvent> {
        try {
            val uri = baseUrl.newBuilder()
                .addPathSegment("${session.id}")
                .addQueryParameter("maxev", "10")
                .addQueryParameter("rid", "${System.currentTimeMillis()}")
                .build()
            return poll(uri)
        } catch (ex: RequestFailedException) {
            if (ex.code == 404) {
                throw NoSuchSessionException(ex)
            } else {
                throw ex
            }
        }
    }

    fun destroy(session: SessionId) {
        try {
            val sessionUri = baseUrl.newBuilder()
                .addPathSegment("${session.id}")
                .build()

            val request = DestroyRequest()
            val response: DestroyResponse = post(sessionUri, request)

            if (response.error != null) {
                throw JanusErrorException(response.error.code, response.error.reason)
            }

            assert(response.janus == "success")
            assert(response.transaction == request.transaction)
            assert(response.session_id == session.id)
        } catch (ex: RequestFailedException) {
            throw NoSuchSessionException(ex)
        }
    }

    fun waitForSdpOffer(session: SessionId): String {
        while (true) {
            for (event in longPollSession(session)) {
                Log.d("Janus Event", Json.encodeToString(event))
                if (event.jsep != null && event.jsep.type == "offer") {
                    // TODO also verify transaction id
                    return event.jsep.sdp
                }
            }
        }
    }

    private inline fun <reified R> poll(url: HttpUrl): R {
        val response = pollClient.newCall(
            Request.Builder().url(url).build()
        ).execute()

        if (!response.isSuccessful) {
            throw RequestFailedException(response.code, response.message)
        }

        return Json.decodeFromStream(response.body!!.byteStream())
    }

    private inline fun <reified T, reified R> post(url: HttpUrl, bodyJson: T): R {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .post(Json.encodeToString(bodyJson).toRequestBody(JSON))
                .build()
        ).execute()

        if (!response.isSuccessful) {
            throw RequestFailedException(response.code, response.message)
        }

        return Json.decodeFromStream(response.body!!.byteStream())
    }

    class RequestFailedException(val code: Int, message: String) : Throwable("HTTP $code: $message")

    class JanusErrorException(code: Int, reason: String) : Throwable("Janus error $code: $reason")

    class NoSuchSessionException(cause: RequestFailedException) : Throwable(cause)

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
