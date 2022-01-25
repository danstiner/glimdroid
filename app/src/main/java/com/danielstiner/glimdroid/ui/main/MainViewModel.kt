package com.danielstiner.glimdroid.ui.main

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val auth: AuthStateDataSource,
    private val users: UserRepository
) : ViewModel() {
    val isAuthorized: Boolean
        get() = auth.getCurrent().isAuthorized

    private val _avatarUri = MutableLiveData<Uri?>()
    val avatarUri: LiveData<Uri?> = _avatarUri

    fun fetch() {
        viewModelScope.launch {
            val me = users.me()

            withContext(Dispatchers.Main) {
                _avatarUri.value = me.avatarUrl?.let { Uri.parse(it) }
            }
        }
    }
}