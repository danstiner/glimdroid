package com.danielstiner.glimdroid.data

import com.danielstiner.glimdroid.data.model.User

class UserRepository(
    val glimesh: GlimeshSocketDataSource,
) {
    suspend fun me(): User {
        return glimesh.myselfQuery()
            .let { node ->
                User(
                    id = node.id,
                    username = node.username,
                    displayName = node.displayname,
                    avatarUrl = node.avatarUrl
                )
            }
    }
}