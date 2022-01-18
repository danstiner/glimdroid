package com.danielstiner.glimdroid.ui.main

import androidx.lifecycle.ViewModel
import com.danielstiner.glimdroid.data.AuthStateDataSource

class MainViewModel(private val authStateDataSource: AuthStateDataSource) : ViewModel() {
    val isAuthorized: Boolean
        get() = authStateDataSource.getCurrent().isAuthorized
}