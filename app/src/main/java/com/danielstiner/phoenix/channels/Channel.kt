package com.danielstiner.phoenix.channels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/**
 * https://hexdocs.pm/phoenix/Phoenix.Channel.html
 * https://hexdocs.pm/phoenix/channels.html
 */
class Channel(
    private val topic: Topic,
    private val joinParams: JsonObject,
    private val socket: Socket,
    private val refFactory: RefFactory,
    messages: SharedFlow<Message>,
    scope: CoroutineScope,
    private val joinRef: Ref?,
) {
    private val channelMessages = MutableSharedFlow<Message>()
    private lateinit var state: State

    init {
        scope.launch {
            messages.filter { it.joinRef == joinRef }.collect {
                channelMessages.emit(it)
            }
        }
    }

    // Send ["join_ref","ref","__absinthe__:control","phx_join",{}]
    // Success Reply ["join_ref","ref","__absinthe__:control","phx_reply",{"response":{},"status":"ok"}]
    // Error Reply ["join_ref","join_ref","__absinthe__:control","phx_close",{}]
    suspend fun join(): Result<JsonObject> {
        state = State.JOINING

        val ref = sendJoin()
        val reply = firstReplyOrClose(ref)
        val status = reply.payload["status"]?.jsonPrimitive?.contentOrNull

        return when {
            reply.event == Event.CLOSE -> {
                Result.Err(TODO())
            }
            reply.event == Event.REPLY && status == "ok" -> {
                Result.Ok(reply.payload["response"]!!.jsonObject)
            }
            else -> TODO()
        }
    }

    private suspend fun firstReplyOrClose(ref: Ref) =
        channelMessages.first { (it.ref == ref && it.event == Event.REPLY) || (it.ref == joinRef && it.event == Event.CLOSE) }

    suspend fun push(
        event: Event,
        payload: JsonObject = buildJsonObject {},
        ref: Ref = refFactory.newRef()
    ): Result<JsonObject> {
        val ref = send(event, payload, ref)
        val reply = firstReplyOrClose(ref)
        val status = reply.payload["status"]?.jsonPrimitive?.contentOrNull

        return when {
            reply.event == Event.CLOSE -> {
                // Push failed
                Result.Err(TODO())
            }
            reply.event == Event.REPLY && status == "ok" -> {
                // Push successful
                Result.Ok(reply.payload["response"]!!.jsonObject)
            }
            else -> TODO("" + reply)
        }
    }

    suspend fun leave(): Result<JsonObject> {
        state = State.LEAVING

        val ref = sendLeave()

        val reply = firstReplyOrClose(ref)
        TODO()
    }

    private fun sendJoin(): Ref = send(Event.JOIN, joinParams)

    private fun sendLeave(): Ref = send(Event.LEAVE)


    /**
     * Format: [join_ref, ref, topic, event, payload]
     * https://hexdocs.pm/phoenix/Phoenix.Socket.Message.html
     */
    private fun send(
        event: Event,
        payload: JsonObject = buildJsonObject {},
        ref: Ref = refFactory.newRef()
    ): Ref {
        socket.send(Json.encodeToString(buildJsonArray {
            add(joinRef?.id)
            add(ref.id)
            add(topic.name)
            add(event.name)
            add(payload)
        }))
        return ref
    }

    enum class State {
        JOINING,
        LEAVING
    }
}