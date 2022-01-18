package com.danielstiner.phoenix.absinthe

import com.danielstiner.phoenix.channels.Topic

data class SubscriptionId(val id: String) {
    fun asTopic(): Topic {
        return Topic(id)
    }
}