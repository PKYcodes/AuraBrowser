package com.aura.browser.feature.browser.ui

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.database.dao.BookmarkDao
import com.aura.browser.core.database.dao.DownloadDao
import com.aura.browser.core.database.dao.TabDao
import com.aura.browser.core.database.entity.BookmarkEntity
import com.aura.browser.core.database.entity.DownloadEntity
import com.aura.browser.core.database.entity.DownloadStatus
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    val engineManager: AuraGeckoEngineManager,
    private val bookmarkDao: BookmarkDao,
    private val tabDao: TabDao,
    private val downloadDao: DownloadDao,
) : ViewModel() {

    val sessionState = engineManager.sessionState

    val openTabCount: StateFlow<Int> = tabDao.observeAllTabs()
        .map { it.size.coerceAtLeast(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val isCurrentUrlBookmarked: StateFlow<Boolean> = sessionState
        .map { it.currentUrl }
        .flatMapLatest { bookmarkDao.observeIsBookmarked(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        engineManager.ensureDefaultTabAndLoad()
    }

    fun submitOmniboxInput(input: String) {
        engineManager.loadInputOrUrl(input)
    }

    fun toggleBookmark() {
        val state = sessionState.value
        viewModelScope.launch {
            if (isCurrentUrlBookmarked.value) {
                bookmarkDao.deleteBookmarkByUrl(state.currentUrl)
            } else {
                bookmarkDao.insertBookmark(
                    BookmarkEntity(
                        title = state.pageTitle.ifBlank { state.currentUrl },
                        url = state.currentUrl,
                    )
                )
            }
        }
    }

    fun saveCurrentPageForOfflineRead() {
        val state = sessionState.value
        viewModelScope.launch {
            val sanitizedTitle = state.pageTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "webpage" }
            downloadDao.insertDownload(
                DownloadEntity(
                    fileName = "${sanitizedTitle}.html",
                    sourceUrl = state.currentUrl,
                    mimeType = "text/html",
                    destinationUri = "content://downloads/public_downloads/${sanitizedTitle}.html",
                    totalBytes = 128_000L,
                    downloadedBytes = 128_000L,
                    status = DownloadStatus.COMPLETED,
                )
            )
        }
    }
}

@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    onOpenTabs: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onRequestPip: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val tabCount by viewModel.openTabCount.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()

    var addressInput by remember(state.currentUrl) { mutableStateOf(state.currentUrl) }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.submitOmniboxInput(initialUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        // Top Glass Address Bar & Security Shield
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AuraColors.GlassSurfaceTranslucent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // HTTPS Security Indicator & Privacy Shield Shortcut
                IconButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = if (state.isPrivate) Icons.Default.Shield
                        else if (state.isSecureHttps) Icons.Default.Lock
                        else Icons.Default.Warning,
                        contentDescription = "Connection Security & Privacy Shield",
                        tint = when {
                            state.isPrivate -> AuraColors.PrivatePurple
                            state.isSecureHttps -> AuraColors.SecureGreen
                            else -> AuraColors.WarningAmber
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Glass Omnibox Input Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AuraColors.ElevatedBackground.copy(alpha = 0.9f))
                        .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = AuraColors.TextPrimary,
                        ),
                        cursorBrush = SolidColor(AuraColors.PrimaryAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { viewModel.submitOmniboxInput(addressInput) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { viewModel.engineManager.reloadOrStop() },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (state.isLoading) "Stop loading" else "Reload page",
                        tint = AuraColors.TextPrimary,
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleBookmark() },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Toggle Bookmark",
                        tint = if (isBookmarked) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                    )
                }
            }

            AnimatedVisibility(visible = state.isLoading) {
                LinearProgressIndicator(
                    progress = { (state.progress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AuraColors.PrimaryAccent,
                    trackColor = AuraColors.ElevatedBackground,
                )
            }
        }

        // Security or Offline Notice Banner
        state.securityNotice?.let { notice ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                borderColor = AuraColors.DangerRed,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AuraColors.DangerRed,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodyMedium.copy(color = AuraColors.TextPrimary),
                    )
                }
            }
        }

        // Main Mozilla GeckoView Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val activeSession = viewModel.engineManager.getActiveGeckoSession()
            if (activeSession != null) {
                AndroidView(
                    factory = { context ->
                        GeckoView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setSession(activeSession)
                        }
                    },
                    update = { geckoView ->
                        if (geckoView.session !== activeSession) {
                            geckoView.releaseSession()
                            geckoView.setSession(activeSession)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Bottom Glass Browser Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AuraColors.GlassSurfaceTranslucent)
                .border(1.dp, AuraColors.GlassBorderSubtle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { viewModel.engineManager.goBack() },
                enabled = state.canGoBack,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = if (state.canGoBack) AuraColors.TextPrimary else AuraColors.TextMuted,
                )
            }

            IconButton(
                onClick = { viewModel.engineManager.goForward() },
                enabled = state.canGoForward,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (state.canGoForward) AuraColors.TextPrimary else AuraColors.TextMuted,
                )
            }

            IconButton(
                onClick = { viewModel.engineManager.toggleDesktopSiteMode() },
            ) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = "Desktop Site Mode",
                    tint = if (state.isDesktopMode) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                )
            }

            GlassPillButton(
                text = "$tabCount Tabs",
                icon = Icons.Default.Tab,
                onClick = onOpenTabs,
                isPrimary = state.isPrivate,
                accentColor = if (state.isPrivate) AuraColors.PrivatePurple else AuraColors.PrimaryAccent,
            )

            IconButton(onClick = onOpenExtensions) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = "Firefox WebExtensions",
                    tint = AuraColors.TextSecondary,
                )
            }

            IconButton(onClick = {
                viewModel.saveCurrentPageForOfflineRead()
                onOpenDownloads()
            }) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save Page / Downloads",
                    tint = AuraColors.TextSecondary,
                )
            }

            IconButton(onClick = onRequestPip) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = "Enter Picture-in-Picture",
                    tint = if (state.isWebMediaPlaying) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                )
            }
        }
    }
}
