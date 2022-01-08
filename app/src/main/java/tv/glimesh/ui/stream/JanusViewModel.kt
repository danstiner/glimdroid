package tv.glimesh.ui.stream

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

fun transactionId(): String = UUID.randomUUID().toString()

@Serializable
data class CreateRequest(val transaction: String = transactionId()) {
    @Suppress("unused")
    val janus: String = "create"
}

@Serializable
data class CreateResponse(val janus: String, val transaction: String, val data: CreateResponseData)

@Serializable
data class CreateResponseData(val id: Long)

@Serializable
data class SessionEvent(val janus: String, val sender: Long, val transaction: String, val plugindata: SessionEventPlugin)

@Serializable
data class SessionEventPlugin(val plugin: String, val data: SessionEventPluginData)

@Serializable
data class SessionEventPluginData(val id: Long)

@Serializable
data class AttachRequest(val plugin: String, val transaction: String = transactionId()) {
    @Suppress("unused")
    val janus: String = "attach"
}

@Serializable
data class AttachResponse(val janus: String, val transaction: String, val session_id: Long, val data: AttachResponseData)

@Serializable
data class AttachResponseData(val id: Long)

@Serializable
data class MessageRequest(val body: Map<String, String>, val transaction: String = transactionId()) {
    @Suppress("unused")
    val janus: String = "message"
}

@Serializable
data class MessageResponse(val janus: String, val transaction: String, val session_id: Long)

class JanusViewModel(private val serverRoot: Uri) {

//    private val serverRoot: Uri = Uri.parse("https://do-nyc3-edge1.kjfk.live.glimesh.tv/janus")

    suspend fun createSession(): Long {
        val request = CreateRequest()
        val response: CreateResponse = post(serverRoot, request)
        assert(response.janus == "success")
        assert(response.transaction == request.transaction)
        return response.data.id
    }

    suspend fun longPollSession(sessionId: Long): Array<SessionEvent> {
        val sessionUri = serverRoot.buildUpon().appendPath("$sessionId").build()
        val uri = sessionUri.buildUpon().appendQueryParameter("maxev", "10").appendQueryParameter("rid", "${System.currentTimeMillis()}").build()
        return get(uri)
    }

    suspend fun attachPlugin(sessionId: Long, plugin: String): Long {
        val sessionUri = serverRoot.buildUpon().appendPath("$sessionId").build()
        val request = AttachRequest(plugin)
        val response: AttachResponse = post(sessionUri, request)
        assert(response.janus == "success")
        assert(response.transaction == request.transaction)
        assert(response.session_id == sessionId)
        return response.data.id
    }

    suspend fun ftlWatchChannel(sessionId: Long, pluginId: Long, channelId: Long) {
        TODO()
    }

    suspend fun messagePlugin(sessionId: Long, pluginId: Long, body: Map<String, String>) {
        val sessionUri = serverRoot.buildUpon().appendPath("$sessionId").build()
        val pluginUri = sessionUri.buildUpon().appendPath("$pluginId").build()
        val request = MessageRequest(body)
        val response: MessageResponse = post(pluginUri, request)
        assert(response.janus == "ack")
        assert(response.transaction == request.transaction)
        assert(response.session_id == sessionId)
    }

    private suspend inline fun <reified R> get(uri: Uri): R {
        return withContext(Dispatchers.IO) {
            val urlConnection = URL("$uri").openConnection() as HttpURLConnection
            try {
                urlConnection.requestMethod = "GET"

                val result = Json.decodeFromStream<R>(urlConnection.inputStream)
                urlConnection.inputStream.close()

                return@withContext result
            } finally {
                urlConnection.disconnect()
                TODO()
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

                Json.encodeToStream(bodyJson, urlConnection.outputStream)
                urlConnection.outputStream.close()

                val result = Json.decodeFromStream<R>(urlConnection.inputStream)
                urlConnection.inputStream.close()

                return@withContext result
            } finally {
                urlConnection.disconnect()
                TODO()
            }
        }
    }
}