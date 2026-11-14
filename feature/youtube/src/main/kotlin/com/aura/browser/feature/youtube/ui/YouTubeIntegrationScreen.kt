package com.aura.browser.feature.youtube.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.datastore.AuraUserSettings
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeSettingsViewModel @Inject constructor(
    private val preferencesDataSource: AuraPreferencesDataSource,
) : ViewModel() {

    val settings: StateFlow<AuraUserSettings> = preferencesDataSource.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuraUserSettings())

    fun setBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch { preferencesDataSource.setYoutubeBackgroundPlay(enabled) }
    }

    fun setHideShorts(enabled: Boolean) {
        viewModelScope.launch { preferencesDataSource.setYoutubeHideShorts(enabled) }
    }

}

@Composable
fun YouTubeIntegrationScreen(
    onOpenInBrowser: (String) -> Unit,
    onRequestPip: () -> Unit,
    viewModel: YouTubeSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "YouTube settings",
            subtitle = "Open links • Background play • Picture-in-Picture",
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                GlassCard(isHighlighted = true) {
                    Text(
                        text = "Open in YouTube",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pick a shortcut or paste a YouTube link. Links shared from other apps open here automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GlassPillButton(
                            text = "YouTube Home",
                            icon = Icons.Default.PlayArrow,
                            isPrimary = true,
                            accentColor = AuraColors.YouTubeRed,
                            onClick = {
                                onOpenInBrowser("https://m.youtube.com")
                            },
                        )
                        GlassPillButton(
                            text = "Subscriptions",
                            icon = Icons.Default.Subscriptions,
                            onClick = {
                                val url = "https://m.youtube.com/feed/subscriptions"
                                onOpenInBrowser(url)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GlassPillButton(
                            text = "YouTube Music",
                            icon = Icons.Default.Headphones,
                            onClick = {
                                val url = "https://music.youtube.com"
                                onOpenInBrowser(url)
                            },
                        )
                        GlassPillButton(
                            text = "Google Sign-In",
                            icon = Icons.Default.AccountCircle,
                            onClick = {
                                val url = "https://accounts.google.com/ServiceLogin?service=youtube"
                                onOpenInBrowser(url)
                            },
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Playback",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "In fullscreen: swipe up/down on the left for brightness, on the right for volume, and pinch to zoom.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingToggleRow(
                        title = "Keep playing in the background",
                        subtitle = "Audio continues when you switch apps or turn the screen off.",
                        checked = settings.youtubeBackgroundPlayEnabled,
                        onCheckedChange = viewModel::setBackgroundPlay,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingToggleRow(
                        title = "Hide Shorts",
                        subtitle = "Best-effort: hides Shorts shelves on youtube.com. Applies to pages you open next.",
                        checked = settings.youtubeHideShortsShelf,
                        onCheckedChange = viewModel::setHideShorts,
                    )
                }
            }

            item {
                GlassCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = null,
                            tint = AuraColors.PrimaryAccent,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Picture-in-Picture",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Keep the video in a small floating window while you use other apps.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        GlassPillButton(
                            text = "Start",
                            onClick = onRequestPip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
