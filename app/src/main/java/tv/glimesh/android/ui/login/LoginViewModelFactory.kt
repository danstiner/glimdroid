package tv.glimesh.android.ui.login

import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import net.openid.appauth.AuthorizationService
import tv.glimesh.android.data.AuthStateDataSource
import tv.glimesh.android.data.LoginDataSource
import tv.glimesh.android.data.LoginRepository

/**
 * ViewModel provider factory to instantiate LoginViewModel.
 * Required given LoginViewModel has a non-empty constructor
 */
class LoginViewModelFactory(val context: Context, private val completionIntent: PendingIntent) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            val authService = AuthorizationService(context)
            val authStateDataSource = AuthStateDataSource(context)
            return LoginViewModel(
                loginRepository = LoginRepository(
                    dataSource = LoginDataSource(authService, completionIntent),
                    authStateDataSource = authStateDataSource,
                )
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}