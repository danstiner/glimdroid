package tv.glimesh.ui.stream

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import tv.glimesh.databinding.ActivityStreamBinding


class StreamActivity : AppCompatActivity() {

    private lateinit var streamViewModel: StreamViewModel
    private lateinit var binding: ActivityStreamBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglBase = EglBase.create()

        streamViewModel = ViewModelProvider(
            this,
            StreamViewModelFactory(applicationContext, eglBase.eglBaseContext)
        )[StreamViewModel::class.java]

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.videoView.setEnableHardwareScaler(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            val sourceRectHint = Rect()
            binding.videoView.getGlobalVisibleRect(sourceRectHint)

            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .setSourceRectHint(sourceRectHint)
                    .setAutoEnterEnabled(true)
                    .build()
            )
        }

        streamViewModel.videoTrack.observe(this, Observer {
            val videoTrack = it ?: return@Observer

            videoTrack.addSink(binding.videoView)
        })

        val smashbetsChannelId = 10552L

        streamViewModel.watch(smashbetsChannelId)
    }
}
