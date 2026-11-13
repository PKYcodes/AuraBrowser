package com.aura.browser.feature.browser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
 * Foreground service shown while a web page is playing media. It exists so Android keeps the browser
 * process at foreground priority (audio continues with the screen off) and to offer play/pause/stop
 * controls in the notification shade.
 */
@AndroidEntryPoint
class AuraWebMediaService : Service() {

    @Inject
    lateinit var engine: AuraGeckoEngineManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // startForegroundService() requires a notification within a few seconds.
        promoteToForeground(engine.webMedia.value)
        scope.launch {
            engine.webMedia.collect { info ->
                if (info.isActive) {
                    promoteToForeground(info)
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
            ACTION_STOP -> engine.stopWebMedia()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(info: WebMediaInfo) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(info),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun buildNotification(info: WebMediaInfo): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val toggle = servicePendingIntent(ACTION_TOGGLE, 1)
        val stop = servicePendingIntent(ACTION_STOP, 2)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (info.isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle(info.title.ifBlank { "Web media" })
            .setContentText(info.artist.ifBlank { "Aura Browser" })
            .setContentIntent(openApp)
            .setOngoing(info.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (info.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (info.isPlaying) "Pause" else "Play",
                toggle,
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
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
        const val ACTION_STOP = "com.aura.browser.action.WEB_MEDIA_STOP"
    }
}
