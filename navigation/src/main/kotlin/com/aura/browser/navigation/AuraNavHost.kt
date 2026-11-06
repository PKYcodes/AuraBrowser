package com.aura.browser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aura.browser.feature.bookmarks.ui.BookmarksScreen
import com.aura.browser.feature.browser.ui.BrowserScreen
import com.aura.browser.feature.downloads.ui.DownloadsScreen
import com.aura.browser.feature.extensions.ui.ExtensionsScreen
import com.aura.browser.feature.filemanager.ui.FileManagerScreen
import com.aura.browser.feature.history.ui.HistoryScreen
import com.aura.browser.feature.home.ui.HomeScreen
import com.aura.browser.feature.music.ui.MusicPlayerScreen
import com.aura.browser.feature.privacy.ui.PrivacyDashboardScreen
import com.aura.browser.feature.settings.ui.SettingsScreen
import com.aura.browser.feature.tabs.ui.TabsScreen
import com.aura.browser.feature.video.ui.VideoPlayerScreen
import com.aura.browser.feature.youtube.ui.YouTubeIntegrationScreen

@Composable
fun AuraNavHost(
    navController: NavHostController,
    initialSharedUrl: String?,
    onRequestPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingBrowserUrl by rememberSaveable { mutableStateOf(initialSharedUrl) }

    NavHost(
        navController = navController,
        startDestination = if (!initialSharedUrl.isNullOrBlank()) {
            AuraDestination.BROWSER.route
        } else {
            AuraDestination.HOME.route
        },
        modifier = modifier,
    ) {
        composable(AuraDestination.HOME.route) {
            HomeScreen(
                onLaunchUrl = { targetUrl ->
                    pendingBrowserUrl = targetUrl
                    navController.navigate(AuraDestination.BROWSER.route)
                },
                onNavigateYoutube = { navController.navigate(AuraDestination.YOUTUBE.route) },
                onNavigateMusic = { navController.navigate(AuraDestination.MUSIC.route) },
                onNavigateVideo = { navController.navigate(AuraDestination.VIDEO.route) },
                onNavigateExtensions = { navController.navigate(AuraDestination.EXTENSIONS.route) },
                onNavigateBookmarks = { navController.navigate(AuraDestination.BOOKMARKS.route) },
                onNavigateHistory = { navController.navigate(AuraDestination.HISTORY.route) },
                onNavigateDownloads = { navController.navigate(AuraDestination.DOWNLOADS.route) },
                onNavigateFileManager = { navController.navigate(AuraDestination.FILE_MANAGER.route) },
                onNavigatePrivacy = { navController.navigate(AuraDestination.PRIVACY.route) },
                onNavigateSettings = { navController.navigate(AuraDestination.SETTINGS.route) },
            )
        }

        composable(AuraDestination.BROWSER.route) {
            BrowserScreen(
                initialUrl = pendingBrowserUrl,
                onOpenTabs = { navController.navigate(AuraDestination.TABS.route) },
                onOpenExtensions = { navController.navigate(AuraDestination.EXTENSIONS.route) },
                onOpenDownloads = { navController.navigate(AuraDestination.DOWNLOADS.route) },
                onOpenPrivacy = { navController.navigate(AuraDestination.PRIVACY.route) },
                onRequestPip = onRequestPip,
            )
        }

        composable(AuraDestination.TABS.route) {
            TabsScreen(
                onTabSelected = { navController.navigate(AuraDestination.BROWSER.route) },
            )
        }

        composable(AuraDestination.YOUTUBE.route) {
            YouTubeIntegrationScreen(
                onOpenInBrowser = { ytUrl ->
                    pendingBrowserUrl = ytUrl
                    navController.navigate(AuraDestination.BROWSER.route)
                },
                onRequestPip = onRequestPip,
            )
        }

        composable(AuraDestination.MUSIC.route) {
            MusicPlayerScreen()
        }

        composable(AuraDestination.VIDEO.route) {
            VideoPlayerScreen(onEnterPip = onRequestPip)
        }

        composable(AuraDestination.EXTENSIONS.route) {
            ExtensionsScreen()
        }

        composable(AuraDestination.BOOKMARKS.route) {
            BookmarksScreen(
                onOpenUrl = { url ->
                    pendingBrowserUrl = url
                    navController.navigate(AuraDestination.BROWSER.route)
                }
            )
        }

        composable(AuraDestination.HISTORY.route) {
            HistoryScreen(
                onOpenUrl = { url ->
                    pendingBrowserUrl = url
                    navController.navigate(AuraDestination.BROWSER.route)
                }
            )
        }

        composable(AuraDestination.DOWNLOADS.route) {
            DownloadsScreen()
        }

        composable(AuraDestination.FILE_MANAGER.route) {
            FileManagerScreen()
        }

        composable(AuraDestination.PRIVACY.route) {
            PrivacyDashboardScreen()
        }

        composable(AuraDestination.SETTINGS.route) {
            SettingsScreen()
        }
    }
}
