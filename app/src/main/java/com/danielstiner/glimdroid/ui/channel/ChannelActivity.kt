package com.danielstiner.glimdroid.ui.channel

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.danielstiner.glimdroid.R
import com.danielstiner.glimdroid.data.model.ChannelId
import com.danielstiner.glimdroid.data.model.EdgeRoute
import com.danielstiner.glimdroid.data.model.StreamId
import com.danielstiner.glimdroid.databinding.ActivityChannelBinding
import com.danielstiner.glimdroid.ui.Urls
import com.google.android.material.chip.Chip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*


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

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            ChannelViewModelFactory(applicationContext)
        )[ChannelViewModel::class.java]

        // Preserving an EGLContext across activity restarts (e.g. orientation change) is tricky,
        // so for now we create a new context every time. Unfortunately a RTC peer connection is
        // tied to a specific context, so this means activity restarts necessitate creating a new
        // connection which interrupts the viewing experience. This could can be mitigated by
        // handling orientation changes without a restart, but that requires some tricky layout work
        // See https://developer.android.com/guide/topics/resources/runtime-changes
        // See https://gamedev.stackexchange.com/a/172003
        val eglBase = EglBase.create()
        val factory = buildPeerConnectionFactory(eglBase.eglBaseContext)
        peerConnectionFactory = WrappedPeerConnectionFactory(factory)

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setEnableHardwareScaler(true)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(pictureInPictureParams(binding.videoView))
        }

        proxyVideoSink = ProxyVideoSink(binding.videoView)

        // Making the actual RTC connection is a complex multi-step process that must handle being
        // interrupted at any time by activity lifecycle events. As described above we must recreate
        // media peer connections if the activity is recreated. Also if the activity is only stopped
        // we should also stop media from flowing so audio stops and bandwidth is not wasted. (e.g.
        // if user switches to another app but the system has not killed us yet, we stop playback)
        //
        // To accomplish that, we use the ViewModel pattern to hold the state of which channel is
        // being watched, and to handle all the communication over HTTP to glimesh.tv. Once the
        // glimesh.tv has told us which Janus edge server to talk to, this LiveData will fire and
        // we can create a Janus media session that will setup the RTC connection and start piping
        // media down like we want. If the activity is recreated, the ViewModel have preserved
        // the info of which Janus edge server to talk to and this will fire immediately.
        //
        // This makes for a fairly complicated back-and-forth between this activity initiating
        // channel switches when it gets a new Intent describing a channel to watch. Then the
        // viewmodel talks to glimesh.tv and finally fires this observer to start the media session.
        lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                Log.d(
                    TAG,
                    "uiState:$uiState currentChannel:${janusMedia?.channel}, currentlyActive:${janusMedia?.isActive}"
                )

                when {
                    uiState.isStopped || uiState.channel == null || uiState.edgeRoute == null -> janusMedia?.close()
                    uiState.channel == janusMedia?.channel && janusMedia?.isActive == true -> {
                        // Do nothing, media is already started for this watch session
                    }
                    else -> {
                        // Channel changed, restart media connection
                        janusMedia?.close()
                        janusMedia = JanusMedia(uiState.channel, uiState.edgeRoute)
                    }
                }
            }
        }

        viewModel.title.observe(this) { title ->
            binding.textviewChannelTitle.text = title
        }
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
        viewModel.displayname.observe(this, { displayname ->
            binding.textviewStreamerDisplayName.text = displayname
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
        val layoutManager = binding.chatRecyclerView.layoutManager as LinearLayoutManager
        binding.chatRecyclerView.adapter = chatAdapter
        viewModel.messages.observe(this, { chats ->
            // Check if we are scrolled to the end of chat (latest chat item is visible)
            val wasScrolledToLatestChat = layoutManager.findFirstVisibleItemPosition() == 0
            val originalItemCount = chatAdapter.itemCount

            chatAdapter.submitList(chats.reversed()) {
                if (originalItemCount == 0 && chatAdapter.itemCount > 0) {
                    Log.v(TAG, "Chat: Initial load: ${chatAdapter.itemCount}")
                    binding.chatRecyclerView.post {
                        layoutManager.scrollToPosition(0)
                    }
                } else if (wasScrolledToLatestChat) {
                    Log.v(TAG, "Chat: Auto-scrolling to new chat message")
                    binding.chatRecyclerView.post {
                        layoutManager.scrollToPosition(0)
                    }
                }
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
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

                val portrait =
                    resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                Log.d(TAG, "IME visibility change; imeVisible:$imeVisible portrait:$portrait")
                transitionLayoutState(imeVisible = imeVisible)

                // Return CONSUMED if you don't want want the window insets to keep being
                // passed down to descendant views.
                WindowInsetsCompat.CONSUMED
            }
        }

        transitionLayoutState()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        viewModel.start()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")

        viewModel.stop()
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

        watch(intent)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // TODO: Check if playing but not entering PiP, if so stream should be paused
    }

    override fun onUserLeaveHint() {
        if (shouldEnterPictureInPicture) {
            enterPictureInPicture()
        } else {
            super.onUserLeaveHint()
        }
    }

    override fun onBackPressed() {
        if (shouldEnterPictureInPicture) {
            // PiP mode must be entered before navigating to prevent current activity from finishing
            enterPictureInPicture()
            navigateToMainActivity()
        } else {
            // Otherwise navigate before finishing so we don't end up creating a new task
            navigateToMainActivity()
            finish()
        }
    }

    /**
     * PiP mode can be weird with the backstack, we can end up with the main activity *not* being
     * the previous activity a normal back press would go to. Together with singleTask set in the
     * manifest this seems to have the desired behavior (the channel task is *always* a child of
     * the main activity. This may need more re-work in the future as more activities are added,
     * or maybe the channel activity should instead be a floating fragment inside main activity.
     */
    private fun navigateToMainActivity() {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val launcherTask = activityManager.appTasks.singleOrNull { task ->
            task.taskInfo.baseIntent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
        }
        if (launcherTask != null) {
            launcherTask.moveToFront()
        } else {
//            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(
            TAG,
            "onConfigurationChanged; isInPictureInPictureMode:${Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this.isInPictureInPictureMode} orientation:${if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}"
        )
        transitionLayoutState(configuration = newConfig)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.d(
            TAG,
            "onPictureInPictureModeChanged; isInPictureInPictureMode:$isInPictureInPictureMode orientation:${if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}"
        )
        transitionLayoutState(
            isInPictureInPictureMode = isInPictureInPictureMode,
            configuration = newConfig
        )
    }

    private fun transitionLayoutState(
        isInPictureInPictureMode: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && this.isInPictureInPictureMode,
        configuration: Configuration = resources.configuration,
        imeVisible: Boolean = false
    ) {

        val newState = when {
            isInPictureInPictureMode -> R.id.pip
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> R.id.landscape
            imeVisible -> R.id.portrait_ime
            else -> R.id.portrait
        }

        fun stateToString(state: Int) = when (state) {
            R.id.pip -> "pip"
            R.id.landscape -> "landscape"
            R.id.portrait -> "portrait"
            R.id.portrait_ime -> "portrait_ime"
            else -> "unknown"
        }

        if (binding.motion.currentState != newState) {
            Log.d(
                TAG,
                "transitionLayoutState from ${stateToString(binding.motion.currentState)} to ${
                    stateToString(
                        newState
                    )
                }"
            )

            binding.motion.jumpToState(newState)

            if (newState == R.id.landscape) {
                hideStatusBar()
            } else {
                showStatusBar()
            }
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
        if (shouldEnterPictureInPicture) {
            enterPictureInPicture()
        }
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Urls.userProfile(username)
            )
        )
    }

    private fun sendChatMessage() {
        val text = binding.chatInputEditText.text.toString().trim()
        binding.chatInputEditText.setText("", TextView.BufferType.NORMAL)
        if (text.isNotEmpty()) {
            viewModel.sendMessage(text)
        } else {
            Log.w(TAG, "sendChatMessage: text to send was empty")
        }
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

    private val shouldEnterPictureInPicture
        get() = !viewModel.uiState.value.isStopped && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPictureInPicture() =
        enterPictureInPictureMode(pictureInPictureParams(binding.videoView))

    private fun buildPeerConnectionFactory(eglBaseContext: EglBase.Context): PeerConnectionFactory {
        val videoEncoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
            .setUseStereoOutput(true)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            .createAudioDeviceModule()

        Log.v(TAG, "Initialize WebRTC.")
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

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

    inner class JanusMedia(val channel: ChannelId, val edgeRoute: EdgeRoute) {

        val isActive get() = coroutineContext.isActive && !closed

        private val TAG = "JanusMedia"

        private val coroutineContext = SupervisorJob() + Dispatchers.IO
        private val coroutineScope = CoroutineScope(coroutineContext)

        private var session: JanusFtlSession? = null
        private var connection: JanusRtcConnection? = null
        private var videoTrack: VideoTrack? = null

        @Volatile
        private var closed = false

        init {
            Log.d(TAG, "Starting, channel:${channel.id}")
            loadVideoPreviewUri(viewModel.thumbnailUri.value)

            coroutineScope.launch {
                val ses = JanusFtlSession.create(edgeRoute.url.toHttpUrlOrNull()!!, channel)
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
            coroutineScope.launch(Dispatchers.Main) {
                if (closed) {
                    Log.d(TAG, "Already closed, channel:${channel.id}")
                    return@launch
                }

                Log.d(TAG, "Closing, channel:${channel.id}")

                closed = true

                // Cleanup
                connection?.close()
                videoTrack?.removeSink(proxyVideoSink)
                session?.destroy()

                this@JanusMedia.coroutineContext.cancel()
            }
        }

        private fun loadVideoPreviewUri(uri: Uri?) {
            Log.d("ChannelActivity", "loadVideoPreviewUri: $uri")
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
        @Volatile
        var waitingForFirstFrame = true
        override fun onFrame(frame: VideoFrame) {
            sink.onFrame(frame)

            // This is a form of double-checked locking as a performance optimization
            if (waitingForFirstFrame) {
                runOnUiThread {
                    if (waitingForFirstFrame) {
                        Log.d(TAG, "gotFirstFrame")
                        waitingForFirstFrame = false
                        binding.videoPreview.visibility = View.GONE
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }

        fun showPreviewAndProgressBar() {
            runOnUiThread {
                Log.d(TAG, "showPreviewAndProgressBar")
                binding.videoView.clearImage()
                binding.videoPreview.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
                waitingForFirstFrame = true
            }
        }

        fun showProgressBarOnly() {
            runOnUiThread {
                Log.d(TAG, "showProgressBarOnly")
                binding.videoView.clearImage()
                binding.progressBar.visibility = View.VISIBLE
                waitingForFirstFrame = true
            }
        }
    }
}
