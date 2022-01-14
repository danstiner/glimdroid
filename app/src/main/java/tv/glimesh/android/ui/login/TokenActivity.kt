package tv.glimesh.android.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.*
import net.openid.appauth.AuthorizationService.TokenResponseCallback
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import tv.glimesh.android.MainActivity
import tv.glimesh.android.data.AuthStateDataSource
import tv.glimesh.android.databinding.ActivityTokenBinding

class TokenActivity : AppCompatActivity() {

    private val TAG = "TokenActivity"
    private lateinit var binding: ActivityTokenBinding
    private lateinit var mStateManager: AuthStateDataSource
    private lateinit var authService: AuthorizationService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityTokenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mStateManager = AuthStateDataSource.getInstance(this)
        authService = AuthorizationService(this)
    }

    override fun onStart() {
        super.onStart()

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        Log.d(
            TAG,
            "onStart response:$response, ex:$ex, isAuthorized:${mStateManager.getCurrent().isAuthorized}"
        )

        if (response != null || ex != null) {
            mStateManager.updateAfterAuthorization(response, ex)
        }

        when {
            response?.authorizationCode != null -> {
                Log.d(TAG, "Authorization code exchange is required")
                mStateManager.updateAfterAuthorization(response, ex)
                exchangeAuthorizationCode(response)
            }
            ex != null -> {
                displayNotAuthorized("Authorization flow failed: " + ex.message)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
            else -> {
                displayNotAuthorized("No authorization state retained - reauthorization required")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        }
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
        performTokenRequest(
            authorizationResponse.createTokenExchangeRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleCodeExchangeResponse(
                tokenResponse,
                authException
            )
        }
    }

    @MainThread
    private fun performTokenRequest(
        request: TokenRequest,
        callback: TokenResponseCallback
    ) {
        val clientAuthentication: ClientAuthentication = try {
            mStateManager.getCurrent().clientAuthentication
        } catch (ex: UnsupportedAuthenticationMethod) {
            Log.d(
                TAG, "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)", ex
            )
            displayNotAuthorized("Client authentication method is unsupported")
            return
        }
        authService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    @WorkerThread
    private fun handleCodeExchangeResponse(
        @Nullable tokenResponse: TokenResponse?,
        @Nullable authException: AuthorizationException?
    ) {
        mStateManager.updateAfterTokenResponse(tokenResponse, authException)
        if (!mStateManager.getCurrent().isAuthorized) {
            val message = ("Authorization Code exchange failed"
                    + if (authException != null) authException.error else "")

            // WrongThread inference is incorrect for lambdas
            runOnUiThread { displayNotAuthorized(message) }
        } else {
            runOnUiThread {
                displayAuthorized()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun displayAuthorized() {
        Log.i(TAG, "Authorized")
        Toast.makeText(
            applicationContext,
            "Authorized",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun displayNotAuthorized(message: String) {
        Log.i(TAG, "Not authorized: $message")
        Toast.makeText(
            applicationContext,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}