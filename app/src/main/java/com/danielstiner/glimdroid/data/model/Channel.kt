package com.danielstiner.glimdroid.data.model

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Channel(
    val id: ChannelId,
    val title: String,
    val matureContent: Boolean,
    val language: String?,
    val category: Category,
    val subcategory: Subcategory?,
    val tags: List<Tag>,
    val streamer: Streamer,
    val stream: Stream?,
) {
    fun displayLanguage() = language?.let {
        var locale = Locale(it)
        locale.getDisplayLanguage(locale)
    }
}
