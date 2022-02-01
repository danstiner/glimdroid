package com.danielstiner.glimdroid.data

import android.app.PendingIntent
import android.net.Uri
import com.danielstiner.glimdroid.BuildConfig
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.io.IOException

val GLIMESH_BASE_URI: Uri = Uri.parse(BuildConfig.GLIMESH_BASE_URL)

const val CLIENT_ID: String = BuildConfig.OAUTH_CLIENT_ID
val REDIRECT_URI: Uri = Uri.parse(BuildConfig.OAUTH_REDIRECT_URL)
val AUTHORIZATION_ENDPOINT: Uri =
    GLIMESH_BASE_URI.buildUpon().appendEncodedPath("oauth/authorize").build()
val TOKEN_ENDPOINT: Uri = GLIMESH_BASE_URI.buildUpon().appendEncodedPath("api/oauth/token").build()
val SERVICE_CONFIGURATION =
    AuthorizationServiceConfiguration(AUTHORIZATION_ENDPOINT, TOKEN_ENDPOINT)

// Space-delimited set of case-sensitive scope identifiers to request
private const val SCOPE: String = "public chat follow"

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
class LoginDataSource(
    private val authService: AuthorizationService,
    private val completionIntent: PendingIntent
) {
    fun login(): Result<Unit> {
        return try {
            val authRequest = AuthorizationRequest.Builder(
                SERVICE_CONFIGURATION, CLIENT_ID, ResponseTypeValues.CODE, REDIRECT_URI
            ).setScope(SCOPE).build()

            authService.performAuthorizationRequest(
                authRequest,
                completionIntent
            )
            Result.Ok(Unit)
        } catch (e: Throwable) {
            Result.Err(IOException("Error logging in", e))
        }
    }

    fun logout() {
        // TODO: revoke authentication
    }
}