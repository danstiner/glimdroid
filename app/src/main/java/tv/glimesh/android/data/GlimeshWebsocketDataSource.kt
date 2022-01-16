package tv.glimesh.android.data

import android.util.Log
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.CustomScalarType
import com.apollographql.apollo3.api.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.glimesh.android.data.model.*
import tv.glimesh.apollo.*
import tv.glimesh.apollo.type.ChatMessageInput
import tv.glimesh.phoenix.channels.Socket
import java.lang.ref.WeakReference
import java.net.URI
import java.net.URL
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

data class ChatMessage(
    val id: String,
    val message: String,
    val displayname: String,
    val username: String,
    val avatarUrl: String?,
    val timestamp: Instant,
)

data class EdgeRoute(val id: String, val url: URL)

/**
 * Query and subscribe to data from glimesh.tv
 *
 * Opens a websocket connection to the Phoenix web frontend and sends regular heartbeats to stay
 * connected. If an authentication access token is available it will be used, otherwise it will
 * fall back to public access.
 * https://glimesh.github.io/api-docs/docs/api/live-updates/channels/
 * http://graemehill.ca/websocket-clients-and-phoenix-channels/
 */
class GlimeshWebsocketDataSource private constructor(
    private val auth: AuthStateDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: tv.glimesh.phoenix.absinthe.Connection? = null
    private var authenticatedConnection: tv.glimesh.phoenix.absinthe.Connection? = null
    private val mutex = Mutex()

    suspend fun channelQuery(channel: ChannelId): ChannelByIdQuery.Data {
        return connection().query(
            ChannelByIdQuery(
                Optional.Present(
                    channel.id.toString()
                )
            )
        ).dataAssertNoErrors
    }

    suspend fun homepageQuery(): HomepageQuery.Data {
        return authenticatedConnection().query(
            HomepageQuery()
        ).dataAssertNoErrors
    }

    suspend fun myFollowingLiveQuery(): MyFollowingLiveQuery.Data {
        return authenticatedConnection().query(
            MyFollowingLiveQuery()
        ).dataAssertNoErrors
    }

    suspend fun liveChannelsQuery(): LiveChannelsQuery.Data {
        return connection().query(
            LiveChannelsQuery()
        ).dataAssertNoErrors
    }

    suspend fun watchChannel(channel: ChannelId, countryCode: String): EdgeRoute {
        val data = connection().mutation(
            WatchChannelMutation(channel.id.toString(), countryCode)
        ).dataAssertNoErrors.watchChannel!!

        return EdgeRoute(data.id!!, URL(data.url!!))
    }

    suspend fun sendMessage(channel: ChannelId, text: CharSequence) {
        authenticatedConnection().mutation(
            SendMessageMutation(
                channel.id.toString(), ChatMessageInput(
                    Optional.Present(
                        text.toString()
                    )
                )
            )
        ).dataAssertNoErrors.createChatMessage
    }

    suspend fun chatMessages(channel: ChannelId): tv.glimesh.phoenix.absinthe.Subscription<ChatMessage> {
        return connection().subscription(
            MessagesSubscription(
                channel.id.toString()
            )
        ).map { response ->
            Log.d("ChatMessage", response.data?.toString() ?: "null")
            val message = response.dataAssertNoErrors.chatMessage!!
            ChatMessage(
                id = message.id,
                message = message.message!!,
                displayname = message.user.displayname,
                username = message.user.username,
                avatarUrl = message.user.avatarUrl,
                timestamp = message.insertedAt,
            )
        }
    }

    suspend fun channelUpdates(channel: ChannelId): tv.glimesh.phoenix.absinthe.Subscription<Channel> {
        return connection().subscription(
            ChannelUpdatesSubscription(
                channel.id.toString()
            )
        ).map { response ->
            val node = response.dataAssertNoErrors.channel!!
            Channel(
                id = ChannelId(node.id!!.toLong()),
                title = node.title!!,
                matureContent = node.matureContent ?: false,
                language = node.language,
                category = Category(node.category!!.name!!),
                tags = node.tags!!.mapNotNull { tag -> Tag(tag!!.name!!) },
                streamer = Streamer(
                    username = node.streamer.username,
                    displayName = node.streamer.displayname,
                    avatarUrl = node.streamer.avatarUrl,
                ),
                stream = node.stream?.let { stream ->
                    Stream(
                        id = StreamId(stream.id!!.toLong()),
                        viewerCount = stream.countViewers ?: 0,
                        thumbnailUrl = null, // Channel view does not care about thumbnail updates
                        startedAt = stream.startedAt
                    )
                }
            )
        }
    }

    suspend fun streamUpdates(channel: ChannelId): tv.glimesh.phoenix.absinthe.Subscription<Stream?> {
        return connection().subscription(
            StreamUpdatesSubscription(
                channel.id.toString()
            )
        ).map { response ->
            response.dataAssertNoErrors.channel!!.stream?.let { stream ->
                Stream(
                    id = StreamId(stream.id!!.toLong()),
                    viewerCount = stream.countViewers ?: 0,
                    thumbnailUrl = null, // Channel view does not care about thumbnail updates
                    startedAt = stream.startedAt
                )
            }
        }
    }

    @Synchronized
    private suspend fun connection(): tv.glimesh.phoenix.absinthe.Connection {
        if (auth.getCurrent().isAuthorized) {
            // TODO maybe close unauthenticated connection
            return authenticatedConnection()
        }

        mutex.withLock {
            var con = connection

            if (con == null) {
                val socket = Socket.open(
                    URI.create("wss://glimesh.tv/api/socket/websocket?vsn=2.0.0&client_id=$CLIENT_ID"),
                    scope = scope
                )
                con = tv.glimesh.phoenix.absinthe.Connection.create(
                    socket,
                    scope,
                    CUSTOM_SCALAR_ADAPTERS
                )
                connection = con
            }

            return con
        }
    }

    @Synchronized
    private suspend fun authenticatedConnection(): tv.glimesh.phoenix.absinthe.Connection {
        mutex.withLock {
            var con = authenticatedConnection

            if (con == null) {
                val socket = Socket.open(
                    URI.create("wss://glimesh.tv/api/socket/websocket?vsn=2.0.0&token=${auth.freshAccessToken()}"),
                    scope = scope
                )
                con = tv.glimesh.phoenix.absinthe.Connection.create(
                    socket,
                    scope,
                    CUSTOM_SCALAR_ADAPTERS
                )
                authenticatedConnection = con
            }

            return con
        }
    }

    companion object {
        const val TAG = "GlimeshWebsocket"

        val CUSTOM_SCALAR_ADAPTERS: CustomScalarAdapters = CustomScalarAdapters.Builder().add(
            CustomScalarType("NaiveDateTime", Instant::javaClass.name),
            GlimeshNaiveTimeToInstantAdapter
        ).build()

        private val INSTANCE_REF = AtomicReference(WeakReference<GlimeshWebsocketDataSource>(null))

        @Synchronized
        fun getInstance(auth: AuthStateDataSource): GlimeshWebsocketDataSource {
            var instance: GlimeshWebsocketDataSource? = INSTANCE_REF.get().get()
            if (instance == null) {
                Log.d(TAG, "Constructing GlimeshWebsocketDataSource")
                instance = GlimeshWebsocketDataSource(auth)
                INSTANCE_REF.set(WeakReference(instance))
            }
            return instance
        }
    }
}
