package com.danielstiner.glimdroid.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielstiner.glimdroid.R
import com.danielstiner.glimdroid.data.LoginRepository
import com.danielstiner.glimdroid.data.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(private val loginRepository: LoginRepository) : ViewModel() {

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    val isAuthorized: Boolean
        get() = loginRepository.isLoggedIn

    fun login() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = loginRepository.login()

            withContext(Dispatchers.Main) {
                if (result is Result.Ok) {
                    _loginResult.value =
                        LoginResult(success = LoggedInUserView())
                } else {
                    _loginResult.value = LoginResult(error = R.string.login_failed)
                }
            }
        }
    }
}