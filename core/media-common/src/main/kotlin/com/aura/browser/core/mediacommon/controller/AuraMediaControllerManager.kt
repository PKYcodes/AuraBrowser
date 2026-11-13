package com.aura.browser.core.mediacommon.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    /** True when the current item has a selected video track (as opposed to audio-only music). */
    val isVideo: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
)

/**
 * App-wide connection to [AuraMediaPlaybackService]. Commands issued before the asynchronous
 * connection completes are queued and executed once the controller is available.
 */
@Singleton
class AuraMediaControllerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()
    private var positionTicker: Job? = null

    private val _controller = MutableStateFlow<MediaController?>(null)

    /** Emits the connected controller (or `null`); UI binds players to this. */
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    val mediaController: MediaController? get() = _controller.value

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
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    controllerFuture = null
                    pendingActions.clear()
                    return@addListener
                }
                connected.addListener(playerListener)
                _controller.value = connected
                syncState(connected)
                if (connected.isPlaying) startPositionTicker()
                while (pendingActions.isNotEmpty()) {
                    pendingActions.removeFirst().invoke(connected)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun runWhenConnected(action: (MediaController) -> Unit) {
        val connected = _controller.value
        if (connected != null) {
            action(connected)
        } else {
            pendingActions.addLast(action)
            connect()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startPositionTicker() else positionTicker?.cancel()
        }
    }

    /** Advances the position while playing; Media3 only reports discrete events, not progress. */
    private fun startPositionTicker() {
        positionTicker?.cancel()
        positionTicker = scope.launch {
            while (isActive) {
                delay(POSITION_TICK_MS)
                _controller.value?.let(::syncState)
            }
        }
    }

    private fun syncState(player: Player) {
        val metadata = player.mediaMetadata
        val repeatOption = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
            Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
            else -> RepeatModeOption.OFF
        }
        val videoSize = player.videoSize
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
            isVideo = player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO),
            videoWidth = videoSize.width,
            videoHeight = videoSize.height,
        )
    }

    fun playAudioQueue(tracks: List<LocalAudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        runWhenConnected { controller ->
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
            controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            controller.prepare()
            controller.play()
            syncState(controller)
        }
    }

    fun playVideoItem(video: LocalVideoItem) {
        runWhenConnected { controller ->
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
                val subtitleUri = Uri.parse(video.subtitleUri)
                val resolverType = runCatching { context.contentResolver.getType(subtitleUri) }.getOrNull()
                val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(detectSubtitleMimeType(video.subtitleUri, resolverType))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                builder.setSubtitleConfigurations(listOf(subtitle))
            }
            controller.setMediaItem(builder.build(), video.lastPositionMs.coerceAtLeast(0L))
            controller.prepare()
            controller.play()
            syncState(controller)
        }
    }

    fun togglePlayPause() = withController { controller ->
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun pause() = withController { it.pause() }

    fun stop() = withController { it.stop() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }

    fun seekRelative(deltaMs: Long) = withController { controller ->
        val duration = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        controller.seekTo((controller.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun skipNext() = withController { controller ->
        if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
    }

    fun skipPrevious() = withController { controller ->
        if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem() else controller.seekTo(0L)
    }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeatMode() = withController { controller ->
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(speed: Float) = withController {
        it.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3.0f))
    }

    fun release() {
        positionTicker?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _controller.value = null
        pendingActions.clear()
    }

    private inline fun withController(block: (MediaController) -> Unit) {
        val controller = _controller.value ?: return
        block(controller)
        syncState(controller)
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}

/** Picks the Media3 subtitle MIME type from the content resolver's answer or the file extension. */
fun detectSubtitleMimeType(uri: String, resolverType: String?): String {
    when (resolverType?.lowercase()) {
        MimeTypes.TEXT_VTT -> return MimeTypes.TEXT_VTT
        MimeTypes.APPLICATION_SUBRIP, "text/srt" -> return MimeTypes.APPLICATION_SUBRIP
        MimeTypes.APPLICATION_TTML -> return MimeTypes.APPLICATION_TTML
    }
    return when (uri.substringBefore('?').substringAfterLast('.', "").lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}
