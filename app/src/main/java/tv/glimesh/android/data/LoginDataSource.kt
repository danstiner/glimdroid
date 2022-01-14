package tv.glimesh.android.data

import android.app.PendingIntent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import tv.glimesh.android.data.model.LoggedInUser
import java.io.IOException
import java.util.*

const val CLIENT_ID = "34d2a4c6-e357-4132-881b-d64305853632"

val REDIRECT_URI: Uri = Uri.parse("tv.glimesh.android://oauthcallback")

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource(
    private val authService: AuthorizationService,
    private val completionIntent: PendingIntent
) {

    fun login(): Result<LoggedInUser> {
        try {
            val serviceConfig = AuthorizationServiceConfiguration(
                Uri.parse("https://glimesh.tv/oauth/authorize"), // authorization endpoint
                Uri.parse("https://glimesh.tv/api/oauth/token")  // token endpoint
            )

            val authRequest = AuthorizationRequest.Builder(
                serviceConfig, CLIENT_ID, ResponseTypeValues.CODE, REDIRECT_URI
            ).setScope("public chat").build()

            authService.performAuthorizationRequest(
                authRequest,
                completionIntent
            )
            // TODO: handle loggedInUser authentication
            val fakeUser = LoggedInUser(UUID.randomUUID().toString(), "Jane Doe")
            return Result.Success(fakeUser)
        } catch (e: Throwable) {
            return Result.Error(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}