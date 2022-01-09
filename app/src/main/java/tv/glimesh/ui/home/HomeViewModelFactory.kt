package tv.glimesh.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.openid.appauth.AuthorizationService
import tv.glimesh.data.AuthStateDataSource

/**
 * ViewModel provider factory to instantiate HomeViewModel.
 * Required given HomeViewModel has a non-empty constructor
 */
class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                AuthStateDataSource.getInstance(context),
                AuthorizationService(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}