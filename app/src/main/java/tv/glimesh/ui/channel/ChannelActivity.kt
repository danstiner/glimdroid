package tv.glimesh.ui.channel

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import tv.glimesh.databinding.ActivityChannelBinding
import tv.glimesh.ui.home.CHANNEL_ID

const val smashbetsChannelId = 10552L

class StreamActivity : AppCompatActivity() {

    private lateinit var streamViewModel: StreamViewModel
    private lateinit var binding: ActivityChannelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglBase = EglBase.create()

        streamViewModel = ViewModelProvider(
            this,
            ChannelViewModelFactory(applicationContext, eglBase.eglBaseContext)
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
                    .setAspectRatio(Rational(16, 9))
                    .setSourceRectHint(sourceRectHint)
                    .setAutoEnterEnabled(true)
                    .build()
            )
        }

        streamViewModel.videoTrack.observe(this, Observer {
            val videoTrack = it ?: return@Observer

            videoTrack.addSink(binding.videoView)
        })
    }

    override fun onStart() {
        super.onStart()

        var channelId = intent.getLongExtra(CHANNEL_ID, smashbetsChannelId)

        Log.d(TAG, "Watching channelId:$channelId")
        streamViewModel.watch(channelId)
    }

    override fun onUserLeaveHint() {
        // TODO Check if paused before PiP'in
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sourceRectHint = Rect()
            binding.videoView.getGlobalVisibleRect(sourceRectHint)
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setSourceRectHint(sourceRectHint)
                    .build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean,
                                               newConfig: Configuration
    ) {
        if (isInPictureInPictureMode) {
            // Hide the normal UI (controls, etc.) while in picture-in-picture mode
            binding.group.visibility = View.INVISIBLE
        } else {
            // Restore the normal UI
            binding.group.visibility = View.VISIBLE
        }
    }
}
