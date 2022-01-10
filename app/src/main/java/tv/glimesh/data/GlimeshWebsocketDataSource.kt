package tv.glimesh.data

import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Query
import kotlinx.serialization.json.*
import okhttp3.WebSocket
import tv.glimesh.apollo.ChannelByIdQuery
import tv.glimesh.apollo.HomepageQuery
import tv.glimesh.apollo.MyFollowingQuery
import tv.glimesh.data.model.ChannelId
import java.util.*

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
    private val authState: AuthStateDataSource
) {
    suspend fun channelByIdQuery(id: ChannelId): ChannelByIdQuery.Data {
        val connection = requireConnection()

        return connection.query(
            ChannelByIdQuery(
                com.apollographql.apollo3.api.Optional.presentIfNotNull(
                    id.toString()
                )
            )
        ).dataAssertNoErrors
    }

    suspend fun homepageQuery(): HomepageQuery.Data {
        val connection = requireConnection()

        return connection.query(HomepageQuery()).dataAssertNoErrors
    }

    suspend fun myFollowingQuery(): MyFollowingQuery.Data {
        val connection = requireAuthenticatedConnection()

        return connection.query(MyFollowingQuery()).dataAssertNoErrors
    }

    private fun requireConnection(): Connection {
        TODO()
    }

    private fun requireAuthenticatedConnection(): Connection {
        assert(authState.getCurrent().isAuthorized)
        TODO()
    }

    class Connection {

        val joinRef = "join_ref"
        var ref = 0

        val socket: WebSocket = TODO()

        /**
         * Join, called automatically when creating the connection
         */
        private suspend fun join() {
            // Send ["join_ref","ref","__absinthe__:control","phx_join",{}]
            // Success Reply ["join_ref","ref","__absinthe__:control","phx_reply",{"response":{},"status":"ok"}]
            // Error Reply ["join_ref","join_ref","__absinthe__:control","phx_close",{}]
            val ref = sendMessage("__absinthe__:control", "phx_join", buildJsonObject { })

            waitForResponse(ref)
        }

        suspend fun <D : Query.Data> query(query: Query<D>): ApolloResponse<D> {
            val ref = sendMessage("__absinthe__:control", "doc", buildJsonObject {
                put("query", query.document())
                put("variables", buildJsonObject {}) // TODO inject variables
            })

            waitForResponse(ref)

            TODO()
        }

        private suspend fun waitForResponse(ref: Reference) {
            TODO()
        }

        /**
         * Format: [join_ref, ref, topic, event, payload]
         * https://hexdocs.pm/phoenix/Phoenix.Socket.Message.html
         */
        private fun sendMessage(topic: String, event: String, payload: JsonObject): Reference {
            val ref = UUID.randomUUID()
            assert(socket.send(buildJsonArray {
                add(joinRef)
                add(ref.toString())
                add(topic)
                add(event)
                add(payload)
            }.toString()))
            return Reference(ref)
        }

        /**
         * Unique reference string a message (sent back with any responses to the message)
         */
        data class Reference(val ref: UUID)
    }
}