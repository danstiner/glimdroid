package tv.glimesh.phoenix.absinthe

import com.apollographql.apollo3.api.*
import com.apollographql.apollo3.api.json.jsonReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import okio.Buffer
import tv.glimesh.phoenix.channels.Event
import tv.glimesh.phoenix.channels.Reply
import tv.glimesh.phoenix.channels.Socket
import tv.glimesh.phoenix.channels.Topic

/**
 * https://hexdocs.pm/absinthe_phoenix/Absinthe.Phoenix.Controller.html
 */
class Connection private constructor(
    private val socket: Socket,
    private val controlChannel: tv.glimesh.phoenix.channels.Channel,
    internal val scope: CoroutineScope
) {

    suspend fun <D : Query.Data> query(query: Query<D>): ApolloResponse<D> {
        when (val reply = pushDoc(query)) {
            is Reply.Ok -> {
                return query.parseJsonResponse(reply.json.jsonReader())
            }
        }
        TODO("Failed")
    }

    // Send ["join_ref", "ref", "__absinthe__:control", "doc", {"query": "subscription($channelId: ID) {chatMessage(channelId: $channelId) { id message}}","variables":{"channelId": "10552"}}]
    // Success Reply ["join_ref", "ref", "__absinthe__:control", "phx_reply", {"response":{"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"},"status":"ok"}]
    // Subscription Message [null,null,"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A","subscription:data",{"result":{"data":{"chatMessage":{"id":"3531994","message":"test"}}},"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"}]
    suspend fun <D : Mutation.Data> mutation(mutation: Mutation<D>): ApolloResponse<D> {
        val reply = pushDoc(mutation)

        if (reply is Reply.Ok) {
            return mutation.parseJsonResponse(reply.json.jsonReader())
        }
        TODO("Failed")
    }

    // Send ["join_ref", "ref", "__absinthe__:control", "doc", {"query": "subscription($channelId: ID) {chatMessage(channelId: $channelId) { id message}}","variables":{"channelId": "10552"}}]
    // Success Reply ["join_ref", "ref", "__absinthe__:control", "phx_reply", {"response":{"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"},"status":"ok"}]
    // Subscription Message [null,null,"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A","subscription:data",{"result":{"data":{"chatMessage":{"id":"3531994","message":"test"}}},"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"}]
    suspend fun <D : com.apollographql.apollo3.api.Subscription.Data> subscription(subscription: com.apollographql.apollo3.api.Subscription<D>): Subscription<ApolloResponse<D>> =
        when (val reply = pushDoc(subscription)) {
            is Reply.Ok -> {
                val id =
                    SubscriptionId(reply.json.jsonObject["subscriptionId"]!!.jsonPrimitive!!.contentOrNull!!)
                val topic = id.asTopic()
                val data = socket.messages
                    .filter { message -> message.joinRef == null && message.ref == null && message.event == SUBSCRIPTION_DATA && message.topic == topic }
                    // TODO check subscription id in payload
                    .map { message -> subscription.parseJsonResponse(message.payload["result"]!!.jsonObject) }
                Subscription(id, data, this)
            }

            else -> TODO("Failed")
        }

    internal suspend fun unsubscribe(subscriptionId: SubscriptionId) {
        val reply = controlChannel.push(UNSUBSCRIBE, buildJsonObject {
            put("subscriptionId", subscriptionId.id)
        })

        // TODO()
    }

    private suspend fun <D : Operation.Data> pushDoc(operation: Operation<D>): Reply {
        return controlChannel.push(DOC, buildJsonObject {
            put("query", operation.document())
            put("variables", buildVariables(operation.variables(SCALAR_ADAPTERS)))
        })
    }

    private fun buildVariables(variables: Executable.Variables): JsonObject =
        buildJsonObject {
            variables.valueMap.forEach { (key, value) ->
                when (value) {
                    is Int -> put(key, value)
                    is String -> put(key, value)
                    null -> TODO("Unsupported variable value: null")
                    else -> TODO("Unsupported variable value: ${value.javaClass}")
                }
            }

        }

    companion object {
        private val DOC = Event("doc")
        private val UNSUBSCRIBE = Event("unsubscribe")
        private val CONTROL = Topic("__absinthe__:control")
        private val SUBSCRIPTION_DATA = Event("subscription:data")
        private val SCALAR_ADAPTERS = CustomScalarAdapters.Empty

        suspend fun create(socket: Socket, scope: CoroutineScope): Connection {
            val controlChannel: tv.glimesh.phoenix.channels.Channel = socket.channel(CONTROL)
            when (val reply = controlChannel.join()) {
                is Reply.Ok -> {

                }
                else -> TODO("Failed to join")
            }
            return Connection(socket, controlChannel, scope)
        }

    }
}

private fun <D : Operation.Data> Operation<D>.parseJsonResponse(result: JsonObject): ApolloResponse<D> =
    parseJsonResponse(Buffer().writeUtf8(result.toString()).jsonReader())
