package tv.glimesh.ui.stream

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.*

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

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
data class CreateRequest(@Required val janus: String = "create", val transaction: String = transactionId())

@Serializable
data class CreateResponse(val data: SessionId, val janus: String, val transaction: String, val error: JanusError? = null)

@Serializable
data class SessionEvent(val plugindata: SessionEventPluginData, val jsep: Jsep? = null, val janus: String, val sender: Long, val transaction: String? = null, val session_id: Long? = null,)

@Serializable
data class SessionEventPluginData(val plugin: String, val data: JsonObject)

@Serializable
data class Jsep(val type: String, val sdp: String)

@Serializable
data class AttachRequest(val plugin: String, @Required val janus: String = "attach", val transaction: String = transactionId())

@Serializable
data class AttachResponse(val data: PluginId? = null, val janus: String, val transaction: String, val session_id: Long? = null, val error: JanusError? = null)

@Serializable
data class MessageRequest(val body: Map<String, String>, @Required val janus: String = "message", val transaction: String = transactionId())

@Serializable
data class MessageResponse(val janus: String, val transaction: String, val session_id: Long)

@Serializable
data class FtlWatchRequest(val body: FtlWatchRequestBody, @Required val janus: String = "message", val transaction: String = transactionId())

@Serializable
data class FtlWatchRequestBody(val channelId: Long, @Required val request: String = "watch")

@Serializable
data class WatchResponse(val janus: String, val transaction: String? = null, val session_id: Long? = null, val error: JanusError? = null)

@Serializable
data class FtlStartRequest(val jsep: Jsep, @Required val body: FtlStartRequestBody = FtlStartRequestBody(), @Required val janus: String = "message", val transaction: String = transactionId())

@Serializable
data class FtlStartRequestBody(@Required val request: String = "start")

@Serializable
data class FtlStartResponse(val janus: String, val transaction: String, val session_id: Long)

@Serializable
data class TrickleRequest(val candidate: IceCandidate, @Required val janus: String = "trickle", val transaction: String = transactionId())

@Serializable
data class TrickleResponse(val janus: String, val transaction: String, val session_id: Long)

class JanusRestApi(private val serverRoot: Uri) {

    suspend fun createSession(): SessionId {
        val request = CreateRequest()
        val response: CreateResponse = post(serverRoot, request)
        assert(response.janus == "success")
        assert(response.transaction == request.transaction)
        return response.data
    }


    suspend fun attachPlugin(session: SessionId, plugin: String): PluginId {
        val sessionUri = serverRoot.buildUpon().appendPath("${session.id}").build()

        val request = AttachRequest(plugin)
        val response: AttachResponse = post(sessionUri, request)

        assert(response.janus == "success")
        assert(response.transaction == request.transaction)
        assert(response.session_id == session.id)
        return response.data!!
    }

    suspend fun ftlWatchChannel(session: SessionId, plugin: PluginId, channelId: Long) {
        val sessionUri = serverRoot.buildUpon().appendPath("${session.id}").build()
        val pluginUri = sessionUri.buildUpon().appendPath("${plugin.id}").build()

        val request = FtlWatchRequest(FtlWatchRequestBody(channelId))
        val response: WatchResponse = post(pluginUri, request)

        assert(response.janus == "ack")
        assert(response.transaction == request.transaction)
        assert(response.session_id == session.id)
    }

    suspend fun waitForSdpOffer(session: SessionId): String {
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

    suspend fun ftlStart(
        session: SessionId,
        plugin: PluginId,
        sdpAnswer: String,
        trickleIceCandidates: Array<IceCandidate>
    ) {
        val sessionUri = serverRoot.buildUpon().appendPath("${session.id}").build()
        val pluginUri = sessionUri.buildUpon().appendPath("${plugin.id}").build()
        /*
        // HACK: Workaround for Chromium not enabling stereo audio by default
        // https://bugs.chromium.org/p/webrtc/issues/detail?id=8133
        if (jsep.sdp.indexOf("stereo=1") == -1) {
            jsep.sdp = jsep.sdp.replace("useinbandfec=1", "useinbandfec=1;stereo=1");
        }
         */
        Log.d(TAG, "ftlStart $sdpAnswer $trickleIceCandidates")

        val request = FtlStartRequest(Jsep(type = "answer", sdp = sdpAnswer))
        val response: FtlStartResponse = post(pluginUri, request)

        assert(response.janus == "ack")
        assert(response.transaction == request.transaction)
        assert(response.session_id == session.id)
    }

    suspend fun trickleIceCandidate(session: SessionId, plugin: PluginId, candidate: IceCandidate) {

        val sessionUri = serverRoot.buildUpon().appendPath("${session.id}").build()
        val pluginUri = sessionUri.buildUpon().appendPath("${plugin.id}").build()

        Log.d(TAG, "trickleIceCandidate $candidate")

        val request = TrickleRequest(candidate)
        val response: TrickleResponse = post(pluginUri, request)

        assert(response.janus == "ack")
        assert(response.transaction == request.transaction)
        assert(response.session_id == session.id)
    }

    suspend fun longPollSession(session: SessionId): Array<SessionEvent> {
        val sessionUri = serverRoot.buildUpon().appendPath("${session.id}").build()
        val uri = sessionUri.buildUpon().appendQueryParameter("maxev", "10").appendQueryParameter("rid", "${System.currentTimeMillis()}").build()
        return get(uri)
    }

    suspend fun messagePlugin(session: SessionId, plugin: PluginId, body: Map<String, String>) {
        val sessionUri = serverRoot.buildUpon().appendPath("${session.id}").build()
        val pluginUri = sessionUri.buildUpon().appendPath("${plugin.id}").build()
        val request = MessageRequest(body)
        val response: MessageResponse = post(pluginUri, request)
        assert(response.janus == "ack")
        assert(response.transaction == request.transaction)
        assert(response.session_id == session.id)
    }

    private suspend inline fun <reified R> get(uri: Uri): R {
        return withContext(Dispatchers.IO) {
            val urlConnection = URL("$uri").openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "GET"

                Log.d("Janus GET Request", "$uri")

                val responseBody = urlConnection.inputStream.bufferedReader().use { it.readText() }

                Log.d("Janus GET Response", responseBody)

                return@withContext Json.decodeFromString<R>(responseBody)
            } finally {
                urlConnection.disconnect()
            }
        }
    }
    private suspend inline fun <reified T, reified R> post(uri: Uri, bodyJson: T): R {
        return withContext(Dispatchers.IO) {
            val urlConnection = URL("$uri").openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "POST"
                urlConnection.doOutput = true;
                urlConnection.setChunkedStreamingMode(0);

                Log.d("Janus POST Request", "$uri: ${Json.encodeToString(bodyJson)}")

                urlConnection.outputStream.use { Json.encodeToStream(bodyJson, it) }

                val responseBody = urlConnection.inputStream.bufferedReader().use { it.readText() }

                Log.d("Janus POST Response", responseBody)

                return@withContext Json.decodeFromString<R>(responseBody)
            } finally {
                urlConnection.disconnect()
            }
        }
    }
}