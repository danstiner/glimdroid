package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserId(val id: Long) {
    override fun toString(): String = "UserId($id)"
}
