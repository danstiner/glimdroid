package tv.glimesh.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo3.ApolloClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import tv.glimesh.apollo.MyFollowingQuery
import tv.glimesh.data.AuthStateDataSource

class HomeViewModel(
    private val authStateDataSource: AuthStateDataSource,
    private val authService: AuthorizationService
) : ViewModel() {

    private val apolloClient = ApolloClient.Builder()
        .serverUrl("https://glimesh.tv/api/graph")
        .build()

    private val _text = MutableLiveData<String>().apply {
        value = "This is the following Fragment"
    }
    val text: LiveData<String> = _text

    private val _followingCount = MutableLiveData<Int>().apply {
        value = 0
    }
    val followingCount: LiveData<Int> = _followingCount

    private val _followingLiveChannels = MutableLiveData<Int>().apply {
        value = 0
    }
    val followingLiveChannels: LiveData<Int> = _followingLiveChannels

    private fun fetchFollowing() {
        authStateDataSource.getCurrent()
            .performActionWithFreshTokens(authService, this::fetchFollowing)
    }

    private fun fetchFollowing(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?
    ) {
        Log.d("HomeViewModel", "accessToken:$accessToken idToken:$idToken")
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _text.value = "Fetching..."
            }

            val response = apolloClient.query(MyFollowingQuery())
                .addHttpHeader("Authorization", "Bearer $accessToken")
                .execute()

            withContext(Dispatchers.Main) {
                _text.value = response.data?.toString()
                _followingCount.value = response.data?.myself?.countFollowing
                _followingLiveChannels.value = response.data?.myself?.followingLiveChannels?.count
            }
        }
    }

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            fetchFollowing()
        }
    }
}
