package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChannelId(val id: Long) {
    override fun toString(): String = "ChannelId($id)"
}
