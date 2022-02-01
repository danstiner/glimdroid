package com.danielstiner.glimdroid.ui.channel

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.danielstiner.glimdroid.data.*


/**
 * ViewModel provider factory to instantiates ChannelViewModel.
 * Required given ChannelViewModel has a non-empty constructor
 */
class ChannelViewModelFactory(
    private val applicationContext: Context
) : ViewModelProvider.Factory {

    private val TAG = "ChannelViewModelFactory"

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelViewModel::class.java)) {

            val auth = AuthStateDataSource(applicationContext)
            val socket = GlimeshSocketDataSource.getInstance(auth = auth)

            return ChannelViewModel(
                channels = ChannelRepository(socket),
                chats = ChatRepository(socket),
                users = UserRepository(socket),
                countryCode = getCountryCode(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    private fun getCountryCode(): String {
        val networkCountryIso = ContextCompat.getSystemService(
            applicationContext,
            TelephonyManager::class.java
        )?.networkCountryIso?.uppercase()

        if (networkCountryIso == null) {
            Log.w(TAG, "No network country code available, defaulting to US")
        }
        return networkCountryIso ?: "US"
    }

}
