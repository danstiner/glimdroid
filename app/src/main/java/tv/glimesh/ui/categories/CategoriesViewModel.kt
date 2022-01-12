package tv.glimesh.ui.categories

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.glimesh.apollo.type.ChannelStatus
import tv.glimesh.data.GlimeshDataSource
import tv.glimesh.ui.home.Channel
import kotlin.math.ln

@RequiresApi(Build.VERSION_CODES.O)
class CategoriesViewModel(
    private val glimesh: GlimeshDataSource
) : ViewModel() {

    private val _channels = MutableLiveData<List<Channel>>().apply {
        value = listOf()
    }
    val channels: LiveData<List<Channel>> = _channels

    private fun fetchFollowing() {
        viewModelScope.launch(Dispatchers.IO) {
            val data = glimesh.liveChannelsQuery()

            withContext(Dispatchers.Main) {
                _channels.value =
                    data
                        ?.channels
                        ?.edges
                        ?.mapNotNull { edge -> edge?.node }
                        ?.filter { node -> node?.status == ChannelStatus.LIVE }
                        ?.sortedBy { node -> node?.stream?.countViewers?.let { (ln(it.toDouble() + 1) + 1) * Math.random() } }
                        ?.reversed()
                        ?.map { node ->
                            Channel(
                                id = node.id!!,
                                title = node?.title!!,
                                streamerDisplayName = node?.streamer?.displayname,
                                streamerAvatarUrl = node?.streamer?.avatarUrl,
                                streamId = node?.stream?.id,
                                streamThumbnailUrl = node?.stream?.thumbnailUrl,
                            )
                        }
            }
        }
    }

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchFollowing()
        }
    }
}
