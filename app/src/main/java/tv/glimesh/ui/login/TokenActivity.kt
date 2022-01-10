package tv.glimesh.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import net.openid.appauth.*
import net.openid.appauth.AuthorizationService.TokenResponseCallback
import net.openid.appauth.ClientAuthentication.UnsupportedAuthenticationMethod
import tv.glimesh.MainActivity
import tv.glimesh.data.AuthStateDataSource
import tv.glimesh.databinding.ActivityTokenBinding


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

        // the stored AuthState is incomplete, so check if we are currently receiving the result of
        // the authorization flow from the browser.
        val response = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)

        if (response != null || ex != null) {
            mStateManager.updateAfterAuthorization(response, ex)
        }

        Log.d(TAG, "onStart response:$response, ex:$ex, isAuthorized:${mStateManager.getCurrent().isAuthorized}")

        when {
            response?.authorizationCode != null -> {
                // authorization code exchange is required
                mStateManager.updateAfterAuthorization(response, ex)
                exchangeAuthorizationCode(response)
            }
            ex != null -> {
                //            displayNotAuthorized("Authorization flow failed: " + ex.message)
            }
            else -> {
                //            displayNotAuthorized("No authorization state retained - reauthorization required")
            }
        }

        if (mStateManager.getCurrent().isAuthorized) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
    }
//
//    @MainThread
//    private fun displayNotAuthorized(explanation: String) {
//        findViewById<View>(R.id.not_authorized).visibility = View.VISIBLE
//        findViewById<View>(R.id.authorized).visibility = View.GONE
//        findViewById<View>(R.id.loading_container).visibility = View.GONE
//        (findViewById<View>(R.id.explanation) as TextView).text = explanation
//        findViewById<View>(R.id.reauth).setOnClickListener { view: View? -> signOut() }
//    }
//
//    @MainThread
//    private fun displayLoading(message: String) {
//        findViewById<View>(R.id.loading_container).visibility = View.VISIBLE
//        findViewById<View>(R.id.authorized).visibility = View.GONE
//        findViewById<View>(R.id.not_authorized).visibility = View.GONE
//        (findViewById<View>(R.id.loading_description) as TextView).text = message
//    }
//
//    @MainThread
//    private fun displayAuthorized() {
//        findViewById<View>(R.id.authorized).visibility = View.VISIBLE
//        findViewById<View>(R.id.not_authorized).visibility = View.GONE
//        findViewById<View>(R.id.loading_container).visibility = View.GONE
//        val state: AuthState = mStateManager.getCurrent()
//        val refreshTokenInfoView = findViewById<TextView>(R.id.refresh_token_info)
//        refreshTokenInfoView.setText(if (state.refreshToken == null) R.string.no_refresh_token_returned else R.string.refresh_token_returned)
//        val idTokenInfoView = findViewById<View>(R.id.id_token_info) as TextView
//        idTokenInfoView.setText(if (state.idToken == null) R.string.no_id_token_returned else R.string.id_token_returned)
//        val accessTokenInfoView = findViewById<View>(R.id.access_token_info) as TextView
//        if (state.accessToken == null) {
//            accessTokenInfoView.setText(R.string.no_access_token_returned)
//        } else {
//            val expiresAt = state.accessTokenExpirationTime
//            if (expiresAt == null) {
//                accessTokenInfoView.setText(R.string.no_access_token_expiry)
//            } else if (expiresAt < System.currentTimeMillis()) {
//                accessTokenInfoView.setText(R.string.access_token_expired)
//            } else {
//                val template = resources.getString(R.string.access_token_expires_at)
//                accessTokenInfoView.text = java.lang.String.format(
//                    template,
//                    DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss ZZ").print(expiresAt)
//                )
//            }
//        }
//        val refreshTokenButton: Button = findViewById<View>(R.id.refresh_token) as Button
//        refreshTokenButton.setVisibility(if (state.refreshToken != null) View.VISIBLE else View.GONE)
//        refreshTokenButton.setOnClickListener { view: View? -> refreshAccessToken() }
//        val viewProfileButton: Button = findViewById<View>(R.id.view_profile) as Button
//        val discoveryDoc = state.authorizationServiceConfiguration!!.discoveryDoc
//        if ((discoveryDoc == null || discoveryDoc.userinfoEndpoint == null)
//            && mConfiguration.getUserInfoEndpointUri() == null
//        ) {
//            viewProfileButton.setVisibility(View.GONE)
//        } else {
//            viewProfileButton.setVisibility(View.VISIBLE)
//            viewProfileButton.setOnClickListener { view: View? -> fetchUserInfo() }
//        }
//        findViewById<View>(R.id.sign_out).setOnClickListener { view: View? -> endSession() }
//        val userInfoCard: View = findViewById(R.id.userinfo_card)
//        val userInfo: JSONObject = mUserInfoJson.get()
//        if (userInfo == null) {
//            userInfoCard.setVisibility(View.INVISIBLE)
//        } else {
//            try {
//                var name: String? = "???"
//                if (userInfo.has("name")) {
//                    name = userInfo.getString("name")
//                }
//                (findViewById<View>(R.id.userinfo_name) as TextView).text = name
//                if (userInfo.has("picture")) {
//                    GlideApp.with(this@TokenActivity)
//                        .load(Uri.parse(userInfo.getString("picture")))
//                        .fitCenter()
//                        .into(findViewById<View>(R.id.userinfo_profile) as ImageView)
//                }
//                (findViewById<View>(R.id.userinfo_json) as TextView).setText(mUserInfoJson.toString())
//                userInfoCard.setVisibility(View.VISIBLE)
//            } catch (ex: JSONException) {
//                Log.e(TAG, "Failed to read userinfo JSON", ex)
//            }
//        }
//    }

    @MainThread
    private fun refreshAccessToken() {
//        displayLoading("Refreshing access token")
        performTokenRequest(
            mStateManager.getCurrent().createTokenRefreshRequest()
        ) { tokenResponse: TokenResponse?, authException: AuthorizationException? ->
            handleAccessTokenResponse(
                tokenResponse,
                authException
            )
        }
    }

    @MainThread
    private fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
