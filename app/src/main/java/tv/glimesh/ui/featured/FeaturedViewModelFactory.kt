package tv.glimesh.ui.featured

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tv.glimesh.data.AuthStateDataSource
import tv.glimesh.data.GlimeshDataSource

/**
 * ViewModel provider factory to instantiate FeaturedViewModel.
 * Required given FeaturedViewModel has a non-empty constructor
 */
class FeaturedViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeaturedViewModel::class.java)) {
            return FeaturedViewModel(
                GlimeshDataSource(AuthStateDataSource.getInstance(context))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}