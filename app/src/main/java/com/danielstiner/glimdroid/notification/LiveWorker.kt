package com.danielstiner.glimdroid.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.danielstiner.glimdroid.R
import com.danielstiner.glimdroid.data.AuthStateDataSource
import com.danielstiner.glimdroid.data.GlimeshDataSource
import com.danielstiner.glimdroid.data.model.Channel
import com.danielstiner.glimdroid.ui.channel.ChannelActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Serializable
data class State(val notifications: MutableMap<String, LiveNotification>) {
    fun encodeToString() = Json.encodeToString(this)

    companion object {
        fun decodeFromString(string: String) = Json.decodeFromString<State>(string)
    }
}

@Serializable
data class LiveNotification(val id: Int, val channel: Channel)

/**
 * Periodically check if any followed channels have gone live recently. Runs at most once
 * every 15 minutes which is the minimum periodic background task interval on android.
 * https://developer.android.com/topic/libraries/architecture/workmanager
 *
 * Should be eventually replaced by a persistent websocket, or better yet a push notification
 * using something like: https://firebase.google.com/docs/cloud-messaging
 */
class LiveWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val STORE_NAME = "LiveWorker"
    private val KEY_STATE = "state"

    private val LIVE_CHANNEL_ID = "LIVE_CHANNEL_ID"
    private val LIVE_CHANNEL_NAME =
        appContext.resources.getString(R.string.live_channel_notification_name)
    private val LIVE_CHANNEL_DESCRIPTION =
        appContext.resources.getString(R.string.live_channel_notification_description)

    private var auth = AuthStateDataSource.getInstance(appContext)
    private var store: SharedPreferences =
        appContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val glimesh = GlimeshDataSource(AuthStateDataSource(appContext))

    private var state: State = readState()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(appContext, NotificationManager::class.java)!!
                .createNotificationChannel(NotificationChannel(
                    LIVE_CHANNEL_ID,
                    LIVE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = LIVE_CHANNEL_DESCRIPTION
                })
        }
    }

    override suspend fun doWork(): Result {
        if (!auth.getCurrent().isAuthorized) {
            Log.d(TAG, "Not authorized, skipping work")
            return Result.success()
        }

        Log.d(TAG, "doWork")

        val liveChannels = glimesh.myFollowedLiveChannels()

        // Check if any channels stopped being live and should be cleared
        state.notifications.entries.removeIf { entry ->
            val notification = entry.value

            val isLive = liveChannels.any { it.id == notification.channel.id }

            if (!isLive) {
                NotificationManagerCompat.from(applicationContext).cancel(null, notification.id);
                return@removeIf true
            }

            return@removeIf false
        }

        // Check if any channels newly went live
        for (channel in liveChannels) {
            val id = channel.id.id.toString()
            if (id in state.notifications) {
                val notification = state.notifications[id]
                // TODO update notification data if needed
            } else {
                // Show notification
                val notificationId = channel.id.id.toInt()
                showNotification(notificationId, channel)
                state.notifications[id] = LiveNotification(notificationId, channel)
            }
        }

        writeState(state)

        return Result.success()
    }

    private suspend fun showNotification(notificationId: Int, channel: Channel) {
        // Build intent to launch to the channel activity when notification is clicked
        val intent = ChannelActivity.intent(
            applicationContext,
            channel.id,
            channel.stream!!.id
        )
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(applicationContext).run {
            // Add the intent, which inflates the back stack
            addNextIntentWithParentStack(intent)
            // Get the PendingIntent containing the entire back stack
            getPendingIntent(
                notificationId,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Build notification itself and display it
        val notification = NotificationCompat.Builder(applicationContext, LIVE_CHANNEL_ID)
            .setContentTitle("${channel.streamer.displayName} is live")
            .setContentText(channel.title)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_status_bar)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification
                .setColor(applicationContext.getColor(R.color.md_theme_light_primary))
                .setColorized(true)
        }

        NotificationManagerCompat.from(applicationContext)
            .notify(notificationId, notification.build())

        // Load avatar icon and replace large icon with it
        channel.streamer.avatarUrl?.let { avatarUrl ->
            val avatarBitmap = loadAvatarBitmap(Uri.parse(avatarUrl))
            Log.d(TAG, "Loaded large notification icon")
            NotificationManagerCompat.from(applicationContext)
                .notify(notificationId, notification.setLargeIcon(avatarBitmap).build())
        }
    }

    private suspend fun loadAvatarBitmap(uri: Uri): Bitmap {
        return suspendCoroutine { continuation ->
            Glide.with(applicationContext)
                .asBitmap()
                .load(uri)
                .circleCrop()
                .into(object : CustomTarget<Bitmap?>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap?>?
                    ) {
                        continuation.resume(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    private fun readState(): State {
        val stateString = store.getString(KEY_STATE, null)
        try {
            if (stateString != null) {
                return State.decodeFromString(stateString)
            }
        } catch (ex: kotlinx.serialization.SerializationException) {
            Log.w(TAG, "Failed to deserialize stored state - discarding", ex)
        } catch (ex: java.lang.ClassCastException) {
            Log.w(TAG, "Failed to deserialize stored state - discarding", ex)
        }

        return State(mutableMapOf())
    }

    private fun writeState(@Nullable state: State?) {
        val editor = store.edit()
        if (state == null) {
            editor.remove(KEY_STATE)
        } else {
            editor.putString(KEY_STATE, state.encodeToString())
        }
        check(editor.commit()) { "Failed to write state to shared prefs" }
    }

    companion object {
        private const val TAG = "LiveWorker"

        /**
         * Start periodic background work request to check for live followed channels.
         */
        @JvmStatic
        fun start(context: Context) {
            Log.d(TAG, "start")
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val work = PeriodicWorkRequestBuilder<LiveWorker>(
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            )
                .setInitialDelay(60, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniquePeriodicWork(
                "liveChannelCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            );
        }
    }
}
