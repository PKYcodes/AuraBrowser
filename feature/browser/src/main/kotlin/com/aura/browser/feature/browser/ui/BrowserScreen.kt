package com.aura.browser.feature.browser.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.database.dao.BookmarkDao
import com.aura.browser.core.database.entity.BookmarkEntity
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.ImmersiveModeEffect
import com.aura.browser.core.designsystem.component.LocalImmersiveMode
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.core.network.download.DownloadEvent
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    val engineManager: AuraGeckoEngineManager,
    private val bookmarkDao: BookmarkDao,
) : ViewModel() {

    val sessionState = engineManager.sessionState

    val openTabCount: StateFlow<Int> = engineManager.tabs
        .map { it.size.coerceAtLeast(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val isCurrentUrlBookmarked: StateFlow<Boolean> = sessionState
        .map { it.currentUrl }
        .flatMapLatest { bookmarkDao.observeIsBookmarked(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Idempotent: restores tabs on first call, no-op afterwards.
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
}

@Composable
fun BrowserScreen(
    onOpenTabs: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onRequestPip: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val engine = viewModel.engineManager
    val context = LocalContext.current
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val tabCount by viewModel.openTabCount.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentUrlBookmarked.collectAsStateWithLifecycle()
    val activeSession by engine.activeGeckoSession.collectAsStateWithLifecycle()
    val findState by engine.findState.collectAsStateWithLifecycle()

    var addressInput by remember(state.currentUrl) { mutableStateOf(state.currentUrl) }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    val immersive = LocalImmersiveMode.current
    val hideChrome = state.isFullScreenMedia || immersive.inPip
    ImmersiveModeEffect(active = state.isFullScreenMedia)

    BackHandler(enabled = state.isFullScreenMedia || findVisible || state.canGoBack) {
        when {
            state.isFullScreenMedia -> engine.exitFullScreen()
            findVisible -> {
                findVisible = false
                engine.clearFindInPage()
            }
            else -> engine.goBack()
        }
    }

    LaunchedEffect(Unit) {
        engine.downloadEvents.collect { event ->
            snackbarHost.currentSnackbarData?.dismiss()
            when (event) {
                is DownloadEvent.Started ->
                    snackbarHost.showSnackbar("Downloading ${event.fileName}", duration = SnackbarDuration.Short)
                is DownloadEvent.Completed -> {
                    val result = snackbarHost.showSnackbar(
                        message = "Saved ${event.fileName}",
                        actionLabel = "View",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenDownloads()
                }
                is DownloadEvent.Failed ->
                    snackbarHost.showSnackbar("Download failed: ${event.reason}", duration = SnackbarDuration.Long)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        if (!hideChrome) {
            AddressBar(
                isPrivate = state.isPrivate,
                isSecure = state.isSecureHttps,
                isLoading = state.isLoading,
                progress = state.progress,
                isBookmarked = isBookmarked,
                addressInput = addressInput,
                onAddressChange = { addressInput = it },
                onSubmit = { viewModel.submitOmniboxInput(addressInput) },
                onReloadOrStop = engine::reloadOrStop,
                onToggleBookmark = viewModel::toggleBookmark,
                onOpenPrivacy = onOpenPrivacy,
            )

            if (findVisible) {
                FindBar(
                    query = findQuery,
                    found = findState.found,
                    current = findState.current,
                    total = findState.total,
                    onQueryChange = {
                        findQuery = it
                        engine.findInPage(it)
                    },
                    onNext = { engine.findInPage(findQuery) },
                    onPrevious = { engine.findInPage(findQuery, backwards = true) },
                    onClose = {
                        findVisible = false
                        engine.clearFindInPage()
                    },
                )
            }

            state.securityNotice?.let { notice ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    borderColor = AuraColors.DangerRed,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AuraColors.DangerRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodyMedium.copy(color = AuraColors.TextPrimary),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            GeckoSessionView(
                session = activeSession,
                gesturesEnabled = state.isFullScreenMedia,
                modifier = Modifier.fillMaxSize(),
            )
            SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
        }

        if (!hideChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuraColors.GlassSurfaceTranslucent)
                    .border(1.dp, AuraColors.GlassBorderSubtle)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = engine::goBack, enabled = state.canGoBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (state.canGoBack) AuraColors.TextPrimary else AuraColors.TextMuted,
                    )
                }
                IconButton(onClick = engine::goForward, enabled = state.canGoForward) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (state.canGoForward) AuraColors.TextPrimary else AuraColors.TextMuted,
                    )
                }

                GlassPillButton(
                    text = "$tabCount Tabs",
                    icon = Icons.Default.Tab,
                    onClick = onOpenTabs,
                    isPrimary = state.isPrivate,
                    accentColor = if (state.isPrivate) AuraColors.PrivatePurple else AuraColors.PrimaryAccent,
                )

                IconButton(onClick = onOpenDownloads) {
                    Icon(Icons.Default.Download, contentDescription = "Downloads", tint = AuraColors.TextSecondary)
                }

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = AuraColors.TextSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Find in page") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = { menuOpen = false; findVisible = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.isDesktopMode) "Request mobile site" else "Request desktop site") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DesktopWindows,
                                    contentDescription = null,
                                    tint = if (state.isDesktopMode) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                                )
                            },
                            onClick = { menuOpen = false; engine.toggleDesktopSiteMode() },
                        )
                        DropdownMenuItem(
                            text = { Text("Save page as PDF") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = { menuOpen = false; engine.savePageAsPdf() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share page") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = { menuOpen = false; shareText(context, state.currentUrl) },
                        )
                        DropdownMenuItem(
                            text = { Text("Picture-in-picture") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PictureInPictureAlt,
                                    contentDescription = null,
                                    tint = if (state.isWebMediaPlaying) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                                )
                            },
                            onClick = { menuOpen = false; onRequestPip() },
                        )
                        DropdownMenuItem(
                            text = { Text("Extensions") },
                            leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenExtensions() },
                        )
                        DropdownMenuItem(
                            text = { Text("Privacy shield") },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                            onClick = { menuOpen = false; onOpenPrivacy() },
                        )
                    }
                }
            }
        }
    }

    BrowserDialogHost(engine = engine)
}

