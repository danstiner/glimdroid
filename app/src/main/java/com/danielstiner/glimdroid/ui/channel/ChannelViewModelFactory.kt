package com.danielstiner.glimdroid.ui.channel

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.ChannelRepository
import com.danielstiner.glimdroid.data.ChatRepository
import com.danielstiner.glimdroid.data.GlimeshSocketDataSource
import org.webrtc.PeerConnectionFactory


/**
 * ViewModel provider factory to instantiates StreamViewModel.
 * Required given StreamViewModel has a non-empty constructor
 */
class ChannelViewModelFactory(
    private val applicationContext: Context
) : ViewModelProvider.Factory {

    private val TAG = "ChannelViewModelFactory"

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelViewModel::class.java)) {

            Log.d(TAG, "Initialize WebRTC.")
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )

            val auth = AuthStateDataSource(applicationContext)
            val socket = GlimeshSocketDataSource.getInstance(auth = auth)
            val countryCode = getCountryCode()

            return ChannelViewModel(
                channels = ChannelRepository(socket),
                chats = ChatRepository(socket),
                countryCode = countryCode,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    private fun getCountryCode(): String {
        val networkCountryIso = getSystemService(
            applicationContext,
            TelephonyManager::class.java
        )?.networkCountryIso?.uppercase()

        if (networkCountryIso == null) {
            Log.w(TAG, "No network country code available, defaulting to US")
        }
        return networkCountryIso ?: "US"
    }
}