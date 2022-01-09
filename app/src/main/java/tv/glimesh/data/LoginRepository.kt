package tv.glimesh.data

import tv.glimesh.data.model.LoggedInUser

/**
 * Class that requests authentication and user information from the remote data source and
 * maintains an in-memory cache of login status and user credentials information.
 */

class LoginRepository(
    val dataSource: LoginDataSource,
    val authStateDataSource: AuthStateDataSource
) {

    val isLoggedIn: Boolean
        get() = authStateDataSource.getCurrent().isAuthorized

    fun logout() {
        dataSource.logout()
    }

    fun login(): Result<LoggedInUser> {
        // handle login
        val result = dataSource.login()

        return result
    }
}