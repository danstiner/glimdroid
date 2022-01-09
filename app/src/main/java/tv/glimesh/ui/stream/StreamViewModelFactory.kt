package tv.glimesh.ui.stream

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.webrtc.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * ViewModel provider factory to instantiates StreamViewModel.
 * Required given StreamViewModel has a non-empty constructor
 */
class StreamViewModelFactory(private val applicationContext: Context, private val eglContext: EglBase.Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreamViewModel::class.java)) {

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

            val factory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(videoEncoderFactory).createPeerConnectionFactory()

            // Set INFO libjingle logging.
            // NOTE: this _must_ happen while |factory| is alive!
//            Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

            return StreamViewModel(
                janus = JanusRestApi(Uri.parse("https://do-nyc3-edge1.kjfk.live.glimesh.tv/janus")),
                peerConnectionFactory = factory,
                executor = executor,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}