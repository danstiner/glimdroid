package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Tag(val name: String) {
    override fun toString(): String = "Tag($name)"
}