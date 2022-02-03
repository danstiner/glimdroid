package com.danielstiner.glimdroid.data

import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.glimdroid.data.model.Follow
import com.danielstiner.glimdroid.data.model.User
import com.danielstiner.glimdroid.data.model.UserId

class UserRepository(
    private val glimesh: GlimeshSocketDataSource,
) {
    suspend fun me(): User {
        return glimesh.myselfQuery()
            .let { user ->
                val userId = UserId(user.id.toLong())
                User(
                    id = userId,
                    username = user.username,
                    displayName = user.displayname,
                    avatarUrl = user.avatarUrl,
                    following = user.following!!.edges!!.map { edge ->
                        val node = edge!!.node!!
                        Follow(
                            id = node.id.toLong(),
                            user = userId,
                            channel = ChannelId(node.streamer.channel!!.id!!.toLong()),
                            liveNotifications = node.hasLiveNotifications
                        )
                    }
                )
            }
    }
}