package tv.glimesh.phoenix.absinthe

import tv.glimesh.phoenix.channels.Topic

data class SubscriptionId(val id: String) {
    fun asTopic(): Topic {
        return Topic(id)
    }
}