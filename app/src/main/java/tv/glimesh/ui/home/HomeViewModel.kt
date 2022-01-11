package tv.glimesh.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.glimesh.data.GlimeshDataSource

class HomeViewModel(
    private val glimesh: GlimeshDataSource,
) : ViewModel() {

    private val _followingCount = MutableLiveData<Int>().apply {
        value = 0
    }
    val followingCount: LiveData<Int> = _followingCount

    private val _followingLiveChannels = MutableLiveData<List<Channel>>().apply {
        value = listOf()
    }
    val followingLiveChannels: LiveData<List<Channel>> = _followingLiveChannels

    private fun fetchFollowing() {
        viewModelScope.launch(Dispatchers.IO) {

            val data = glimesh.myFollowingQuery()

            withContext(Dispatchers.Main) {
                _followingCount.value = data.myself?.countFollowing
                _followingLiveChannels.value =
                    data
                        ?.myself
                        ?.followingLiveChannels
                        ?.edges
                        ?.mapNotNull { edge -> edge?.node }
                        ?.map { node ->
                            Channel(
                                id = node.id!!,
                                title = node?.title!!,
                                streamerDisplayName = node?.streamer?.displayname,
                                streamerAvatarUrl = node?.streamer?.avatarUrl,
                                streamId = node?.stream?.id,
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
