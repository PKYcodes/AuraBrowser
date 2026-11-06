package com.aura.browser.feature.music.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.database.dao.PlaylistDao
import com.aura.browser.core.database.entity.PlaylistEntity
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.core.mediacommon.controller.AuraMediaControllerManager
import com.aura.browser.core.mediacommon.equalizer.AuraAudioEffectsController
import com.aura.browser.core.mediacommon.model.LocalAudioTrack
import com.aura.browser.core.mediacommon.model.RepeatModeOption
import com.aura.browser.feature.music.scanner.LocalAudioScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val scanner: LocalAudioScanner,
    val mediaControllerManager: AuraMediaControllerManager,
    val audioEffectsController: AuraAudioEffectsController,
    private val playlistDao: PlaylistDao,
    private val preferencesDataSource: AuraPreferencesDataSource,
) : ViewModel() {

    private val _tracks = MutableStateFlow<List<LocalAudioTrack>>(emptyList())
    val tracks: StateFlow<List<LocalAudioTrack>> = _tracks.asStateFlow()

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        mediaControllerManager.connect()
        scanMediaStoreLibrary()
    }

    fun scanMediaStoreLibrary() {
        viewModelScope.launch {
            val scanned = scanner.scanDeviceAudioLibrary()
            if (scanned.isNotEmpty()) {
                _tracks.value = scanned
            }
        }
    }

    fun importSafDirectory(treeUri: android.net.Uri) {
        viewModelScope.launch {
            preferencesDataSource.setSafMusicTreeUri(treeUri.toString())
            val safTracks = scanner.scanSafDirectoryTree(treeUri)
            val combined = (_tracks.value + safTracks).distinctBy { it.uri }
            _tracks.value = combined
        }
    }

    fun createQuickPlaylistFromQueue(name: String) {
        viewModelScope.launch {
            val uris = _tracks.value.joinToString(",") { it.uri }
            playlistDao.upsertPlaylist(PlaylistEntity(name = name, trackUrisCsv = uris))
        }
    }

    fun selectEqualizerPreset(index: Int) {
        audioEffectsController.selectPreset(index)
        viewModelScope.launch {
            preferencesDataSource.setEqualizerConfig(
                presetIndex = index,
                bassStrength = audioEffectsController.state.value.bassBoostStrength,
            )
        }
    }

    fun setBassStrength(strength: Int) {
        audioEffectsController.setBassBoostStrength(strength)
        viewModelScope.launch {
            preferencesDataSource.setEqualizerConfig(
                presetIndex = audioEffectsController.state.value.selectedPresetIndex,
                bassStrength = strength,
            )
        }
    }
}

@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playbackState by viewModel.mediaControllerManager.playbackState.collectAsStateWithLifecycle()
    val eqState by viewModel.audioEffectsController.state.collectAsStateWithLifecycle()
    var showEqualizerPanel by remember { mutableStateOf(false) }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importSafDirectory(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scanMediaStoreLibrary()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Aura Music Studio",
            subtitle = "Media3 ExoPlayer • Background MediaSessionService",
            actions = {
                IconButton(onClick = { showEqualizerPanel = !showEqualizerPanel }) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer & Bass Boost",
                        tint = if (showEqualizerPanel) AuraColors.PrimaryAccent else AuraColors.TextPrimary,
                    )
                }
                IconButton(onClick = { viewModel.scanMediaStoreLibrary() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan Audio Files",
                        tint = AuraColors.TextPrimary,
                    )
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Now Playing Glass Deck
            item {
                GlassCard(isHighlighted = true, cornerRadius = 24.dp) {
                    Text(
                        text = playbackState.currentTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playbackState.currentArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val totalDuration = playbackState.durationMs.coerceAtLeast(1L)
                    val progressFraction = (playbackState.positionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            viewModel.mediaControllerManager.seekTo((fraction * totalDuration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = AuraColors.PrimaryAccent,
                            activeTrackColor = AuraColors.PrimaryAccent,
                            inactiveTrackColor = AuraColors.ElevatedBackground,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDurationMs(playbackState.positionMs),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = formatDurationMs(playbackState.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.mediaControllerManager.toggleShuffle() }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                            )
                        }
                        IconButton(onClick = { viewModel.mediaControllerManager.skipPrevious() }) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = AuraColors.TextPrimary,
                            )
                        }
                        GlassPillButton(
                            text = if (playbackState.isPlaying) "Pause" else "Play",
                            icon = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            isPrimary = true,
                            onClick = { viewModel.mediaControllerManager.togglePlayPause() },
                        )
                        IconButton(onClick = { viewModel.mediaControllerManager.skipNext() }) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = AuraColors.TextPrimary,
                            )
                        }
                        IconButton(onClick = { viewModel.mediaControllerManager.cycleRepeatMode() }) {
                            Icon(
                                imageVector = if (playbackState.repeatMode == RepeatModeOption.ONE)
                                    Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = playbackState.repeatMode.label,
                                tint = if (playbackState.repeatMode != RepeatModeOption.OFF)
                                    AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                            )
                        }
                    }
                }
            }

            // Equalizer & BassBoost Panel
            if (showEqualizerPanel) {
                item {
                    GlassCard {
                        Text(
                            text = "Hardware AudioFX Equalizer & Bass Boost",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(eqState.presets) { idx, presetName ->
                                GlassPillButton(
                                    text = presetName,
                                    isPrimary = eqState.selectedPresetIndex == idx,
                                    onClick = { viewModel.selectEqualizerPreset(idx) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Bass Boost Strength: ${eqState.bassBoostStrength / 10}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Slider(
                            value = eqState.bassBoostStrength / 1000f,
                            onValueChange = { viewModel.setBassStrength((it * 1000).toInt()) },
                        )
                    }
                }
            }

            // SAF Folder & Playlist Actions
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GlassPillButton(
                        text = "Select Music Folder (SAF)",
                        icon = Icons.Default.FolderOpen,
                        isPrimary = true,
                        onClick = { safFolderLauncher.launch(null) },
                    )
                    GlassPillButton(
                        text = "Save Queue Playlist (${playlists.size})",
                        icon = Icons.Default.Add,
                        onClick = {
                            viewModel.createQuickPlaylistFromQueue("Favorites #${playlists.size + 1}")
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Local Audio Tracks (${tracks.size})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (tracks.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "No audio tracks indexed yet. Tap 'Select Music Folder (SAF)' to grant scoped folder access or place MP3/FLAC/M4A files in your Music folder.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                itemsIndexed(tracks, key = { _, item -> item.id }) { index, track ->
                    GlassCard(
                        onClick = { viewModel.mediaControllerManager.playAudioQueue(tracks, index) },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = AuraColors.PrimaryAccent,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = formatDurationMs(track.durationMs),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
