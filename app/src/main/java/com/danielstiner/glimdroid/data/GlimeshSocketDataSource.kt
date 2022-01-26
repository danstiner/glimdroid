package com.danielstiner.glimdroid.data

import android.net.Uri
import android.util.Log
import com.apollographql.apollo3.api.Adapter
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.CustomScalarType
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.api.json.JsonReader
import com.apollographql.apollo3.api.json.JsonWriter
import com.danielstiner.glimdroid.BuildConfig
import com.danielstiner.glimdroid.apollo.*
import com.danielstiner.glimdroid.apollo.type.ChatMessageInput
import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.phoenix.absinthe.Connection
import com.danielstiner.phoenix.channels.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.lang.ref.WeakReference
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

val WEBSOCKET_V2_URI: Uri =
    Uri.parse(BuildConfig.GLIMESH_WEBSOCKET_URL).buildUpon().appendQueryParameter("vsn", "2.0.0")
        .build()

/**
 * Query and subscribe to data from glimesh.tv
 *
 * Opens a websocket connection to the Phoenix web frontend and sends regular heartbeats to stay
 * connected. If an authentication access token is available it will be used, otherwise it will
 * fall back to public access.
 * https://glimesh.github.io/api-docs/docs/api/live-updates/channels/
 * http://graemehill.ca/websocket-clients-and-phoenix-channels/
 */
class GlimeshSocketDataSource private constructor(
    private val auth: AuthStateDataSource,
    private val client: OkHttpClient = defaultClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection = WeakReference<Connection>(null)
    private var authenticatedConnection: Connection? = null
    private val mutex = Mutex()

    suspend fun channelQuery(channel: ChannelId) =
        connection().query(
            ChannelByIdQuery(
                Optional.Present(
                    channel.id.toString()
                )
            )
        ).dataAssertNoErrors

    suspend fun myHomepageQuery() =
        authenticatedConnection().query(MyHomepageQuery()).dataAssertNoErrors

    suspend fun homepageQuery() =
        connection().query(HomepageQuery()).dataAssertNoErrors

    suspend fun myFollowingLiveQuery() =
        authenticatedConnection().query(MyFollowingLiveQuery()).dataAssertNoErrors

    suspend fun liveChannelsQuery() =
        connection().query(LiveChannelsQuery()).dataAssertNoErrors

    suspend fun recentMessagesQuery(channel: ChannelId) =
        connection().query(RecentMessagesQuery(channel.id.toString())).dataAssertNoErrors

    suspend fun myselfQuery() = connection().query(MyselfQuery()).dataAssertNoErrors.myself!!

    suspend fun watchChannelMutation(channel: ChannelId, countryCode: String) =
        connection().mutation(
            WatchChannelMutation(channel.id.toString(), countryCode)
        ).dataAssertNoErrors.watchChannel!!

    suspend fun sendMessageMutation(channel: ChannelId, text: CharSequence) =
        authenticatedConnection().mutation(
            SendMessageMutation(
                channel.id.toString(),
                ChatMessageInput(
                    Optional.Present(
                        text.toString()
                    )
                )
            )
        ).dataAssertNoErrors.createChatMessage


    suspend fun messagesSubscription(channel: ChannelId) =
        connection().subscription(
            MessagesSubscription(
                channel.id.toString()
            )
        ).map { response -> response.dataAssertNoErrors }

    private suspend fun connection(): Connection {
        if (auth.getCurrent().isAuthorized) {
            return authenticatedConnection()
        }

        mutex.withLock {
            var con = connection.get()

            if (con == null) {
                val socket = Socket.open(
                    WEBSOCKET_V2_URI.buildUpon()
                        .appendQueryParameter("client_id", CLIENT_ID)
                        .build().toURI(),
                    client = client,
                    scope = scope,
                )
                con = Connection.create(
                    socket,
                    scope,
                    CUSTOM_SCALAR_ADAPTERS
                )
                connection = WeakReference(con)
            }

            return con
        }
    }

    private suspend fun authenticatedConnection(): Connection {
        mutex.withLock {
            var con = authenticatedConnection

            if (con == null) {
                val socket = Socket.open(
                    WEBSOCKET_V2_URI.buildUpon()
                        .appendQueryParameter("token", auth.freshAccessToken())
                        .build().toURI(),
                    client = client,
                    scope = scope
                )
                con = Connection.create(
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

        private val INSTANCE_REF = AtomicReference(WeakReference<GlimeshSocketDataSource>(null))

        @Synchronized
        fun getInstance(auth: AuthStateDataSource): GlimeshSocketDataSource {
            var instance: GlimeshSocketDataSource? = INSTANCE_REF.get().get()
            if (instance == null) {
                Log.v(TAG, "Constructing GlimeshWebsocketDataSource")
                instance = GlimeshSocketDataSource(auth)
                INSTANCE_REF.set(WeakReference(instance))
            }
            return instance
        }
    }
}

private fun defaultClient() =
    OkHttpClient.Builder().pingInterval(30.seconds.toJavaDuration()).build()


/**
 * The Glimesh GraphQL endpoint says it's NaiveDateTime is ISO8601, but it's not actually, hence the
 * custom adapter
 *
 * The timezone identifier is missing, e.g. '2011-12-03T10:15:30'
 */
internal object GlimeshNaiveTimeToInstantAdapter : Adapter<Instant> {
    override fun fromJson(reader: JsonReader, customScalarAdapters: CustomScalarAdapters): Instant {
        return Instant.parse(reader.nextString()!! + "Z")
    }

    override fun toJson(
        writer: JsonWriter,
        customScalarAdapters: CustomScalarAdapters,
        value: Instant
    ) {
        TODO()
    }
}


private fun Uri.toURI(): URI = URI.create(this.toString())
