package com.danielstiner.glimdroid.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.GlimeshSocketDataSource
import com.danielstiner.glimdroid.data.UserRepository

/**
 * ViewModel provider factory to instantiate MainViewModel.
 * Required given MainViewModel has a non-empty constructor
 */
class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val auth = AuthStateDataSource.getInstance(context)
            return MainViewModel(
                auth = auth,
                users = UserRepository(GlimeshSocketDataSource.getInstance(auth))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
