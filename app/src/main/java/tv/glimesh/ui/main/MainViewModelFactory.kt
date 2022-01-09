package tv.glimesh.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tv.glimesh.data.AuthStateDataSource

/**
 * ViewModel provider factory to instantiate MainViewModel.
 * Required given MainViewModel has a non-empty constructor
 */
class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(AuthStateDataSource.getInstance(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}