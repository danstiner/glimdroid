package com.danielstiner.glimdroid.ui.channel

import android.content.Context
import android.media.AudioAttributes
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.ChannelRepository
import com.danielstiner.glimdroid.data.ChatRepository
import com.danielstiner.glimdroid.data.GlimeshSocketDataSource
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.Logging
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule


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

            val eglBase = EglBase.create()

            Log.d(TAG, "Initialize WebRTC.")
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )

            val factory = buildPeerConnectionFactory(eglBase.eglBaseContext)
            val auth = AuthStateDataSource(applicationContext)
            val socket = GlimeshSocketDataSource.getInstance(auth = auth)
            val countryCode = getCountryCode()

            return ChannelViewModel(
                peerConnectionFactory = WrappedPeerConnectionFactory(factory),
                channels = ChannelRepository(socket),
                chats = ChatRepository(socket),
                countryCode = countryCode,
                eglBase = eglBase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    private fun buildPeerConnectionFactory(eglBaseContext: EglBase.Context): PeerConnectionFactory {
        val videoEncoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
            .setUseStereoOutput(true)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            .createAudioDeviceModule()

        val factory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoEncoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        // Set libjingle logging level
        // NOTE: this _must_ happen while |factory| is alive!
        Logging.enableLogToDebugOutput(Logging.Severity.LS_WARNING)

        return factory
    }

    private fun getCountryCode(): String {
        val networkCountryIso = getSystemService(
            applicationContext,
            TelephonyManager::class.java
        )?.networkCountryIso
        if (networkCountryIso == null) {
            Log.w(TAG, "No network country code available, defaulting to US")
        }
        return networkCountryIso ?: "US"
    }
}