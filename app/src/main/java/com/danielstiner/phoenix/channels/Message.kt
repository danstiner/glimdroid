package com.danielstiner.phoenix.channels

import kotlinx.serialization.json.JsonObject

data class Message(
    val joinRef: Ref?,
    val ref: Ref?,
    val topic: Topic,
    val event: Event,
    val payload: JsonObject
)
