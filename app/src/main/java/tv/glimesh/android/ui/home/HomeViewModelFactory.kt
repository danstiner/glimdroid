package tv.glimesh.android.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tv.glimesh.android.data.AuthStateDataSource
import tv.glimesh.android.data.GlimeshDataSource

/**
 * ViewModel provider factory to instantiate HomeViewModel.
 * Required given HomeViewModel has a non-empty constructor
 */
class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                GlimeshDataSource(AuthStateDataSource.getInstance(context))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}