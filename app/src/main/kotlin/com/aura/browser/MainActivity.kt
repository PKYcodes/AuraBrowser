package com.aura.browser

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.browser.core.designsystem.theme.AuraBrowserTheme
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.core.mediacommon.controller.AuraMediaControllerManager
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import com.aura.browser.feature.pip.AuraPipController
import com.aura.browser.feature.youtube.intent.YouTubeIntentResolver
import com.aura.browser.navigation.AuraDestination
import com.aura.browser.navigation.AuraNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pipController: AuraPipController

    @Inject
    lateinit var mediaControllerManager: AuraMediaControllerManager

    @Inject
    lateinit var geckoEngineManager: AuraGeckoEngineManager

    @Inject
    lateinit var youTubeIntentResolver: YouTubeIntentResolver

    private var sharedOrDeepLinkUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pipController.registerActionReceiver()
        sharedOrDeepLinkUrl = extractIncomingUrl(intent)

        setContent {
            AuraBrowserTheme {
                val navController = rememberNavController()
                val inPipMode by pipController.isInPipMode.collectAsStateWithLifecycle()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: AuraDestination.HOME.route

                val bottomDestinations = listOf(
                    AuraDestination.HOME to Icons.Default.Home,
                    AuraDestination.BROWSER to Icons.Default.Public,
                    AuraDestination.YOUTUBE to Icons.Default.PlayCircleFilled,
                    AuraDestination.MUSIC to Icons.Default.LibraryMusic,
                    AuraDestination.VIDEO to Icons.Default.Movie,
                )

                Scaffold(
                    containerColor = AuraColors.DeepBackground,
                    bottomBar = {
                        // Hide bottom navigation bar during Picture-in-Picture mode per Android guidelines
                        if (!inPipMode) {
                            NavigationBar(
                                containerColor = AuraColors.GlassSurfaceTranslucent,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                                    .border(
                                        width = 1.dp,
                                        color = AuraColors.GlassBorder,
                                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                                    ),
                            ) {
                                bottomDestinations.forEach { (destination, icon) ->
                                    val selected = currentRoute == destination.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(destination.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = destination.label,
                                            )
                                        },
                                        label = { Text(text = destination.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = AuraColors.PrimaryAccent,
                                            selectedTextColor = AuraColors.PrimaryAccent,
                                            unselectedIconColor = AuraColors.TextSecondary,
                                            unselectedTextColor = AuraColors.TextSecondary,
                                            indicatorColor = AuraColors.ElevatedBackground,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AuraColors.DeepBackground)
                            .padding(innerPadding),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AuraNavHost(
                                navController = navController,
                                initialSharedUrl = sharedOrDeepLinkUrl,
                                onRequestPip = {
                                    val isPlaying = mediaControllerManager.playbackState.value.isPlaying ||
                                        geckoEngineManager.sessionState.value.isWebMediaPlaying
                                    pipController.enterPipNow(
                                        activity = this@MainActivity,
                                        isPlaying = isPlaying,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = extractIncomingUrl(intent)
        if (!incoming.isNullOrBlank()) {
            sharedOrDeepLinkUrl = incoming
            geckoEngineManager.loadInputOrUrl(incoming)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isMediaActive = mediaControllerManager.playbackState.value.isPlaying ||
            geckoEngineManager.sessionState.value.isFullScreenMedia
        if (isMediaActive) {
            pipController.enterPipNow(activity = this, isPlaying = true)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipController.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        pipController.unregisterActionReceiver()
        super.onDestroy()
    }

    private fun extractIncomingUrl(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                val rawUri = intent.dataString ?: return null
                youTubeIntentResolver.extractAndNormalize(rawUri)?.canonicalHttpsUrl ?: rawUri
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
                youTubeIntentResolver.extractAndNormalize(sharedText)?.canonicalHttpsUrl ?: sharedText
            }
            else -> null
        }
    }
}
