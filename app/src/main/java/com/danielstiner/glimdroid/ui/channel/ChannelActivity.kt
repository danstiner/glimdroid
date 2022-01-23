package com.danielstiner.glimdroid.ui.channel

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.danielstiner.glimdroid.BuildConfig
import com.danielstiner.glimdroid.R
import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.glimdroid.data.model.StreamId
import com.danielstiner.glimdroid.databinding.ActivityChannelBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


const val EXTRA_CHANNEL_ID = "tv.glimesh.android.extra.channel.id"
const val EXTRA_STREAM_ID = "tv.glimesh.android.extra.stream.id"
const val EXTRA_STREAM_THUMBNAIL_URL = "tv.glimesh.android.extra.stream.url"

class ChannelActivity : AppCompatActivity() {

    private lateinit var viewModel: ChannelViewModel
    private lateinit var binding: ActivityChannelBinding
    private lateinit var proxyVideoSink: ProxyVideoSink
    private lateinit var peerConnectionFactory: WrappedPeerConnectionFactory

    private var janusMedia: JanusMedia? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hideStatusBar()
        } else {
            showStatusBar()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            ChannelViewModelFactory(applicationContext)
        )[ChannelViewModel::class.java]

        // Start watching that channel!
        watch(intent)

        val eglBase = EglBase.create()
        val factory = buildPeerConnectionFactory(eglBase.eglBaseContext)
        peerConnectionFactory = WrappedPeerConnectionFactory(factory)

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setEnableHardwareScaler(true)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(pictureInPictureParams(binding.videoView))
        }

        proxyVideoSink = ProxyVideoSink(binding.videoView)

        // TODO
        viewModel.channelWatch.observe(this, { watchSession ->

            // TODO remove
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Looper.getMainLooper().isCurrentThread) {
                    TODO("not main thread")
                }
            }

            when {
                watchSession == null -> janusMedia?.close()
                watchSession == janusMedia?.channelWatch && janusMedia?.isActive == true -> {
                    // Do nothing, media is already started for this watch session
                }
                else -> {
                    // New watch session, restart media connection
                    janusMedia?.close()
                    janusMedia = JanusMedia(watchSession)
                }
            }
        })
        viewModel.title.observe(this, { title ->
            binding.textviewChannelTitle.text = title
        })
        viewModel.category.observe(this, { category ->
            with(binding.chipCategory) {
                text = category.name
            }
        })
        viewModel.subcategory.observe(this, { subcategory ->
            with(binding.chipSubcategory) {
                text = subcategory?.name
                visibility = if (subcategory != null) View.VISIBLE else View.GONE
            }
        })
        viewModel.matureContent.observe(this, { matureContent ->
            with(binding.chipMature) {
                visibility = if (matureContent) View.VISIBLE else View.GONE
            }
        })
        viewModel.displayLanguage.observe(this, { displayLanguage ->
            with(binding.chipLanguage) {
                text = displayLanguage
                visibility = if (displayLanguage != null) View.VISIBLE else View.GONE
            }
        })
        viewModel.tags.observe(this, { tags ->
            with(binding.chipGroupTag) {
                removeAllViews()
                for (tag in tags) {
                    val view = LayoutInflater.from(context)
                        .inflate(R.layout.chip_tag, this, false) as Chip
                    view.text = tag.name
                    addView(view)
                }
            }
        })
        viewModel.displayname.observe(this, { streamerDisplayname ->
            binding.textviewStreamerDisplayName.text = streamerDisplayname
        })
        viewModel.username.observe(this, { username ->
            binding.textviewStreamerDisplayName.setOnClickListener {
                openStreamerProfile(username)
            }
            binding.avatarImage.setOnClickListener {
                openStreamerProfile(username)
            }
        })
        viewModel.avatarUri.observe(this, { avatarUri ->
            if (avatarUri != null) {
                Glide
                    .with(this)
                    .load(avatarUri)
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

        // Hide the normal UI (controls, etc.) while in picture-in-picture mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            binding.group.visibility = View.INVISIBLE
        }

        // Ensure sourceRectHint is updated so exiting PiP animates smoothly to the original view
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            binding.videoView.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                          oldLeft, oldTop, oldRight, oldBottom ->
                if (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom) {
                    setPictureInPictureParams(pictureInPictureParams(binding.videoView))
                }
            }
        }

        // Listen for IME keyboard being shown and hide non-essential info to leave more room for
        // the user to see some incoming chat messages while they type
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
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

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        janusMedia?.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Because we use "launchMode=singleTask" on this activity, clicking into other channels
        // will invoke onNewIntent without creating a new activity or task. Without this, the
        // original intent that launched this activity would be kept and used in onCreate if the
        // activity is re-created for some reason(e.g. rotation).
        setIntent(intent)

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
        if (viewModel.isSubscribed) {
            enterPictureInPicture()
        } else {
            super.onUserLeaveHint()
        }
    }

    override fun onBackPressed() {
        if (viewModel.isSubscribed) {
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

    private fun hideStatusBar() {
        ViewCompat.getWindowInsetsController(window.decorView)?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showStatusBar() {
        ViewCompat.getWindowInsetsController(window.decorView)?.apply {
            show(WindowInsetsCompat.Type.systemBars())
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pictureInPictureParams(sourceView: View): PictureInPictureParams {
        val sourceRectHint = Rect()
        sourceView.getGlobalVisibleRect(sourceRectHint)
        val builder = PictureInPictureParams.Builder()
            .setSourceRectHint(sourceRectHint)
            .setAspectRatio(Rational(16, 9))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
        }

        return builder.build()
    }

    private fun watch(intent: Intent) {
        var channel = ChannelId(intent.getLongExtra(EXTRA_CHANNEL_ID, 0))
        val thumbnailUri = intent.getStringExtra(EXTRA_STREAM_THUMBNAIL_URL)?.let { Uri.parse(it) }

        if (channel.id == 0L) {
            Log.e(TAG, "watch: No channel id to watch in given intent")
            return
        }

        viewModel.watch(channel, thumbnailUri)
    }

    private fun enterPictureInPicture() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(pictureInPictureParams(binding.videoView))
        }
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

    companion object {
        private val TAG = "ChannelActivity"
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

    inner class JanusMedia(val channelWatch: ChannelViewModel.ChannelWatch) {

        val isActive get() = coroutineContext.isActive

        private val TAG = "JanusMedia"

        private val coroutineContext = SupervisorJob() + Dispatchers.IO
        private val coroutineScope = CoroutineScope(coroutineContext)

        private var session: JanusFtlSession? = null
        private var connection: JanusRtcConnection? = null
        private var videoTrack: VideoTrack? = null

        @Volatile
        private var closed = false

        init {
            Log.d(TAG, "Starting, channel:${channelWatch.channel.id}")
            loadVideoPreviewUri(viewModel.thumbnailUri.value)

            coroutineScope.launch {
                val ses = JanusFtlSession.create(channelWatch.edgeRoute.url, channelWatch.channel)
                if (closed) {
                    ses.destroy()
                    return@launch
                }
                val con = JanusRtcConnection.create(
                    ses,
                    peerConnectionFactory,
                    coroutineContext
                ) { stream ->
                    runOnUiThread {
                        if (!closed) {
                            videoTrack = stream.videoTracks.single().apply {
                                addSink(proxyVideoSink)
                            }
                        }
                    }
                }

                con.start()

                withContext(Dispatchers.Main) {
                    if (closed) {
                        con.close()
                        videoTrack?.removeSink(proxyVideoSink)
                        ses.destroy()
                    } else {
                        session = ses
                        connection = con
                    }
                }
            }
        }

        fun close() {
            if (closed) {
                Log.d(TAG, "Already closed, channel:${channelWatch.channel.id}")
                return
            }

            Log.d(TAG, "Closing, channel:${channelWatch.channel.id}")

            // Launch to UI thread TODO
            coroutineScope.launch(Dispatchers.Main) {
                closed = true

                // Cleanup
                connection?.close()
                videoTrack?.removeSink(proxyVideoSink)
                session?.destroy()

                this@JanusMedia.coroutineContext.cancel()
            }
        }

        private fun loadVideoPreviewUri(uri: Uri?) {
            if (uri != null) {
                Glide
                    .with(this@ChannelActivity)
                    .asBitmap()
                    .load(uri)
                    .into(binding.videoPreview)
                proxyVideoSink.showPreviewAndProgressBar()
            } else {
                Glide.with(this@ChannelActivity).clear(binding.videoPreview)
                proxyVideoSink.showProgressBarOnly()
            }
        }
    }

    inner class ProxyVideoSink(private val sink: VideoSink) : VideoSink {
        var waitingForFirstFrame = AtomicBoolean(true)
        override fun onFrame(frame: VideoFrame) {
            sink.onFrame(frame)

            // This is a form of double-checked locking as a performance optimization
            if (waitingForFirstFrame.get()) {
                runOnUiThread {
                    if (waitingForFirstFrame.getAndSet(false)) {
                        binding.videoPreview.visibility = View.GONE
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }

        fun showPreviewAndProgressBar() {
            runOnUiThread {
                binding.videoView.clearImage()
                binding.videoPreview.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
                waitingForFirstFrame.set(true)
            }
        }

        fun showProgressBarOnly() {
            runOnUiThread {
                binding.videoView.clearImage()
                binding.progressBar.visibility = View.VISIBLE
                waitingForFirstFrame.set(true)
            }
        }
    }
}
