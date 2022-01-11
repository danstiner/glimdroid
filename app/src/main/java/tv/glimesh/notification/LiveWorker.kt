package tv.glimesh.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tv.glimesh.R
import tv.glimesh.data.AuthStateDataSource
import tv.glimesh.data.GlimeshDataSource
import tv.glimesh.data.model.ChannelId
import tv.glimesh.data.model.StreamId
import tv.glimesh.ui.channel.ChannelActivity
import tv.glimesh.ui.home.Channel
import java.util.concurrent.TimeUnit

@Serializable
data class State(val notifications: MutableMap<String, LiveNotification>)

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
@RequiresApi(Build.VERSION_CODES.O)
class LiveWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val STORE_NAME = "LiveWorker"
    private val KEY_STATE = "state"

    private val LIVE_CHANNEL_ID = "LIVE_CHANNEL_ID"

    private var store: SharedPreferences =
        appContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)
    private val glimesh = GlimeshDataSource(AuthStateDataSource(appContext))

    private var state: State = readState()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LIVE_CHANNEL_ID,
                "LIVE_CHANNEL_NAME",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Live Followed Channels"

            getSystemService(appContext, NotificationManager::class.java)!!
                .createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")
        val data = glimesh.myFollowingQuery()
        val channels = data
            ?.myself
            ?.followingLiveChannels
            ?.edges
            ?.mapNotNull { edge -> edge?.node }
            ?.map { node ->
                Channel(
                    id = node.id!!,
                    title = node?.title!!,
                    streamerDisplayName = node?.streamer?.displayname,
                    streamerAvatarUrl = node?.streamer?.avatarUrl,
                    streamId = node?.stream?.id,
                    streamThumbnailUrl = node?.stream?.thumbnailUrl,
                )
            } ?: listOf()

        // Check if any channels stopped being live and should be cleared
        state.notifications.entries.removeIf { entry ->
            val notification = entry.value

            val isLive = channels.any { it.id == notification.channel.id }

            if (!isLive) {
                NotificationManagerCompat.from(applicationContext).cancel(null, notification.id);
                return@removeIf true
            }

            return@removeIf false
        }

        // Check if any channels newly went live
        for (channel in channels) {
            if (channel.id in state.notifications) {
                val notification = state.notifications[channel.id]
                // TODO update notification data if needed
            } else {
                // Show notification
                val notificationId = channel.id.toInt()
                showNotification(notificationId, channel)

                state.notifications[channel.id] = LiveNotification(notificationId, channel)
            }
        }

        writeState(state)

        return Result.success()
    }

    private fun showNotification(notificationId: Int, channel: Channel) {
        val intent = ChannelActivity.intent(
            applicationContext,
            ChannelId(channel.id.toLong()),
            StreamId(channel.streamId?.toLong()!!)
        )
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(applicationContext).run {
            // Add the intent, which inflates the back stack
            addNextIntentWithParentStack(intent)
            // Get the PendingIntent containing the entire back stack
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(applicationContext, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera_black_24dp)
            .setContentTitle("${channel.streamerDisplayName} is live")
            .setContentText(channel.title)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    private fun readState(): State {
        val currentState = store.getString(KEY_STATE, null) ?: return State(mutableMapOf())
        return try {
            Json.decodeFromString(currentState)
        } catch (ex: kotlinx.serialization.SerializationException) {
            Log.w(TAG, "Failed to deserialize stored state - discarding: ${ex.message}")
            State(mutableMapOf())
        }
    }

    private fun writeState(@Nullable state: State?) {
        val editor = store.edit()
        if (state == null) {
            editor.remove(KEY_STATE)
        } else {
            editor.putString(KEY_STATE, Json.encodeToString(state))
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
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val work = PeriodicWorkRequestBuilder<LiveWorker>(
                PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            )
                .setInitialDelay(1, TimeUnit.SECONDS)
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
