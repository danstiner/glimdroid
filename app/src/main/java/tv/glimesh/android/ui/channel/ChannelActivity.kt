package tv.glimesh.android.ui.channel

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import tv.glimesh.android.R
import tv.glimesh.android.data.model.ChannelId
import tv.glimesh.android.data.model.StreamId
import tv.glimesh.android.databinding.ActivityChannelBinding
import java.util.*


const val smashbetsChannelId = 10552L

const val CHANNEL_ID = "tv.glimesh.android.extra.channel.id"
const val STREAM_ID = "tv.glimesh.android.extra.stream.id"
const val STREAM_THUMBNAIL_URL = "tv.glimesh.android.extra.stream.url"

@RequiresApi(Build.VERSION_CODES.O)
class ChannelActivity : AppCompatActivity() {

    private val TAG = "ChannelActivity"

    private lateinit var viewModel: ChannelViewModel
    private lateinit var binding: ActivityChannelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChannelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val origStreamThumbnailUrl =
            intent.getStringExtra(STREAM_THUMBNAIL_URL)?.let { Uri.parse(it) }
        origStreamThumbnailUrl?.let {
            Log.d(TAG, "Stream thumbnail url: $it")
            Glide
                .with(this)
                .load(it)
                .onlyRetrieveFromCache(true)
                .into(binding.videoPreview)
        }

        val eglBase = EglBase.create()

        viewModel = ViewModelProvider(
            this,
            ChannelViewModelFactory(applicationContext, eglBase.eglBaseContext)
        )[ChannelViewModel::class.java]

        binding.videoView.init(eglBase.eglBaseContext, null)
        binding.videoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.videoView.setEnableHardwareScaler(true)
        binding.videoView.setZOrderOnTop(true);
        binding.videoView.holder.setFormat(PixelFormat.TRANSPARENT);

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
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
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://glimesh.tv/").buildUpon()
                            .appendPath(username)
                            .appendPath("profile").build()
                    )
                )
            }
            binding.avatarImage.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://glimesh.tv/").buildUpon()
                            .appendPath(username)
                            .appendPath("profile").build()
                    )
                )
            }
            binding.buttonSupport.setOnClickListener {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://glimesh.tv/").buildUpon()
                            .appendPath(username)
                            .appendPath("support").build()
                    )
                )
            }
        })
        viewModel.streamerAvatarUrl.observe(this, {
            if (it != null) {
                Glide
                    .with(this)
                    .load(it)
                    .fitCenter()
                    .circleCrop()
                    .into(binding.avatarImage)
            } else {
                Glide.with(this).clear(binding.avatarImage)
            }
        })
//        viewModel.viewerCount.observe(this, {
//            if (it != null) {
//                binding.textviewChannelSubtitle.text = "$it viewers"
//            } else {
//                binding.textviewChannelSubtitle.text = "Not live"
//            }
//        })
        viewModel.streamerAvatarUrl.observe(this, {
            if (it != null) {
                Glide
                    .with(this)
                    .load(it)
                    .fitCenter()
                    .circleCrop()
                    .into(binding.avatarImage)
            } else {
                Glide.with(this).clear(binding.avatarImage)
            }
        })
        viewModel.videoThumbnailUrl.observe(this, {
            val newWithoutQuery = Uri.parse(it.toString()).buildUpon().clearQuery().build()
            val origWithoutQuery = origStreamThumbnailUrl?.buildUpon()?.clearQuery()?.build()
            when {
                newWithoutQuery == origWithoutQuery -> {
                    return@observe
                }
                it != null -> {
                    Glide
                        .with(this)
                        .load(it)
                        .onlyRetrieveFromCache(true)
                        .into(binding.videoPreview)
                }
                else -> {
                    Glide.with(this).clear(binding.videoPreview)
                }
            }
        })
        viewModel.videoTrack.observe(this, {
            it.addSink(binding.videoView)
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

        binding.chatInputEditText.setOnEditorActionListener { textView, id, _ ->
            when (id) {
                EditorInfo.IME_ACTION_DONE -> {
                    viewModel.sendMessage(textView.text)
                    textView.text = ""
                    true
                }
                else -> false
            }
        }

        // Ensure sourceRectHint is updated so exiting PiP animates smoothly to the original view
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
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
        watch(intent)
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopWatching()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        watch(intent)
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

    private fun watch(intent: Intent?) {
        var channelId = intent?.getLongExtra(CHANNEL_ID, 0) ?: 0
        if (channelId == 0L) {
            Log.w(TAG, "watch: No channel id to watch in given intent")
            viewModel.stopWatching()
            return
        }

        var channel = ChannelId(channelId)
        Log.d(TAG, "onNewIntent: Watching $channel, current channel:${viewModel.currentChannel}")
        if (viewModel.currentChannel != channel) {
            binding.videoView.clearImage()
        }
        viewModel.watch(channel)
    }

    private fun enterPictureInPicture() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            enterPictureInPictureMode(pictureInPictureParams(binding.videoView))
        }
    }

    companion object {
        fun intent(
            context: Context,
            channel: ChannelId,
            stream: StreamId,
            streamThumbnailUrl: Uri? = null
        ) =
            Intent(context, ChannelActivity::class.java).apply {
                putExtra(CHANNEL_ID, channel.id)
                putExtra(STREAM_ID, stream.id)
                streamThumbnailUrl?.let {
                    putExtra(
                        STREAM_THUMBNAIL_URL,
                        streamThumbnailUrl.toString()
                    )
                }
            }
    }
}
