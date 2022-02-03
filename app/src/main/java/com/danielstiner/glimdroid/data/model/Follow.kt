package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Follow(
    val id: Long,
    val user: UserId,
    val channel: ChannelId,
    val liveNotifications: Boolean
)
