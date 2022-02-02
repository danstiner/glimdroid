package com.danielstiner.phoenix.channels

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URI
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * https://hexdocs.pm/phoenix/Phoenix.Socket.html
 */
class Socket private constructor(private val uri: URI, private val scope: CoroutineScope) {
    enum class State(val state: String) {
        CREATED("created"),
        CONNECTING("connecting"),
        OPEN("open"),
        CLOSING("closing"),
        CLOSED("closed"),
        UNKNOWN("unknown"),
    }

    private val refFactory = RefFactory()
    private lateinit var webSocket: WebSocket

    private lateinit var _state: State
    val state get() = _state

    private val _messages = MutableSharedFlow<Message>()
    val messages: SharedFlow<Message> = _messages

    fun channel(
        topic: Topic,
        params: JsonObject = buildJsonObject { },
        joinRef: Ref? = refFactory.newRef()
    ): Channel {
        return Channel(topic, params, this, refFactory, messages, scope, joinRef)
    }

    private suspend fun connect(client: OkHttpClient) {
        this._state = State.CONNECTING
        val request = okhttp3.Request.Builder().url(uri.toString()).build()

        suspendCoroutine<Unit> { continuation ->
            webSocket = client.newWebSocket(request, Listener(continuation))
        }
    }

    private suspend fun keepAliveLoop() {
        val phoenixChannel = channel(Topic("phoenix"), joinRef = null)
        while (state == State.OPEN) {
            delay(30_000)
            if (phoenixChannel.push(Event("heartbeat")) is Result.Err) {
                TODO("Failed")
            }
        }
        TODO("Socket closed")
    }

    internal fun send(text: String) {
        Log.v("Socket", "send: $text")
        webSocket.send(text)
    }

    private inner class Listener(private val continuation: Continuation<Unit>) :
        WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@Socket.webSocket = webSocket
            this@Socket._state = State.OPEN
            continuation.resume(Unit)
            scope.launch { keepAliveLoop() }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            this@Socket._state = State.UNKNOWN
            Log.e(TAG, "onFailure: ${t.message}", t)
            TODO("PhoenixSocket onFailure not handled")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@Socket._state = State.CLOSED
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            this@Socket._state = State.CLOSING
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val array: JsonArray = Json.decodeFromString(text)
            Log.v(TAG, "onMessage: $text")
            scope.launch {
                _messages.emit(
                    Message(
                        joinRef = array[0].jsonPrimitive.contentOrNull?.let { Ref(it) },
                        ref = array[1].jsonPrimitive.contentOrNull?.let { Ref(it) },
                        topic = Topic(array[2].jsonPrimitive.contentOrNull!!),
                        event = Event(array[3].jsonPrimitive.contentOrNull!!),
                        payload = array[4].jsonObject,
                    )
                )
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            TODO("PhoenixSocket onMessage not handled for binary type messages")
        }
    }

    companion object {
        private const val TAG = "PhoenixSocket"

        suspend fun open(
            url: URI,
            client: OkHttpClient = OkHttpClient(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ): Socket {
            val socket = Socket(url, scope)
            socket.connect(client)
            return socket
        }
    }
}
