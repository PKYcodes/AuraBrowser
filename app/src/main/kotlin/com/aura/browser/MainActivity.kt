package com.aura.browser

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.designsystem.component.ImmersiveModeState
import com.aura.browser.core.designsystem.component.LocalImmersiveMode
import com.aura.browser.core.designsystem.theme.AuraBrowserTheme
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.core.mediacommon.controller.AuraMediaControllerManager
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import com.aura.browser.feature.pip.AuraPipController
import com.aura.browser.feature.youtube.intent.YouTubeIntentResolver
import com.aura.browser.navigation.AuraDestination
import com.aura.browser.navigation.AuraNavHost
import com.aura.browser.navigation.navigateTopLevel
import com.aura.browser.navigation.openBrowser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var preferencesDataSource: AuraPreferencesDataSource

    /** A link received from outside the app (share sheet, VIEW intent) waiting to be opened. */
    private data class IncomingLink(val url: String, val isYouTube: Boolean)

    private var incomingLink by mutableStateOf<IncomingLink?>(null)

    /** "Auto-enter Picture-in-Picture" preference, mirrored from DataStore. */
    private var autoPipEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pipController.registerActionReceiver()
        pipController.playPauseHandler = ::togglePlaybackFromPip

        if (savedInstanceState == null) incomingLink = extractIncomingLink(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                preferencesDataSource.userSettings
                    .map { it.autoEnterPipOnBackground }
                    .collect { autoPipEnabled = it }
            }
        }

        setContent {
            AuraBrowserTheme {
                val navController = rememberNavController()
                val immersive = remember { ImmersiveModeState() }
                val inPipMode by pipController.isInPipMode.collectAsStateWithLifecycle()
                val playback by mediaControllerManager.playbackState.collectAsStateWithLifecycle()
                val browserState by geckoEngineManager.sessionState.collectAsStateWithLifecycle()
                val youTubeState by geckoEngineManager.youTubeState.collectAsStateWithLifecycle()
                val webMedia by geckoEngineManager.webMedia.collectAsStateWithLifecycle()
                val webVideoSize by geckoEngineManager.webVideoSize.collectAsStateWithLifecycle()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: AuraDestination.HOME.route

                val bottomDestinations = listOf(
                    AuraDestination.HOME to Icons.Default.Home,
                    AuraDestination.BROWSER to Icons.Default.Public,
                    AuraDestination.YOUTUBE to Icons.Default.PlayCircleFilled,
                    AuraDestination.MUSIC to Icons.Default.LibraryMusic,
                    AuraDestination.VIDEO to Icons.Default.Movie,
                )

                // Open links that arrive from other apps, on cold start and while running.
                LaunchedEffect(incomingLink) {
                    incomingLink?.let { link ->
                        if (link.isYouTube) {
                            // YouTube links open in the full-screen YouTube section, like the real app.
                            geckoEngineManager.openYouTube(link.url)
                            navController.navigateTopLevel(AuraDestination.YOUTUBE.route)
                        } else {
                            geckoEngineManager.loadInputOrUrl(link.url)
                            navController.openBrowser()
                        }
                        incomingLink = null
                    }
                }

                SideEffect { immersive.inPip = inPipMode }

                // Hide system bars for fullscreen video.
                LaunchedEffect(immersive.enabled) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    if (immersive.enabled) {
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                // Keep PiP parameters current so Android 12+ can enter PiP automatically on Home.
                val localVideoPlaying = playback.isPlaying && playback.isVideo
                val webFullScreen = browserState.isFullScreenMedia || youTubeState.isFullScreen
                val webVideoPlaying = webFullScreen && webMedia.isPlaying
                LaunchedEffect(
                    localVideoPlaying, webVideoPlaying, playback.videoWidth, playback.videoHeight, webVideoSize, autoPipEnabled,
                ) {
                    val width = if (localVideoPlaying) playback.videoWidth else webVideoSize?.first ?: 16
                    val height = if (localVideoPlaying) playback.videoHeight else webVideoSize?.second ?: 9
                    pipController.updateParams(
                        activity = this@MainActivity,
                        width = width,
                        height = height,
                        isPlaying = localVideoPlaying || webVideoPlaying,
                        autoEnter = autoPipEnabled && (localVideoPlaying || webVideoPlaying),
                    )
                }

                // Keep the screen awake while a video plays full screen (otherwise it dims and locks mid-video).
                val keepScreenOn = localVideoPlaying || webVideoPlaying
                DisposableEffect(keepScreenOn) {
                    if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                }

                CompositionLocalProvider(LocalImmersiveMode provides immersive) {
                    Scaffold(
                        containerColor = AuraColors.DeepBackground,
                        bottomBar = {
                            // Hidden in Picture-in-Picture and during fullscreen playback.
                            if (!inPipMode && !immersive.enabled) {
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
                                        NavigationBarItem(
                                            selected = currentRoute == destination.route,
                                            onClick = {
                                                navController.navigate(destination.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Icon(imageVector = icon, contentDescription = destination.label) },
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
                        },
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AuraColors.DeepBackground)
                                .padding(if (immersive.enabled) PaddingValues(0.dp) else innerPadding),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AuraNavHost(
                                    navController = navController,
                                    onOpenUrl = geckoEngineManager::loadInputOrUrl,
                                    onRequestPip = ::requestPipFromUi,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPipFromUi() {
        val playback = mediaControllerManager.playbackState.value
        val web = geckoEngineManager.webMedia.value
        val playing = playback.isPlaying || web.isPlaying
        if (!playing) {
            Toast.makeText(this, "Start a video first, then enter Picture-in-Picture", Toast.LENGTH_SHORT).show()
            return
        }
        val useLocal = playback.isPlaying && playback.isVideo
        val webSize = geckoEngineManager.webVideoSize.value
        val width = if (useLocal) playback.videoWidth else webSize?.first ?: 16
        val height = if (useLocal) playback.videoHeight else webSize?.second ?: 9

        lifecycleScope.launch {
            if (!useLocal) {
                // Make the page show only its video, otherwise the PiP window would be a cropped copy of the page.
                if (geckoEngineManager.setWebPipLayout(true)) delay(250)
            }
            val entered = pipController.enterPipNow(activity = this@MainActivity, width = width, height = height, isPlaying = true)
            if (!entered) {
                if (!useLocal) geckoEngineManager.setWebPipLayout(false)
                Toast.makeText(this@MainActivity, "Picture-in-Picture isn\u2019t available on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun togglePlaybackFromPip() {
        val localPlaying = mediaControllerManager.playbackState.value.isPlaying
        val webActive = geckoEngineManager.webMedia.value.isActive
        if (!localPlaying && webActive) geckoEngineManager.toggleWebMedia() else mediaControllerManager.togglePlayPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingLink(intent)?.let { incomingLink = it }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP automatically through the params set above; older versions need this call.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !autoPipEnabled) return
        val playback = mediaControllerManager.playbackState.value
        val web = geckoEngineManager.sessionState.value
        val localVideo = playback.isPlaying && playback.isVideo
        val webFullScreen = web.isFullScreenMedia || geckoEngineManager.youTubeState.value.isFullScreen
        val webVideo = webFullScreen && geckoEngineManager.webMedia.value.isPlaying
        if (localVideo || webVideo) {
            val webSize = geckoEngineManager.webVideoSize.value
            pipController.enterPipNow(
                activity = this,
                width = if (localVideo) playback.videoWidth else webSize?.first ?: 16,
                height = if (localVideo) playback.videoHeight else webSize?.second ?: 9,
                isPlaying = true,
            )
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipController.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // Back to the normal page layout once the PiP window is closed or expanded.
        if (!isInPictureInPictureMode) geckoEngineManager.setWebPipLayout(false)
    }

    override fun onStop() {
        geckoEngineManager.persistSessionStates()
        super.onStop()
    }

    override fun onDestroy() {
        pipController.playPauseHandler = null
        pipController.unregisterActionReceiver()
        if (isFinishing) geckoEngineManager.onAppExit()
        super.onDestroy()
    }

    private fun extractIncomingLink(intent: Intent?): IncomingLink? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null

        val youTube = youTubeIntentResolver.extractAndNormalize(raw)
        return if (youTube != null) IncomingLink(youTube.canonicalHttpsUrl, isYouTube = true) else IncomingLink(raw, isYouTube = false)
    }
}
