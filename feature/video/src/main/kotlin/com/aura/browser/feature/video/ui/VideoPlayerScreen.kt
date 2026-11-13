package com.aura.browser.feature.video.ui

import kotlinx.coroutines.delay
import com.aura.browser.core.permissions.AuraPermissionManager
import com.aura.browser.core.permissions.AuraPermissionGroup
import com.aura.browser.core.designsystem.component.ImmersiveModeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import android.content.pm.ActivityInfo
import android.content.Intent
import android.content.ContextWrapper
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.PlayerView
import com.aura.browser.core.database.dao.VideoPlaybackPositionDao
import com.aura.browser.core.database.entity.VideoPlaybackPositionEntity
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.core.mediacommon.controller.AuraMediaControllerManager
import com.aura.browser.core.mediacommon.model.LocalVideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val mediaControllerManager: AuraMediaControllerManager,
    private val positionDao: VideoPlaybackPositionDao,
    private val permissionManager: AuraPermissionManager,
) : ViewModel() {

    private val _hasVideoAccess = MutableStateFlow(permissionManager.isPermissionGroupGranted(AuraPermissionGroup.VIDEO_MEDIA))
    val hasVideoAccess: StateFlow<Boolean> = _hasVideoAccess.asStateFlow()

    fun missingPermissions(): Array<String> =
        permissionManager.permissionsToRequest(AuraPermissionGroup.VIDEO_MEDIA)

    fun onPermissionsResult() {
        _hasVideoAccess.value = permissionManager.isPermissionGroupGranted(AuraPermissionGroup.VIDEO_MEDIA)
        scanDeviceVideos()
    }

    private val _videos = MutableStateFlow<List<LocalVideoItem>>(emptyList())
    val videos: StateFlow<List<LocalVideoItem>> = _videos.asStateFlow()

    private val _selectedVideo = MutableStateFlow<LocalVideoItem?>(null)
    val selectedVideo: StateFlow<LocalVideoItem?> = _selectedVideo.asStateFlow()

    val availableSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    init {
        mediaControllerManager.connect()
        scanDeviceVideos()
    }

    fun scanDeviceVideos() {
        if (!permissionManager.isPermissionGroupGranted(AuraPermissionGroup.VIDEO_MEDIA)) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val list = mutableListOf<LocalVideoItem>()
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.WIDTH,
                    MediaStore.Video.Media.HEIGHT,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.SIZE,
                )
                runCatching {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${MediaStore.Video.Media.DATE_ADDED} DESC",
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id,
                            ).toString()
                            val savedPos = positionDao.getPosition(uri)
                            list.add(
                                LocalVideoItem(
                                    id = id.toString(),
                                    uri = uri,
                                    title = cursor.getString(titleCol) ?: "Local Video",
                                    durationMs = cursor.getLong(durCol),
                                    width = cursor.getInt(wCol).takeIf { it > 0 } ?: 1920,
                                    height = cursor.getInt(hCol).takeIf { it > 0 } ?: 1080,
                                    mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                                    sizeBytes = cursor.getLong(sizeCol),
                                    lastPositionMs = savedPos?.positionMs ?: 0L,
                                    subtitleUri = savedPos?.subtitleUri,
                                )
                            )
                        }
                    }
                }
                list
            }
            _videos.value = loaded
        }
    }

    fun openSafVideoUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val saved = positionDao.getPosition(uri.toString())
            val item = LocalVideoItem(
                id = uri.toString(),
                uri = uri.toString(),
                title = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected Video",
                durationMs = saved?.durationMs ?: 0L,
                lastPositionMs = saved?.positionMs ?: 0L,
                subtitleUri = saved?.subtitleUri,
            )
            _selectedVideo.value = item
            mediaControllerManager.playVideoItem(item)
        }
    }

    fun attachSubtitleUri(subtitleUri: android.net.Uri) {
        val current = _selectedVideo.value ?: return
        val updated = current.copy(
            subtitleUri = subtitleUri.toString(),
            lastPositionMs = mediaControllerManager.playbackState.value.positionMs,
        )
        _selectedVideo.value = updated
        mediaControllerManager.playVideoItem(updated)
    }

    fun playVideo(item: LocalVideoItem) {
        _selectedVideo.value = item
        mediaControllerManager.playVideoItem(item)
    }

    fun saveResumeCheckpoint() {
        val current = _selectedVideo.value ?: return
        val playback = mediaControllerManager.playbackState.value
        viewModelScope.launch {
            positionDao.savePosition(
                VideoPlaybackPositionEntity(
                    mediaUri = current.uri,
                    title = current.title,
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    playbackSpeed = playback.playbackSpeed,
                    subtitleUri = current.subtitleUri,
                )
            )
        }
    }
}

