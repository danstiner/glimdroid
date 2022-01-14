package tv.glimesh.android.ui.main

import androidx.lifecycle.ViewModel
import tv.glimesh.android.data.AuthStateDataSource

class MainViewModel(private val authStateDataSource: AuthStateDataSource) : ViewModel() {
    val isAuthorized: Boolean
        get() = authStateDataSource.getCurrent().isAuthorized
}