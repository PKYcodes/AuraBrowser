package com.aura.browser.core.mediacommon.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.browser.core.mediacommon.model.LocalAudioTrack
import com.aura.browser.core.mediacommon.model.LocalVideoItem
import com.aura.browser.core.mediacommon.model.RepeatModeOption
import com.aura.browser.core.mediacommon.service.AuraMediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTitle: String = "Nothing Playing",
    val currentArtist: String = "Select a track or video",
    val currentMediaUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val playbackSpeed: Float = 1.0f,
)

@Singleton
class AuraMediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AuraMediaPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                mediaController = controller
                controller.addListener(playerListener)
                syncState(controller)
            },
            MoreExecutors.directExecutor(),
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
        }
    }

    private fun syncState(player: Player) {
        val metadata = player.mediaMetadata
        val repeatOption = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
            Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
            else -> RepeatModeOption.OFF
        }
        _playbackState.value = PlaybackUiState(
            isConnected = true,
            isPlaying = player.isPlaying,
            currentTitle = metadata.title?.toString() ?: "Aura Media",
            currentArtist = metadata.artist?.toString() ?: "Local Library",
            currentMediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = repeatOption,
            playbackSpeed = player.playbackParameters.speed,
        )
    }

    fun playAudioQueue(tracks: List<LocalAudioTrack>, startIndex: Int = 0) {
        connect()
        val controller = mediaController ?: return
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build()
                )
                .build()
        }
        controller.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        controller.prepare()
        controller.play()
        syncState(controller)
    }

    fun playVideoItem(video: LocalVideoItem) {
        connect()
        val controller = mediaController ?: return
        val builder = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(Uri.parse(video.uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .setArtist("Local Video")
                    .build()
            )
        if (!video.subtitleUri.isNullOrBlank()) {
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(video.subtitleUri))
                .setMimeType("text/vtt")
                .setLanguage("en")
                .build()
            builder.setSubtitleConfigurations(listOf(subtitleConfig))
        }
        controller.setMediaItem(builder.build(), video.lastPositionMs.coerceAtLeast(0L))
        controller.prepare()
        controller.play()
        syncState(controller)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
        syncState(controller)
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        syncState(controller)
    }

    fun seekRelative(deltaMs: Long) {
        val controller = mediaController ?: return
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(target)
        syncState(controller)
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
        syncState(controller)
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem() else controller.seekTo(0L)
        syncState(controller)
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        syncState(controller)
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        syncState(controller)
    }

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3.0f))
        syncState(controller)
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
