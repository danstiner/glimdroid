package com.danielstiner.glimdroid.ui.channel

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.danielstiner.glimdroid.BuildConfig
import com.danielstiner.glimdroid.R
import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.glimdroid.data.model.StreamId
import com.danielstiner.glimdroid.databinding.ActivityChannelBinding
import com.google.android.material.chip.Chip
import org.webrtc.EglBase
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


const val EXTRA_CHANNEL_ID = "tv.glimesh.android.extra.channel.id"
const val EXTRA_STREAM_ID = "tv.glimesh.android.extra.stream.id"
const val EXTRA_STREAM_THUMBNAIL_URL = "tv.glimesh.android.extra.stream.url"

class ChannelActivity : AppCompatActivity() {

    private val TAG = "ChannelActivity"

    private lateinit var viewModel: ChannelViewModel
    private lateinit var binding: ActivityChannelBinding
    private lateinit var proxyVideoSink: ProxyVideoSink
    private var videoPreviewUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglBase = EglBase.create()

        viewModel = ViewModelProvider(
            this,
            ChannelViewModelFactory(applicationContext, eglBase.eglBaseContext)
        )[ChannelViewModel::class.java]

        proxyVideoSink = ProxyVideoSink(binding.videoView)
        loadVideoPreviewUri(
            intent.getStringExtra(EXTRA_STREAM_THUMBNAIL_URL)?.let { Uri.parse(it) }
        )

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setEnableHardwareScaler(true)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(pictureInPictureParams(binding.videoView))
        }

        viewModel.title.observe(this, {
            binding.textviewChannelTitle.text = it
        })
        viewModel.matureContent.observe(this, {
            if (it) {
                binding.chipMature.visibility = View.VISIBLE
            } else {
                binding.chipMature.visibility = View.GONE
            }
        })
        viewModel.language.observe(this, {
            if (it != null) {
                val loc = Locale(it)
                val name: String = loc.getDisplayLanguage(loc)
                binding.chipLanguage.text = name
                binding.chipLanguage.visibility = View.VISIBLE
            } else {
                binding.chipLanguage.visibility = View.GONE
            }
        })
        viewModel.category.observe(this, {
            if (it != null) {
                binding.chipCategory.text = it.name
                binding.chipCategory.visibility = View.VISIBLE
            } else {
                binding.chipCategory.visibility = View.GONE
            }
        })
        viewModel.subcategory.observe(this, {
            if (it != null) {
                binding.chipSubcategory.text = it.name
                binding.chipSubcategory.visibility = View.VISIBLE
            } else {
                binding.chipSubcategory.visibility = View.GONE
            }
        })
        viewModel.tags.observe(this, {
            binding.chipGroupTag.removeAllViews()
            for (tag in it) {
                val view = LayoutInflater.from(binding.chipGroupTag.context)
                    .inflate(R.layout.chip_tag, binding.chipGroupTag, false) as Chip
                view.text = tag.name
                binding.chipGroupTag.addView(view)
            }
        })
        viewModel.streamerDisplayname.observe(this, {
            binding.textviewStreamerDisplayName.text = it
        })
        viewModel.streamerUsername.observe(this, { username ->
            binding.textviewStreamerDisplayName.setOnClickListener {
                openStreamerProfile(username)
            }
            binding.avatarImage.setOnClickListener {
                openStreamerProfile(username)
            }
        })
        viewModel.streamerAvatarUrl.observe(this, {
            if (it != null) {
                Glide
                    .with(this)
                    .load(it)
                    .circleCrop()
                    .into(binding.avatarImage)
            } else {
                Glide.with(this).clear(binding.avatarImage)
            }
        })
        viewModel.viewerCount.observe(this, {
            if (it != null) {
                binding.textviewSubtitle.text = "$it viewers"
            } else {
                binding.textviewSubtitle.text = ""
            }
        })
        viewModel.videoTrack.observe(this, {
            it?.addSink(proxyVideoSink)
        })

        val chatAdapter = ChatAdapter()
        binding.chatRecyclerView.adapter = chatAdapter
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.stackFromEnd = true
        binding.chatRecyclerView.layoutManager = linearLayoutManager
        viewModel.messages.observe(this, {
            chatAdapter.submitList(it)
            // TODO only scroll if we're already at the bottom of the list
            binding.chatRecyclerView.smoothScrollToPosition(it.size)
        })

        binding.chatInputEditText.setOnEditorActionListener { _, id, _ ->
            when (id) {
                EditorInfo.IME_ACTION_SEND -> {
                    sendChatMessage()
                    true
                }
                else -> false
            }
        }

        binding.chatInputEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendChatMessage()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        // Ensure sourceRectHint is updated so exiting PiP animates smoothly to the original view
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.videoView.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                          oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom) {
                    setPictureInPictureParams(pictureInPictureParams(binding.videoView))
                }
            }
        }

        // Hide stream/streamer info when ime keyboard is visible, to leave more room to see chats
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(binding.activityContainer) { view, windowInsets ->
                val imeVisible =
                    view.rootWindowInsets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
                val insets =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                // Apply the insets as a margin to the view. Here the system is setting
                // only the bottom, left, and right dimensions, but apply whichever insets are
                // appropriate to your layout. You can also update the view padding
                // if that's more appropriate.
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    leftMargin = insets.left
                    bottomMargin = insets.bottom
                    rightMargin = insets.right
                    topMargin = insets.top
                }

                if (imeVisible) {
                    binding.groupInfo.visibility = View.GONE
                } else {
                    binding.groupInfo.visibility = View.VISIBLE
                }

                // Return CONSUMED if you don't want want the window insets to keep being
                // passed down to descendant views.
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    private fun openStreamerProfile(username: String) {
        enterPictureInPicture()
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                BASE_URI.buildUpon()
                    .appendPath(username)
                    .appendPath("profile").build()
            )
        )
    }

    private fun sendChatMessage() {
        viewModel.sendMessage(binding.chatInputEditText.text.toString().trim())
        binding.chatInputEditText.setText("", TextView.BufferType.NORMAL)
    }

    private fun loadVideoPreviewUri(uri: Uri?) {
        val currentWithoutQuery = videoPreviewUri?.buildUpon()?.clearQuery()?.build()
        val newWithoutQuery = uri?.buildUpon()?.clearQuery()?.build()

        when {
            newWithoutQuery == currentWithoutQuery -> {
                proxyVideoSink.showPreview()
            }
            uri != null -> {
                Glide.with(this).clear(binding.videoPreview)
                proxyVideoSink.showPreview()
                Glide
                    .with(this)
                    .asBitmap()
                    .load(uri)
                    .into(binding.videoPreview)
            }
            else -> {
                Glide.with(this).clear(binding.videoPreview)
            }
        }

        videoPreviewUri = uri
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pictureInPictureParams(sourceView: View): PictureInPictureParams {
        val sourceRectHint = Rect()
        sourceView.getGlobalVisibleRect(sourceRectHint)
        val builder = PictureInPictureParams.Builder()
            .setSourceRectHint(sourceRectHint)
            .setAspectRatio(Rational(16, 9))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true);
        }

        return builder.build()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        watch(intent)
    }

    override fun onStop() {
        super.onStop()
        stopWatching()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: ${lifecycle.currentState}")
        if (lifecycle.currentState == Lifecycle.State.STARTED) {
            watch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // TODO: Check if playing but not entering PiP, if so stream should be paused
    }

    override fun onUserLeaveHint() {
        if (viewModel.isWatching) {
            enterPictureInPicture()
        } else {
            super.onUserLeaveHint()
        }
    }

    override fun onBackPressed() {
        if (viewModel.isWatching) {
            enterPictureInPicture()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        Log.d(TAG, "Picture-in-picture mode changed to: $isInPictureInPictureMode")
        if (isInPictureInPictureMode) {
            // Hide the normal UI (controls, etc.) while in picture-in-picture mode
            binding.group.visibility = View.INVISIBLE
        } else {
            // Restore the normal UI
            binding.group.visibility = View.VISIBLE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.orientation === Configuration.ORIENTATION_LANDSCAPE) {
            animateConstraintLayout(
                binding.root,
                ConstraintSet().apply {
                    clone(binding.root)
                    connect(
                        R.id.video_view,
                        ConstraintSet.BOTTOM,
                        R.id.parent,
                        ConstraintSet.BOTTOM
                    )
                },
                100
            )
        } else if (newConfig.orientation === Configuration.ORIENTATION_PORTRAIT) {
            animateConstraintLayout(
                binding.root,
                ConstraintSet().apply {
                    clone(binding.root)
                    clear(R.id.video_view, ConstraintSet.BOTTOM)
                },
                100
            )
        }
    }

    private fun animateConstraintLayout(
        constraintLayout: ConstraintLayout,
        set: ConstraintSet,
        duration: Long
    ) {
        val trans = AutoTransition()
        trans.duration = duration
        trans.interpolator = AccelerateDecelerateInterpolator()
        TransitionManager.beginDelayedTransition(constraintLayout, trans)
        set.applyTo(constraintLayout)
    }

    private fun watch(intent: Intent?) {
        var channelId = intent?.getLongExtra(EXTRA_CHANNEL_ID, 0) ?: 0
        if (intent == null || channelId == 0L) {
            Log.w(TAG, "watch: No channel id to watch in given intent")
            stopWatching()
            return
        }

        var channel = ChannelId(channelId)
        Log.d(TAG, "watch: Watching $channel, current channel:${viewModel.currentChannel}")
        if (viewModel.currentChannel == channel) {
            viewModel.videoTrack.value?.addSink(proxyVideoSink)
        } else {
            stopWatching()
            loadVideoPreviewUri(
                intent.getStringExtra(EXTRA_STREAM_THUMBNAIL_URL)?.let { Uri.parse(it) }
            )
            viewModel.watch(channel)
        }
    }

    private fun stopWatching() {
        viewModel.stopWatching()
        viewModel.videoTrack.value?.removeSink(proxyVideoSink)
    }

    private fun enterPictureInPicture() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(pictureInPictureParams(binding.videoView))
        }
    }

    companion object {
        private val BASE_URI = Uri.parse(BuildConfig.GLIMESH_BASE_URL)

        fun intent(
            context: Context,
            channel: ChannelId,
            stream: StreamId,
            streamThumbnailUrl: Uri? = null
        ) =
            Intent(context, ChannelActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channel.id)
                putExtra(EXTRA_STREAM_ID, stream.id)
                streamThumbnailUrl?.let {
                    putExtra(
                        EXTRA_STREAM_THUMBNAIL_URL,
                        streamThumbnailUrl.toString()
                    )
                }
            }
    }

    inner class ProxyVideoSink(private val sink: VideoSink) : VideoSink {
        var previewShown = AtomicBoolean(true)
        override fun onFrame(frame: VideoFrame) {
            sink.onFrame(frame)
            if (previewShown.get()) {
                if (previewShown.getAndSet(false)) {
                    runOnUiThread { binding.videoPreview.visibility = View.GONE }
                }
            }
        }

        fun showPreview() {
            if (!previewShown.getAndSet(true)) {
                runOnUiThread { binding.videoPreview.visibility = View.VISIBLE }
            }
        }
    }
}
