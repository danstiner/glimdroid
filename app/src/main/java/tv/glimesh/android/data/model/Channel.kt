package tv.glimesh.android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: ChannelId,
    val title: String,
    val matureContent: Boolean,
    val language: String?,
    val category: Category,
    val tags: List<Tag>,
    val streamer: Streamer,
    val stream: Stream?,
)
