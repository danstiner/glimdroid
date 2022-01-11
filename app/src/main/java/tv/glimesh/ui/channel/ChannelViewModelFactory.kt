package tv.glimesh.ui.channel

import android.content.Context
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.Logging
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import tv.glimesh.data.AuthStateDataSource
import tv.glimesh.data.GlimeshDataSource
import tv.glimesh.data.GlimeshWebsocketDataSource
import tv.glimesh.data.JanusRestApi
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * ViewModel provider factory to instantiates StreamViewModel.
 * Required given StreamViewModel has a non-empty constructor
 */
class ChannelViewModelFactory(
    private val applicationContext: Context,
    private val eglContext: EglBase.Context
) : ViewModelProvider.Factory {

    private val TAG = "ChannelViewModelFactory"

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelViewModel::class.java)) {

            val authState = AuthStateDataSource(applicationContext)

            val networkCountryIso = getSystemService(
                applicationContext,
                TelephonyManager::class.java
            )?.networkCountryIso
            if (networkCountryIso == null) {
                Log.w(TAG, "No network country code available, defaulting to US")
            }
            val countryCode = networkCountryIso ?: "US"

            // Executor thread is started once used for all
            // peer connection API calls to ensure new peer connection factory is
            // created on the same thread as previously destroyed factory.
            val executor: ExecutorService = Executors.newSingleThreadExecutor()

            executor.execute {
                Log.d(TAG, "Initialize WebRTC.")

                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
                )
            }

            val videoEncoderFactory = DefaultVideoDecoderFactory(eglContext)

            val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
                .setUseStereoOutput(true)
                // TODO apply audio attributes to make audio a media stream once newer webrtc version with the api is avaiable
//                .setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).setUsage(AudioAttributes.USAGE_MEDIA).build())
                .createAudioDeviceModule()

            val factory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(videoEncoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

            // Set INFO libjingle logging.
            // NOTE: this _must_ happen while |factory| is alive!
            Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

            return ChannelViewModel(
                janus = JanusRestApi(Uri.parse("https://do-nyc3-edge1.kjfk.live.glimesh.tv/janus")),
                peerConnectionFactory = factory,
                executor = executor,
                glimesh = GlimeshDataSource(authState = authState),
                glimeshSocket = GlimeshWebsocketDataSource(authState = authState),
                countryCode = countryCode,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}