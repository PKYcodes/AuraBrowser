package com.aura.browser.feature.youtube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.browser.core.designsystem.component.ImmersiveModeEffect
import com.aura.browser.core.designsystem.component.LocalImmersiveMode
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import com.aura.browser.feature.browser.ui.BrowserDialogHost
import com.aura.browser.feature.browser.ui.GeckoSessionView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    val engineManager: AuraGeckoEngineManager,
) : ViewModel()

/**
 * The YouTube tab: YouTube's mobile site shown full screen, like an app, in its own session
 * (separate from browser tabs). Chrome is minimal and disappears in fullscreen video and in
 * Picture-in-Picture; pinch-zoom and brightness/volume swipes work in fullscreen.
 */
@Composable
fun YouTubeScreen(
    onRequestPip: () -> Unit,
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val engine = viewModel.engineManager
    val session by engine.youTubeSession.collectAsStateWithLifecycle()
    val state by engine.youTubeState.collectAsStateWithLifecycle()
    val immersive = LocalImmersiveMode.current
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { engine.openYouTube() }

    ImmersiveModeEffect(active = state.isFullScreen)

    BackHandler(enabled = state.isFullScreen || state.canGoBack) {
        if (state.isFullScreen) engine.exitYouTubeFullScreen() else engine.youTubeGoBack()
    }

    val hideChrome = state.isFullScreen || immersive.inPip

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!hideChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuraColors.GlassSurfaceTranslucent)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.canGoBack) {
                    IconButton(onClick = engine::youTubeGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuraColors.TextPrimary)
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = "YouTube",
                    style = MaterialTheme.typography.titleMedium.copy(color = AuraColors.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = engine::youTubeGoHome) {
                    Icon(Icons.Default.Home, contentDescription = "YouTube home", tint = AuraColors.TextSecondary)
                }
                IconButton(onClick = engine::youTubeReload) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (state.isLoading) "Stop loading" else "Reload",
                        tint = AuraColors.TextSecondary,
                    )
                }
                IconButton(onClick = onRequestPip) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture-in-picture", tint = AuraColors.TextSecondary)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "YouTube settings", tint = AuraColors.TextSecondary)
                }
            }
            AnimatedVisibility(visible = state.isLoading) {
                LinearProgressIndicator(
                    progress = { (state.progress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFFFF0033),
                    trackColor = Color.Transparent,
                )
            }
        }

        GeckoSessionView(
            session = session,
            gesturesEnabled = state.isFullScreen,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }

    if (showSettings) {
        Dialog(
            onDismissRequest = { showSettings = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = AuraColors.DeepBackground,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    YouTubeIntegrationScreen(
                        onOpenInBrowser = { url ->
                            engine.openYouTube(url)
                            showSettings = false
                        },
                        onRequestPip = {
                            showSettings = false
                            onRequestPip()
                        },
                    )
                    IconButton(
                        onClick = { showSettings = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(AuraColors.ElevatedBackground),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close settings", tint = AuraColors.TextPrimary)
                    }
                }
            }
        }
    }

    BrowserDialogHost(engine = engine)
}
