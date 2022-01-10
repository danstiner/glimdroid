package tv.glimesh.data

import tv.glimesh.apollo.ChannelByIdQuery
import tv.glimesh.data.model.ChannelId

class ChannelRepository(
    val glimesh: GlimeshWebsocketDataSource
) {
    suspend fun get(id: ChannelId): ChannelByIdQuery.Data {
        // TODO convert data
        return glimesh.channelByIdQuery(id)
    }
}