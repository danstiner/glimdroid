package tv.glimesh.android.data.model

import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Stream(
    val id: StreamId,
    val viewerCount: Int?,
    val thumbnailUrl: String?,
    @Serializable(with = InstantComponentSerializer::class)
    val startedAt: Instant,
)
