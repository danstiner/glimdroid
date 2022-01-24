package com.danielstiner.phoenix.absinthe

import com.apollographql.apollo3.api.*
import com.apollographql.apollo3.api.json.jsonReader
import com.danielstiner.phoenix.channels.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import okio.Buffer

/**
 * https://hexdocs.pm/absinthe_phoenix/Absinthe.Phoenix.Controller.html
 */
class Connection private constructor(
    private val socket: Socket,
    private val controlChannel: Channel,
    internal val scope: CoroutineScope,
    private val customScalarAdapters: CustomScalarAdapters
) {

    suspend fun <D : Query.Data> query(query: Query<D>): ApolloResponse<D> {
        when (val reply = pushDoc(query)) {
            is Result.Ok -> {
                return query.parseJsonResponse(reply.value.jsonObject, customScalarAdapters)
            }
            is Result.Err -> {
                TODO("Failed")
            }
        }
    }

    // Send ["join_ref", "ref", "__absinthe__:control", "doc", {"query": "subscription($channelId: ID) {chatMessage(channelId: $channelId) { id message}}","variables":{"channelId": "10552"}}]
    // Success Reply ["join_ref", "ref", "__absinthe__:control", "phx_reply", {"response":{"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"},"status":"ok"}]
    // Subscription Message [null,null,"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A","subscription:data",{"result":{"data":{"chatMessage":{"id":"3531994","message":"test"}}},"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"}]
    suspend fun <D : Mutation.Data> mutation(mutation: Mutation<D>): ApolloResponse<D> {
        val result = pushDoc(mutation)

        if (result is Result.Ok) {
            return mutation.parseJsonResponse(result.value.jsonObject, customScalarAdapters)
        }
        TODO("Failed")
    }

    // Send ["join_ref", "ref", "__absinthe__:control", "doc", {"query": "subscription($channelId: ID) {chatMessage(channelId: $channelId) { id message}}","variables":{"channelId": "10552"}}]
    // Success Reply ["join_ref", "ref", "__absinthe__:control", "phx_reply", {"response":{"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"},"status":"ok"}]
    // Subscription Message [null,null,"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A","subscription:data",{"result":{"data":{"chatMessage":{"id":"3531994","message":"test"}}},"subscriptionId":"__absinthe__:doc:-576460752257112799:07BA4A1ED159234418A35EB4A11517B5E26F748D8BE9AD57110863199DC35D5A"}]
    suspend fun <D : com.apollographql.apollo3.api.Subscription.Data> subscription(subscription: com.apollographql.apollo3.api.Subscription<D>): Subscription<ApolloResponse<D>> =
        when (val result = pushDoc(subscription)) {
            is Result.Ok -> {
                val id =
                    SubscriptionId(result.value.jsonObject["subscriptionId"]!!.jsonPrimitive.contentOrNull!!)
                val topic = id.asTopic()
                val data = socket.messages
                    .filter { message -> message.joinRef == null && message.ref == null && message.event == SUBSCRIPTION_DATA && message.topic == topic }
                    // TODO check subscription id in payload
                    .map { message ->
                        subscription.parseJsonResponse(
                            message.payload["result"]!!.jsonObject,
                            customScalarAdapters
                        )
                    }
                Subscription(id, data, this)
            }

            else -> TODO("Failed")
        }

    internal suspend fun unsubscribe(subscriptionId: SubscriptionId) {
        controlChannel.push(UNSUBSCRIBE, buildJsonObject {
            put("subscriptionId", subscriptionId.id)
        })

        // TODO()
    }

    private suspend fun <D : Operation.Data> pushDoc(operation: Operation<D>) =
        controlChannel.push(DOC, buildJsonObject {
            put("query", operation.document())
            put("variables", toJson(operation.variables(customScalarAdapters)))
        })

    private fun toJson(variables: Executable.Variables): JsonElement {

        fun toJson(value: Any?): JsonElement =
            when (value) {
                is String -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Double -> JsonPrimitive(value)
                null -> JsonNull
                is Map<*, *> -> buildJsonObject {
                    value.forEach { (key, value) ->
                        put(key as String, toJson(value))
                    }
                }
                is List<Any?> -> buildJsonArray {
                    value.forEach { value ->
                        add(toJson(value))
                    }
                }
                else -> TODO("Unsupported variable value: ${value.javaClass}")
            }

        return toJson(variables.valueMap)
    }

    companion object {
        private val DOC = Event("doc")
        private val UNSUBSCRIBE = Event("unsubscribe")
        private val CONTROL = Topic("__absinthe__:control")
        private val SUBSCRIPTION_DATA = Event("subscription:data")

        suspend fun create(
            socket: Socket,
            scope: CoroutineScope,
            customScalarAdapters: CustomScalarAdapters = CustomScalarAdapters.Empty
        ): Connection {
            val controlChannel = socket.channel(CONTROL)
            when (controlChannel.join()) {
                is Result.Ok -> {
                    return Connection(socket, controlChannel, scope, customScalarAdapters)
                }
                else -> TODO("Failed to join")
            }
        }
    }
}

private fun <D : Operation.Data> Operation<D>.parseJsonResponse(
    result: JsonObject,
    customScalarAdapters: CustomScalarAdapters
): ApolloResponse<D> =
    parseJsonResponse(Buffer().writeUtf8(result.toString()).jsonReader(), customScalarAdapters)
