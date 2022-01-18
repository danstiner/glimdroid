package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Subcategory(val name: String) {
    override fun toString(): String = "Category($name)"
}