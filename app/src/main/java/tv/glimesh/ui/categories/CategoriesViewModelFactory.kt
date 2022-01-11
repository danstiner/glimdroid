package tv.glimesh.ui.categories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import tv.glimesh.data.AuthStateDataSource
import tv.glimesh.data.GlimeshDataSource

/**
 * ViewModel provider factory to instantiate FeaturedViewModel.
 * Required given FeaturedViewModel has a non-empty constructor
 */
class CategoriesViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoriesViewModel::class.java)) {
            return CategoriesViewModel(
                GlimeshDataSource(AuthStateDataSource.getInstance(context))
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}