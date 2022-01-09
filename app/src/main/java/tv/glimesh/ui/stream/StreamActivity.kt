package tv.glimesh.ui.stream

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoSink
import tv.glimesh.databinding.ActivityStreamBinding
import tv.glimesh.ui.login.LoginViewModel

class StreamActivity : AppCompatActivity() {

    private lateinit var streamViewModel: StreamViewModel
    private lateinit var binding: ActivityStreamBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglBase = EglBase.create();

        streamViewModel = ViewModelProvider(
            this,
            StreamViewModelFactory(applicationContext, eglBase.eglBaseContext)
        )[StreamViewModel::class.java]

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.videoView.setEnableHardwareScaler(true)


        streamViewModel.videoTrack.observe(this, Observer {
            val videoTrack = it ?: return@Observer

            videoTrack.addSink(binding.videoView)
        })

        val smashbetsChannelId = 10552L

        streamViewModel.watch(smashbetsChannelId)
    }
}
