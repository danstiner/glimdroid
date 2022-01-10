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
import tv.glimesh.data.model.ChannelId
import tv.glimesh.databinding.ActivityChannelBinding
import tv.glimesh.ui.home.CHANNEL_ID

const val smashbetsChannelId = 10552L

class ChannelActivity : AppCompatActivity() {

    private lateinit var viewModel: ChannelViewModel
    private lateinit var binding: ActivityChannelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglBase = EglBase.create()

        viewModel = ViewModelProvider(
            this,
            ChannelViewModelFactory(applicationContext, eglBase.eglBaseContext)
        )[ChannelViewModel::class.java]

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

        viewModel.title.observe(this, {
            binding.textviewChannelTitle.text = it
        })
        viewModel.streamerDisplayName.observe(this, {
            binding.textviewStreamerDisplayName.text = it
        })
        viewModel.viewerCount.observe(this, {
            if (it != null) {
                binding.textviewChannelSubtitle.text = "$it viewers"
            } else {
                binding.textviewChannelSubtitle.text = "Not live"
            }
        })
        viewModel.videoTrack.observe(this, {
            it.addSink(binding.videoView)
        })
    }

    override fun onStart() {
        super.onStart()

        var channelId = ChannelId(intent.getLongExtra(CHANNEL_ID, smashbetsChannelId))

        Log.d(TAG, "Watching $channelId")
        viewModel.watch(channelId)
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
