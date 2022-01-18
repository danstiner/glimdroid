package com.danielstiner.glimdroid.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.GlimeshDataSource
import com.danielstiner.glimdroid.data.GlimeshWebsocketDataSource

/**
 * ViewModel provider factory to instantiate HomeViewModel.
 * Required given HomeViewModel has a non-empty constructor
 */
class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val auth = AuthStateDataSource.getInstance(context)
            return HomeViewModel(
                GlimeshDataSource(auth),
                GlimeshWebsocketDataSource.getInstance(auth)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}