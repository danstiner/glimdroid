package tv.glimesh.ui.stream

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoSink
import tv.glimesh.databinding.ActivityStreamBinding

class StreamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStreamBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglBase = EglBase.create();

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.videoView.setEnableHardwareScaler(true)

        // val rtcClient = new FtlRTCClient(this);

//        createFtlConnection(binding.videoView, "SmashBets");
    }

    private fun createFtlConnection(videoSink: VideoSink, username: String) {
        TODO()
    }
}
