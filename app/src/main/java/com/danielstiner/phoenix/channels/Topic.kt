package com.danielstiner.phoenix.channels

data class Topic(val name: String) {
    override fun toString(): String {
        return name
    }
}