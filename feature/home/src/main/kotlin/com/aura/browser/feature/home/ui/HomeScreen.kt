package com.aura.browser.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.database.dao.BookmarkDao
import com.aura.browser.core.database.entity.BookmarkEntity
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.datastore.AuraUserSettings
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassStatBadge
import com.aura.browser.core.designsystem.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class QuickLaunchSite(
    val title: String,
    val url: String,
    val subtitle: String,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    preferencesDataSource: AuraPreferencesDataSource,
    bookmarkDao: BookmarkDao,
) : ViewModel() {

    val settings: StateFlow<AuraUserSettings> = preferencesDataSource.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuraUserSettings())

    val bookmarks: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quickSites = listOf(
        QuickLaunchSite("DuckDuckGo", "https://duckduckgo.com", "Private Web Search"),
        QuickLaunchSite("YouTube", "https://m.youtube.com", "Video & Music Streaming"),
        QuickLaunchSite("Mozilla Add-ons", "https://addons.mozilla.org/en-US/android/", "Android WebExtensions"),
        QuickLaunchSite("Wikipedia", "https://www.wikipedia.org", "Free Encyclopedia"),
        QuickLaunchSite("GitHub", "https://github.com", "Developer Repositories"),
        QuickLaunchSite("MDN Web Docs", "https://developer.mozilla.org", "Web Standards & APIs"),
    )
}

@Composable
fun HomeScreen(
    onLaunchUrl: (String) -> Unit,
    onNavigateYoutube: () -> Unit,
    onNavigateMusic: () -> Unit,
    onNavigateVideo: () -> Unit,
    onNavigateExtensions: () -> Unit,
    onNavigateBookmarks: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateDownloads: () -> Unit,
    onNavigateFileManager: () -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigateSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = "AURA BROWSER",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = AuraColors.PrimaryAccent,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Private browsing 2022 Music 2022 Video",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Glass Search & URL Omnibox
        item {
            GlassCard(
                cornerRadius = 24.dp,
                isHighlighted = true,
                contentPadding = PaddingValues(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search or enter URL",
                        tint = AuraColors.PrimaryAccent,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search ${settings.searchEngine.displayName} or type a URL...",
                                style = MaterialTheme.typography.bodyLarge.copy(color = AuraColors.TextMuted),
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuraColors.TextPrimary),
                            cursorBrush = SolidColor(AuraColors.PrimaryAccent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) onLaunchUrl(searchQuery)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassPillButton(
                        text = "Go",
                        isPrimary = true,
                        onClick = {
                            onLaunchUrl(searchQuery.ifBlank { settings.homePageUrl })
                        },
                    )
                }
            }
        }

        // Real-Time Privacy & Security Telemetry Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassStatBadge(
                    label = "Trackers Blocked",
                    value = settings.blockTrackersCount.toString(),
                    accentColor = AuraColors.SecureGreen,
                    modifier = Modifier.weight(1f),
                )
                GlassStatBadge(
                    label = "HTTPS Upgrades",
                    value = settings.httpsUpgradesCount.toString(),
                    accentColor = AuraColors.PrimaryAccent,
                    modifier = Modifier.weight(1f),
                )
                GlassStatBadge(
                    label = "Shield Mode",
                    value = settings.trackingProtectionPreset.title,
                    accentColor = AuraColors.SecondaryCyan,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // All-in-One Feature Hub Grid
        item {
            Text(
                text = "All-in-One Media & Browser Hub",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeatureHubTile(
                        title = "YouTube Hub",
                        subtitle = "Web + Share + PiP",
                        icon = Icons.Default.PlayCircleFilled,
                        onClick = onNavigateYoutube,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureHubTile(
                        title = "Music Player",
                        subtitle = "Media3 + Equalizer",
                        icon = Icons.Default.LibraryMusic,
                        onClick = onNavigateMusic,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeatureHubTile(
                        title = "Video Player",
                        subtitle = "MKV/MP4/WebM + PiP",
                        icon = Icons.Default.Movie,
                        onClick = onNavigateVideo,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureHubTile(
                        title = "Extensions",
                        subtitle = "Firefox WebExtensions",
                        icon = Icons.Default.Extension,
                        onClick = onNavigateExtensions,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeatureHubTile(
                        title = "File Manager",
                        subtitle = "SAF & Local Storage",
                        icon = Icons.Default.Folder,
                        onClick = onNavigateFileManager,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureHubTile(
                        title = "Downloads",
                        subtitle = "SHA-256 Verified",
                        icon = Icons.Default.Download,
                        onClick = onNavigateDownloads,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeatureHubTile(
                        title = "Bookmarks",
                        subtitle = "${bookmarks.size} saved sites",
                        icon = Icons.Default.Bookmark,
                        onClick = onNavigateBookmarks,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureHubTile(
                        title = "History",
                        subtitle = "Timeline & Search",
                        icon = Icons.Default.History,
                        onClick = onNavigateHistory,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeatureHubTile(
                        title = "Privacy Shield",
                        subtitle = "Cookies & Permissions",
                        icon = Icons.Default.Security,
                        onClick = onNavigatePrivacy,
                        modifier = Modifier.weight(1f),
                    )
                    FeatureHubTile(
                        title = "Settings",
                        subtitle = "Search, HTTPS & Defaults",
                        icon = Icons.Default.Settings,
                        onClick = onNavigateSettings,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Quick Launch Speed Dials
        item {
            Text(
                text = "Speed Dial Destinations",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        items(viewModel.quickSites) { site ->
            GlassCard(
                cornerRadius = 18.dp,
                onClick = { onLaunchUrl(site.url) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = AuraColors.PrimaryAccent,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = site.title, style = MaterialTheme.typography.titleMedium)
                        Text(text = site.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "Open →",
                        style = MaterialTheme.typography.labelMedium.copy(color = AuraColors.PrimaryAccent),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureHubTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = AuraColors.PrimaryAccent,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