@Composable
fun VideoPlayerScreen(
    onEnterPip: () -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val playbackState by viewModel.mediaControllerManager.playbackState.collectAsStateWithLifecycle()
    val controller by viewModel.mediaControllerManager.controller.collectAsStateWithLifecycle()
    val hasVideoAccess by viewModel.hasVideoAccess.collectAsStateWithLifecycle()
    var isFullscreenLandscape by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionsResult() }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Keep access across process death so "resume" keeps working for picked files.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.openSafVideoUri(uri)
        }
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.attachSubtitleUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        val missing = viewModel.missingPermissions()
        if (missing.isNotEmpty()) permissionLauncher.launch(missing) else viewModel.scanDeviceVideos()
    }

    // Resume checkpoint: every few seconds while playing, and when leaving the screen.
    LaunchedEffect(selectedVideo?.id) {
        while (true) {
            delay(5_000L)
            if (viewModel.mediaControllerManager.playbackState.value.isPlaying) viewModel.saveResumeCheckpoint()
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.saveResumeCheckpoint() }
    }

    BackHandler(enabled = isFullscreenLandscape) { isFullscreenLandscape = false }
    ImmersiveModeEffect(active = isFullscreenLandscape)
    DisposableEffect(isFullscreenLandscape) {
        val activity = context.findActivity()
        activity?.requestedOrientation = if (isFullscreenLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        if (!isFullscreenLandscape) {
            GlassTopBar(
                title = "Video",
                subtitle = "${videos.size} video(s) • double-tap to seek",
                actions = {
                    IconButton(onClick = onEnterPip) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Enter Picture-in-Picture",
                            tint = AuraColors.PrimaryAccent,
                        )
                    }
                }
            )
        }

        // Media3 PlayerView Container with Double-Tap Seek Gestures
        Box(
            modifier = (
                if (isFullscreenLandscape) Modifier.fillMaxSize()
                else Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                .clip(RoundedCornerShape(if (isFullscreenLandscape) 0.dp else 22.dp))
                .background(AuraColors.ElevatedBackground)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val isRightHalf = offset.x > size.width / 2f
                            if (isRightHalf) {
                                viewModel.mediaControllerManager.seekRelative(10_000L)
                            } else {
                                viewModel.mediaControllerManager.seekRelative(-10_000L)
                            }
                        }
                    )
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = true
                    }
                },
                update = { playerView ->
                    if (playerView.player !== controller) playerView.player = controller
                },
                onRelease = { playerView -> playerView.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!isFullscreenLandscape) LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!hasVideoAccess) {
                item {
                    GlassCard(borderColor = AuraColors.WarningAmber) {
                        Text(text = "Allow access to your videos", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aura needs permission to list videos on this device. You can still open a single file below.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GlassPillButton(
                            text = "Allow access",
                            isPrimary = true,
                            onClick = { permissionLauncher.launch(viewModel.missingPermissions()) },
                        )
                    }
                }
            }

            // Controls Bar: Seek -10s/+10s, Subtitles, Fullscreen, PiP
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.mediaControllerManager.seekRelative(-10_000L) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = AuraColors.TextPrimary)
                        }
                        IconButton(onClick = { viewModel.mediaControllerManager.seekRelative(10_000L) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = AuraColors.TextPrimary)
                        }
                        GlassPillButton(
                            text = "Subtitles",
                            icon = Icons.Default.Subtitles,
                            onClick = { subtitlePickerLauncher.launch(arrayOf("*/*")) },
                        )
                        IconButton(onClick = { isFullscreenLandscape = !isFullscreenLandscape }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = AuraColors.PrimaryAccent,
                            )
                        }
                        IconButton(onClick = onEnterPip) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = AuraColors.SecondaryCyan,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AuraColors.PrimaryAccent,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(viewModel.availableSpeeds) { speed ->
                                GlassPillButton(
                                    text = "${speed}x",
                                    isPrimary = playbackState.playbackSpeed == speed,
                                    onClick = { viewModel.mediaControllerManager.setPlaybackSpeed(speed) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GlassPillButton(
                        text = "Open video file",
                        icon = Icons.Default.VideoFile,
                        isPrimary = true,
                        onClick = {
                            videoPickerLauncher.launch(
                                arrayOf("video/mp4", "video/x-matroska", "video/webm", "video/*")
                            )
                        },
                    )
                }
            }

            item {
                Text(
                    text = "On this device (${videos.size})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            items(videos, key = { it.id }) { video ->
                GlassCard(
                    isHighlighted = selectedVideo?.id == video.id,
                    onClick = { viewModel.playVideo(video) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = AuraColors.PrimaryAccent,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${video.width}x${video.height} • ${video.mimeType} • Resume: ${video.lastPositionMs / 1000}s",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
