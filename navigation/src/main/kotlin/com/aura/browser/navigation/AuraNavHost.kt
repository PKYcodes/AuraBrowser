package com.aura.browser.navigation

import androidx.compose.runtime.Composable
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

/**
 * App navigation graph.
 *
 * [onOpenUrl] is invoked by every screen that wants to browse somewhere (home shortcuts, bookmarks,
 * history, YouTube). The host loads the URL straight into the engine and this graph then reveals the
 * browser, so there is no pending-URL state that could be replayed when the browser tab is revisited.
 */
@Composable
fun AuraNavHost(
    navController: NavHostController,
    onOpenUrl: (String) -> Unit,
    onRequestPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openInBrowser: (String) -> Unit = { url ->
        onOpenUrl(url)
        navController.openBrowser()
    }

    NavHost(
        navController = navController,
        startDestination = AuraDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(AuraDestination.HOME.route) {
            HomeScreen(
                onLaunchUrl = openInBrowser,
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
                onOpenTabs = { navController.navigate(AuraDestination.TABS.route) },
                onOpenExtensions = { navController.navigate(AuraDestination.EXTENSIONS.route) },
                onOpenDownloads = { navController.navigate(AuraDestination.DOWNLOADS.route) },
                onOpenPrivacy = { navController.navigate(AuraDestination.PRIVACY.route) },
                onRequestPip = onRequestPip,
            )
        }

        composable(AuraDestination.TABS.route) {
            TabsScreen(
                onTabSelected = { navController.openBrowser() },
            )
        }

        composable(AuraDestination.YOUTUBE.route) {
            YouTubeIntegrationScreen(
                onOpenInBrowser = openInBrowser,
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
                onOpenUrl = openInBrowser
            )
        }

        composable(AuraDestination.HISTORY.route) {
            HistoryScreen(
                onOpenUrl = openInBrowser
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

/** Shows the browser without stacking duplicate copies of it on the back stack. */
fun NavHostController.openBrowser() {
    if (!popBackStack(AuraDestination.BROWSER.route, inclusive = false)) {
        navigate(AuraDestination.BROWSER.route) { launchSingleTop = true }
    }
}