@Composable
private fun AddressBar(
    isPrivate: Boolean,
    isSecure: Boolean,
    isLoading: Boolean,
    progress: Int,
    isBookmarked: Boolean,
    addressInput: String,
    onAddressChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.GlassSurfaceTranslucent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onOpenPrivacy, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = when {
                        isPrivate -> Icons.Default.Shield
                        isSecure -> Icons.Default.Lock
                        else -> Icons.Default.Warning
                    },
                    contentDescription = "Connection security and privacy shield",
                    tint = when {
                        isPrivate -> AuraColors.PrivatePurple
                        isSecure -> AuraColors.SecureGreen
                        else -> AuraColors.WarningAmber
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

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
                    onValueChange = onAddressChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuraColors.TextPrimary),
                    cursorBrush = SolidColor(AuraColors.PrimaryAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onReloadOrStop, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Stop loading" else "Reload page",
                    tint = AuraColors.TextPrimary,
                )
            }

            IconButton(onClick = onToggleBookmark, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (isBookmarked) AuraColors.PrimaryAccent else AuraColors.TextSecondary,
                )
            }
        }

        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0.05f, 1f) },
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
}

@Composable
private fun FindBar(
    query: String,
    found: Boolean,
    current: Int,
    total: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuraColors.ElevatedBackground)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuraColors.TextPrimary),
            cursorBrush = SolidColor(AuraColors.PrimaryAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                query.isBlank() -> ""
                !found -> "No matches"
                else -> "$current/$total"
            },
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (query.isNotBlank() && !found) AuraColors.WarningAmber else AuraColors.TextSecondary,
            ),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onPrevious) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", tint = AuraColors.TextPrimary) }
        IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", tint = AuraColors.TextPrimary) }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close find bar", tint = AuraColors.TextPrimary) }
    }
}
