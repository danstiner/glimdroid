package tv.glimesh.phoenix.channels

import kotlinx.serialization.json.JsonObject

data class Message(
    val joinRef: Ref?,
    val ref: Ref?,
    val topic: Topic,
    val event: Event,
    val payload: JsonObject
)
