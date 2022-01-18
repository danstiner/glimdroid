package com.danielstiner.glimdroid.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.Nullable
import net.openid.appauth.*
import org.json.JSONException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class AuthStateDataSource(context: Context) {

    private val TAG = "AuthStateManager"

    private val STORE_NAME = "AuthState"
    private val KEY_STATE = "state"

    private var authorizationService = AuthorizationService(context)
    private var mPrefs: SharedPreferences =
        context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private var mPrefsLock: ReentrantLock = ReentrantLock()
    private var mCurrentAuthState: AtomicReference<AuthState> = AtomicReference()

    companion object {
        private val INSTANCE_REF: AtomicReference<WeakReference<AuthStateDataSource>> =
            AtomicReference(WeakReference(null))

        @AnyThread
        fun getInstance(context: Context): AuthStateDataSource {
            var manager: AuthStateDataSource? = INSTANCE_REF.get().get()
            if (manager == null) {
                manager = AuthStateDataSource(context.applicationContext)
                INSTANCE_REF.set(WeakReference(manager))
            }
            return manager
        }
    }

    @AnyThread
    fun getCurrent(): AuthState {
        if (mCurrentAuthState.get() != null) {
            return mCurrentAuthState.get()
        }
        val state = readState()
        return if (mCurrentAuthState.compareAndSet(null, state)) {
            state
        } else {
            mCurrentAuthState.get()
        }
    }

    @AnyThread
    suspend fun freshAccessToken(): String {
        return suspendCoroutine { continuation ->
            getCurrent().performActionWithFreshTokens(
                authorizationService
            ) { accessToken, _, ex ->
                if (ex != null) {
                    continuation.resumeWithException(ex)
                } else {
                    continuation.resume(accessToken!!)
                }
            }
        }
    }

    @AnyThread
    fun replace(state: AuthState): AuthState {
        writeState(state)
        mCurrentAuthState.set(state)
        return state
    }

    @AnyThread
    fun updateAfterAuthorization(
        @Nullable response: AuthorizationResponse?,
        @Nullable ex: AuthorizationException?
    ): AuthState {
        val current = getCurrent()
        current.update(response, ex)
        return replace(current)
    }

    @AnyThread
    fun updateAfterTokenResponse(
        @Nullable response: TokenResponse?,
        @Nullable ex: AuthorizationException?
    ): AuthState {
        val current = getCurrent()
        current.update(response, ex)
        return replace(current)
    }

    @AnyThread
    fun updateAfterRegistration(
        response: RegistrationResponse?,
        ex: AuthorizationException?
    ): AuthState {
        val current = getCurrent()
        if (ex != null) {
            return current
        }
        current.update(response)
        return replace(current)
    }

    @AnyThread
    private fun readState(): AuthState {
        mPrefsLock.lock()
        return try {
            val currentState = mPrefs.getString(KEY_STATE, null) ?: return AuthState()
            try {
                AuthState.jsonDeserialize(currentState)
            } catch (ex: JSONException) {
                Log.w(TAG, "Failed to deserialize stored auth state - discarding")
                AuthState()
            }
        } finally {
            mPrefsLock.unlock()
        }
    }

    @AnyThread
    private fun writeState(@Nullable state: AuthState?) {
        mPrefsLock.lock()
        try {
            val editor = mPrefs.edit()
            if (state == null) {
                editor.remove(KEY_STATE)
            } else {
                editor.putString(KEY_STATE, state.jsonSerializeString())
            }
            check(editor.commit()) { "Failed to write state to shared prefs" }
        } finally {
            mPrefsLock.unlock()
        }
    }
}