//        displayLoading("Exchanging authorization code")
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
        val clientAuthentication: ClientAuthentication
        clientAuthentication = try {
            mStateManager.getCurrent().clientAuthentication
        } catch (ex: UnsupportedAuthenticationMethod) {
            Log.d(
                TAG, "Token request cannot be made, client authentication for the token "
                        + "endpoint could not be constructed (%s)", ex
            )
//            displayNotAuthorized("Client authentication method is unsupported")
            return
        }
        authService.performTokenRequest(
            request,
            clientAuthentication,
            callback
        )
    }

    @WorkerThread
    private fun handleAccessTokenResponse(
        @Nullable tokenResponse: TokenResponse?,
        @Nullable authException: AuthorizationException?
    ) {
        mStateManager.updateAfterTokenResponse(tokenResponse, authException)
//        runOnUiThread { displayAuthorized() }
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
//            runOnUiThread { displayNotAuthorized(message) }
        } else {
//            runOnUiThread { displayAuthorized() }
        }
    }

    /**
     * Demonstrates the use of [AuthState.performActionWithFreshTokens] to retrieve
     * user info from the IDP's user info endpoint. This callback will negotiate a new access
     * token / id token for use in a follow-up action, or provide an error if this fails.
     */
    @MainThread
    private fun fetchUserInfo() {
//        displayLoading("Fetching user info")
        mStateManager.getCurrent().performActionWithFreshTokens(authService, this::fetchUserInfo2)
    }

    @MainThread
    private fun fetchUserInfo2(
        accessToken: String?,
        idToken: String?,
        ex: AuthorizationException?
    ) {
        if (ex != null) {
            Log.e(TAG, "Token refresh failed when fetching user info")
//            mUserInfoJson.set(null)
//            runOnUiThread { displayAuthorized() }
            return
        }
//        val discovery: AuthorizationServiceDiscovery = mStateManager.getCurrent()
//            .getAuthorizationServiceConfiguration().discoveryDoc!!
//        val userInfoEndpoint: Uri = if (mConfiguration.getUserInfoEndpointUri() != null) Uri.parse(
//            mConfiguration.getUserInfoEndpointUri().toString()
//        ) else Uri.parse(discovery.userinfoEndpoint.toString())
//        mExecutor.submit {
//            try {
//                val conn: HttpURLConnection = mConfiguration.getConnectionBuilder().openConnection(
//                    userInfoEndpoint
//                )
//                conn.setRequestProperty("Authorization", "Bearer $accessToken")
//                conn.setInstanceFollowRedirects(false)
//                val response: String = Okio.buffer(Okio.source(conn.getInputStream()))
//                    .readString(Charset.forName("UTF-8"))
//                mUserInfoJson.set(JSONObject(response))
//            } catch (ioEx: IOException) {
//                Log.e(TAG, "Network error when querying userinfo endpoint", ioEx)
//                showSnackbar("Fetching user info failed")
//            } catch (jsonEx: JSONException) {
//                Log.e(TAG, "Failed to parse userinfo response")
//                showSnackbar("Failed to parse user info")
//            }
//            runOnUiThread { displayAuthorized() }
//        }
    }
//
//    @MainThread
//    private fun endSession() {
//        val currentState: AuthState = mStateManager.getCurrent()
//        val config = currentState.authorizationServiceConfiguration
//        if (config!!.endSessionEndpoint != null) {
//            val endSessionIntent: Intent = mAuthService.getEndSessionRequestIntent(
//                EndSessionRequest.Builder(config)
//                    .setIdTokenHint(currentState.idToken)
//                    .setPostLogoutRedirectUri(mConfiguration.getEndSessionRedirectUri())
//                    .build()
//            )
//            startActivityForResult(endSessionIntent, END_SESSION_REQUEST_CODE)
//        } else {
//            signOut()
//        }
//    }

    @MainThread
    private fun signOut() {
        // discard the authorization and token state, but retain the configuration and
        // dynamic client registration (if applicable), to save from retrieving them again.
        val currentState: AuthState = mStateManager.getCurrent()
        val clearedState = AuthState(currentState.authorizationServiceConfiguration!!)
        if (currentState.lastRegistrationResponse != null) {
            clearedState.update(currentState.lastRegistrationResponse)
        }
        mStateManager.replace(clearedState)
        val mainIntent = Intent(this, LoginActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(mainIntent)
        finish()
    }
}