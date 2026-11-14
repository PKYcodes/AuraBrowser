package com.aura.browser.feature.browser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.aura.browser.feature.browser.R
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import com.aura.browser.feature.browser.engine.WebMediaInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service shown while a web page plays media.
 *
 * It keeps the browser process at foreground priority (audio continues with the screen off) and publishes a
 * real media notification: a [MediaSessionCompat] plus `MediaStyle`, so the system shows the proper
 * previous / play-pause / next / stop buttons with icons, on the lock screen and with headset buttons too.
 * (A plain notification only shows text labels for its actions on current Android versions.)
 */
@AndroidEntryPoint
class AuraWebMediaService : Service() {

    @Inject
    lateinit var engine: AuraGeckoEngineManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSessionCompat

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        mediaSession = MediaSessionCompat(this, "AuraWebMedia").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = engine.playWebMedia()
                override fun onPause() = engine.pauseWebMedia()
                override fun onStop() = engine.stopWebMedia()
                override fun onSkipToNext() = engine.webNextTrack()
                override fun onSkipToPrevious() = engine.webPreviousTrack()
            })
            isActive = true
        }

        // startForegroundService() requires a notification within a few seconds.
        render(engine.webMedia.value)
        scope.launch {
            engine.webMedia.collect { info ->
                if (info.isActive) {
                    render(info)
                } else {
                    ServiceCompat.stopForeground(this@AuraWebMediaService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.toggleWebMedia()
            ACTION_NEXT -> engine.webNextTrack()
            ACTION_PREVIOUS -> engine.webPreviousTrack()
            ACTION_STOP -> engine.stopWebMedia()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun render(info: WebMediaInfo) {
        updateSession(info)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(info),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun updateSession(info: WebMediaInfo) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.title.ifBlank { "Web media" })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.artist.ifBlank { "Aura Browser" })
                .build()
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (info.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build()
        )
    }

    private fun buildNotification(info: WebMediaInfo): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_web_media)
            .setContentTitle(info.title.ifBlank { "Web media" })
            .setContentText(info.artist.ifBlank { "Aura Browser" })
            .setContentIntent(openApp)
            .setOngoing(info.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_media_previous, "Previous", servicePendingIntent(ACTION_PREVIOUS, 1))
            .addAction(
                if (info.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                if (info.isPlaying) "Pause" else "Play",
                servicePendingIntent(ACTION_TOGGLE, 2),
            )
            .addAction(R.drawable.ic_media_next, "Next", servicePendingIntent(ACTION_NEXT, 3))
            .addAction(R.drawable.ic_media_stop, "Stop", servicePendingIntent(ACTION_STOP, 4))
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AuraWebMediaService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Web media playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for audio and video playing in web pages"
                setShowBadge(false)
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "aura_web_media"
        const val NOTIFICATION_ID = 4101
        const val ACTION_TOGGLE = "com.aura.browser.action.WEB_MEDIA_TOGGLE"
        const val ACTION_NEXT = "com.aura.browser.action.WEB_MEDIA_NEXT"
        const val ACTION_PREVIOUS = "com.aura.browser.action.WEB_MEDIA_PREVIOUS"
        const val ACTION_STOP = "com.aura.browser.action.WEB_MEDIA_STOP"
    }
}
