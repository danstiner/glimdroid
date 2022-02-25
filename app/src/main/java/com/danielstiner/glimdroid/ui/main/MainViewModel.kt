package com.danielstiner.glimdroid.ui.main

import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException

class MainViewModel(
    private val auth: AuthStateDataSource,
    private val users: UserRepository
) : ViewModel() {

    private val _isAuthorized = MutableLiveData(auth.isAuthorized)
    val isAuthorized: LiveData<Boolean> = _isAuthorized

    private val _avatarUri = MutableLiveData<Uri?>()
    val avatarUri: LiveData<Uri?> = _avatarUri

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                auth.freshAccessToken()
                _isAuthorized.postValue(auth.isAuthorized)

            } catch (ex: AuthorizationException) {
                Log.i(TAG, "Fetching fresh access token failed", ex)
                _isAuthorized.postValue(false)
            }
        }
    }

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val me = users.me()

                withContext(Dispatchers.Main) {
                    _avatarUri.value = me.avatarUrl?.let { Uri.parse(it) }
                }
            } catch (ex: AuthorizationException) {
                Log.e(TAG, "Fetch failed", ex)
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}