package tv.glimesh.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(val name: String) {
    override fun toString(): String = "Category($name)"
}