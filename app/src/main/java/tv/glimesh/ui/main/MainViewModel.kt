package tv.glimesh.ui.main

import androidx.lifecycle.ViewModel
import tv.glimesh.data.AuthStateDataSource

class MainViewModel(private val authStateDataSource: AuthStateDataSource) : ViewModel() {
    val isAuthorized: Boolean
        get() = authStateDataSource.getCurrent().isAuthorized
}