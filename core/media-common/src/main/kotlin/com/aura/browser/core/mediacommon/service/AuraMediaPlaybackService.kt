package com.aura.browser.core.mediacommon.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.mediacommon.equalizer.AuraAudioEffectsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Production AndroidX Media3 [MediaSessionService] providing:
 * - Background audio/video playback
 * - Automatic AudioFocus management (C.AUDIO_CONTENT_TYPE_MUSIC)
 * - Becoming noisy (headphone unplug) auto-pause
 * - Lock-screen & notification media controls
 * - Hardware headphone media button handling
 * - Equalizer & BassBoost attachment to active ExoPlayer audioSessionId
 */
@AndroidEntryPoint
class AuraMediaPlaybackService : MediaSessionService() {

    @Inject
    lateinit var audioEffectsController: AuraAudioEffectsController

    @Inject
    lateinit var preferencesDataSource: AuraPreferencesDataSource

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffectsController.attachToAudioSession(audioSessionId)
            }
        })

        audioEffectsController.attachToAudioSession(player.audioSessionId)
        serviceScope.launch {
            val saved = preferencesDataSource.userSettings.first()
            audioEffectsController.restoreSavedConfig(saved.equalizerPresetIndex, saved.bassBoostStrength)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivityPendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val sessionBuilder = MediaSession.Builder(this, player)
        if (sessionActivityPendingIntent != null) {
            sessionBuilder.setSessionActivity(sessionActivityPendingIntent)
        }

        exoPlayer = player
        mediaSession = sessionBuilder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        audioEffectsController.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        super.onDestroy()
    }
}
