package tv.glimesh.data

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.json.BufferedSourceJsonReader
import com.apollographql.apollo3.api.variables
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okio.Buffer
import tv.glimesh.apollo.MessagesSubscription
import tv.glimesh.data.model.ChannelId
import tv.glimesh.ui.channel.ChatMessage
import java.io.Closeable
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext

const val CLIENT_ID = "34d2a4c6-e357-4132-881b-d64305853632"

/**
 * Query and subscribe to data from glimesh.tv
 *
 * Opens a websocket connection to the Phoenix web frontend and sends regular heartbeats to stay
 * connected. If an authentication access token is available it will be used, otherwise it will
 * fall back to public access.
 * https://glimesh.github.io/api-docs/docs/api/live-updates/channels/
 * https://hexdocs.pm/absinthe_phoenix/Absinthe.Phoenix.Controller.html
 * http://graemehill.ca/websocket-clients-and-phoenix-channels/
 */
class GlimeshWebsocketDataSource(
    private val authState: AuthStateDataSource,
) {
    private val scope: CloseableCoroutineScope =
        CloseableCoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connection: Connection? = null

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun chatMessages(id: ChannelId): Flow<ChatMessage> {
        val connection = requireAuthenticatedConnection()

        return connection.subscription(
            MessagesSubscription(
                id.id.toString()
            )
        ).map { response ->
            val message = response.dataAssertNoErrors.chatMessage!!
            ChatMessage(
                id = message.id,
                message = message.message!!,
                displayname = message.user.displayname,
                username = message.user.username,
                avatarUrl = message.user.avatarUrl,
                timestamp = Instant.parse(message.insertedAt as String + "Z"),
            )
        }
    }

    private suspend fun requireAuthenticatedConnection(): Connection {
        assert(authState.getCurrent().isAuthorized)
        if (connection == null) {

            // TODO handle authorization exceptions
            var (accessToken, idToken, ex) = authState.retrieveFreshTokens()
            connection = Connection.create(accessToken!!, scope)
        }
        return connection!!
    }

    internal class Connection(private val socket: DefaultClientWebSocketSession) {
        private val joinRef = "join_ref"
        private val _received = MutableSharedFlow<Message>() // private mutable shared flow
        private val received = _received.asSharedFlow() // publicly exposed as read-only shared flow

        private val controlTopic = "__absinthe__:control"

        /**
         * Join, called automatically when creating the connection
         */
        private suspend fun join() {
            // Send ["join_ref","ref","__absinthe__:control","phx_join",{}]
            // Success Reply ["join_ref","ref","__absinthe__:control","phx_reply",{"response":{},"status":"ok"}]
            // Error Reply ["join_ref","join_ref","__absinthe__:control","phx_close",{}]
            val response = rr(controlTopic, "phx_join", buildJsonObject { })
            assert(response.event == "phx_reply")
            assert(response.payload["status"]?.jsonPrimitive?.contentOrNull == "ok")
        }

        // Send ["join_ref", "ref", "__absinthe__:control", "doc", {"query": "subscription($channelId: ID) {chatMessage(channelId: $channelId) { id message}}","variables":{"channelId": "10552"}}]
        // Success Reply ["join_ref", "ref", "__absinthe__:control", "phx_reply", {"response":{"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"},"status":"ok"}]
        // Subscription Message [null,null,"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A","subscription:data",{"result":{"data":{"chatMessage":{"id":"3531994","message":"test"}}},"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"}]
        suspend fun <D : Subscription.Data> subscription(subscription: Subscription<D>): Flow<ApolloResponse<D>> {
            val variables = buildJsonObject {
                subscription.variables(CustomScalarAdapters.Empty).valueMap.forEach { (key, value) ->
                    when (value) {
                        is Int -> put(key, value)
                        is String -> put(key, value)
                        else -> TODO("Unsupported variable: " + value.toString())
                    }
                }
            }

            val response = rr(controlTopic, "doc", buildJsonObject {
                put("query", subscription.document())
                put("variables", variables) // TODO inject variables
            })

            assert(response.event == "phx_reply")
            assert(response.payload["status"]?.jsonPrimitive?.contentOrNull == "ok")
            val subscriptionId =
                response.payload["response"]!!.jsonObject["subscriptionId"]!!.jsonPrimitive!!.contentOrNull
            return received
                .filter { message -> message.event == "subscription:data" && message.topic == subscriptionId }
                .map { message ->
                    val buffer = Buffer().write(
                        message.payload.jsonObject["result"]!!.jsonObject["data"]!!.toString()
                            .toByteArray()
                    )
                    ApolloResponse.Builder(
                        subscription,
                        com.benasher44.uuid.Uuid.fromString(response.ref!!.ref),
                        subscription.adapter()
                            .fromJson(BufferedSourceJsonReader(buffer), CustomScalarAdapters.Empty)
                    ).build()
                }
        }

        private suspend fun subscriptionResponses(ref: Reference): Flow<Message> {
            val response = waitForResponse(ref)!!
            assert(response.event == "phx_reply")
            assert(response.payload["status"]?.jsonPrimitive?.contentOrNull == "ok")
            val subscriptionId =
                response.payload["response"]!!.jsonObject["response"]!!.jsonPrimitive!!.contentOrNull
            return received.filter { message -> message.topic == subscriptionId }
        }

        private suspend fun waitForResponse(ref: Reference): Message? {
            return received.filter { message ->
                Log.d(TAG, "waitForResponse: ${message.ref == ref} $message")
                message.ref == ref
            }.firstOrNull()
        }

        private suspend fun rr(
            topic: String,
            event: String,
            payload: JsonObject
        ): Message {
            val ref = Reference(UUID.randomUUID().toString())
            val content = buildJsonArray {
                add(joinRef)
                add(ref.ref)
                add(topic)
                add(event)
                add(payload)
            }.toString()
            Log.d(TAG, "rr: $content")
            return coroutineScope {
                val start = async(start = CoroutineStart.UNDISPATCHED) { waitForResponse(ref) }
                async { socket.send(content) }
                return@coroutineScope start.await()!!
            }
        }

        /**
         * Format: [join_ref, ref, topic, event, payload]
         * https://hexdocs.pm/phoenix/Phoenix.Socket.Message.html
         */
        private suspend fun sendMessage(
            topic: String,
            event: String,
            payload: JsonObject
        ): Reference {
            val ref = UUID.randomUUID().toString()
            val content = buildJsonArray {
                add(joinRef)
                add(ref)
                add(topic)
                add(event)
                add(payload)
            }.toString()
            Log.d(TAG, "sendMessage: $content")
            socket.send(content)
            return Reference(ref)
        }

        private suspend fun receive() {
            while (true) {
                if (socket.incoming.isClosedForReceive) {
                    TODO("Why closed")
                }
                val frame = socket.incoming.receive() as Frame.Text
                val text = frame.readText()
                Log.d(TAG, text)
                val array = Json.decodeFromString<JsonArray>(text)
                Log.d(TAG, "Listeners: " + _received.subscriptionCount.value)
                _received.emit(
                    Message(
                        joinRef = array[0].jsonPrimitive.contentOrNull,
                        ref = array[1].jsonPrimitive.contentOrNull?.let { Reference(it) },
                        topic = array[2].jsonPrimitive.contentOrNull!!,
                        event = array[3].jsonPrimitive.contentOrNull!!,
                        payload = array[4].jsonObject,
                    )
                )
            }
        }

        private suspend fun keepalive() {
            while (true) {
                delay(30_000)
                sendMessage("phoenix", "heartbeat", buildJsonObject { })
            }
        }

        /**
         * Unique reference string a message (sent back with any responses to the message)
         */
        data class Reference(val ref: String)

        data class Message(
            val joinRef: String?,
            val ref: Reference?,
            val topic: String,
            val event: String,
            val payload: JsonObject
        )

        companion object {
            private val TAG = "GlimeshWebsocket"

            val client = HttpClient(OkHttp) {
//                install(Auth) {
//                    bearer {
//                        refreshTokens { unauthorizedResponse: HttpResponse ->
//                            refreshTokenInfo = tokenClient.submitForm(
//                                url = "https://accounts.google.com/o/oauth2/token",
//                                formParameters = Parameters.build {
//                                    append("grant_type", "refresh_token")
//                                    append("client_id", clientId)
//                                    append("refresh_token", tokenInfo.refreshToken!!)
//                                }
//                            )
//                            BearerTokens(
//                                accessToken = refreshTokenInfo.accessToken,
//                                refreshToken = tokenInfo.refreshToken!!
//                            )
//                        }
//                    }
//                }
                install(WebSockets)
            }

            suspend fun create(accessToken: String, scope: CoroutineScope): Connection {
                val result = Channel<Connection>()
                scope.launch {
                    client.wss("wss://glimesh.tv/api/socket/websocket?vsn=2.0.0&token=$accessToken") {
                        val connection = Connection(this)
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { connection!!.receive() }
                        connection!!.join()
                        result.send(connection)
                        connection!!.keepalive()
                    }
                }
                return result.receive()
            }

            suspend fun webSocket(
                accessToken: String, scope: CoroutineScope,
                request: HttpRequestBuilder.() -> Unit,
                block: suspend DefaultClientWebSocketSession.() -> Unit
            ) {
                val session = client.request<HttpStatement> {
                    url {
                        protocol = URLProtocol.WS
                        port = protocol.defaultPort
                    }

                    url.protocol = URLProtocol.WS
                    url.port = port

                    url.takeFrom("wss://glimesh.tv/api/socket/websocket?vsn=2.0.0&token=$accessToken")
                    request()
                }

                session.receive<DefaultClientWebSocketSession, Unit> {
                    try {
                        block(it)
                    } finally {
                        it.close()
                    }
                }
            }
        }
    }

    internal class CloseableCoroutineScope(context: CoroutineContext) : Closeable, CoroutineScope {
        override val coroutineContext: CoroutineContext = context

        override fun close() {
            coroutineContext.cancel()
        }
    }
}