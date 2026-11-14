package com.aura.browser.feature.browser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.aura.browser.core.common.model.TrackingProtectionPreset
import com.aura.browser.core.database.dao.HistoryDao
import com.aura.browser.core.database.dao.SitePermissionDao
import com.aura.browser.core.database.dao.TabDao
import com.aura.browser.core.database.entity.TabEntity
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.datastore.AuraUserSettings
import com.aura.browser.core.network.download.AuraDownloadManager
import com.aura.browser.core.network.download.DownloadRequest
import com.aura.browser.core.network.url.AuraUrlSanitizer
import com.aura.browser.core.network.url.ResolvedNavigationTarget
import com.aura.browser.feature.browser.service.AuraWebMediaService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the GeckoRuntime and every GeckoSession (one per tab) and exposes browser state as flows.
 *
 * Threading: every engine callback and every mutation of the maps below happens on the main thread
 * (`engineScope` uses `Dispatchers.Main.immediate`). Persistence is delegated to Room and runs off-thread.
 * Private tabs are **never** persisted: they exist only in [privateTabs] and in memory sessions.
 */
@Singleton
class AuraGeckoEngineManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tabDao: TabDao,
    private val historyDao: HistoryDao,
    private val sitePermissionDao: SitePermissionDao,
    private val preferencesDataSource: AuraPreferencesDataSource,
    private val urlSanitizer: AuraUrlSanitizer,
    private val downloadManager: AuraDownloadManager,
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val activeSessions = ConcurrentHashMap<String, GeckoSession>()
    private val liveStates = ConcurrentHashMap<String, TabLive>()
    private val lastRecordedUrl = ConcurrentHashMap<String, String>()
    private val pendingSessionStates = ConcurrentHashMap<String, GeckoSession.SessionState>()
    private val mediaStates = ConcurrentHashMap<String, WebMediaInfo>()
    private val mediaSessions = ConcurrentHashMap<String, MediaSession>()

    @Volatile
    private var geckoRuntime: GeckoRuntime? = null

    @Volatile
    private var cachedSettings: AuraUserSettings = AuraUserSettings()

    private val initMutex = Mutex()
    private var initialized = false
    private var stateFlushJob: Job? = null
    private var trackerFlushJob: Job? = null
    private val pendingTrackerCount = AtomicInteger(0)

    private val _sessionState = MutableStateFlow(ActiveBrowserSessionState())
    val sessionState: StateFlow<ActiveBrowserSessionState> = _sessionState.asStateFlow()

    private val _activeSession = MutableStateFlow<GeckoSession?>(null)

    /** The session currently shown by the browser UI. Emits `null` while a tab is being replaced. */
    val activeGeckoSession: StateFlow<GeckoSession?> = _activeSession.asStateFlow()

    private val privateTabs = MutableStateFlow<List<BrowserTab>>(emptyList())

    val tabs: StateFlow<List<BrowserTab>> = combine(
        tabDao.observeAllTabs(),
        privateTabs,
        _sessionState.map { it.activeTabId }.distinctUntilChanged(),
    ) { stored, private, activeId ->
        (stored.map { it.toBrowserTab() } + private)
            .sortedBy { it.createdAt }
            .map { it.copy(isActive = it.id == activeId) }
    }.stateIn(engineScope, SharingStarted.Eagerly, emptyList())

    val promptQueue = BrowserPromptQueue()

    private val _contextMenu = MutableStateFlow<ContextMenuTarget?>(null)
    val contextMenu: StateFlow<ContextMenuTarget?> = _contextMenu.asStateFlow()

    private val _findState = MutableStateFlow(FindInPageState())
    val findState: StateFlow<FindInPageState> = _findState.asStateFlow()

    private val _webMedia = MutableStateFlow(WebMediaInfo())
    val webMedia: StateFlow<WebMediaInfo> = _webMedia.asStateFlow()

    /** Intrinsic size of the video a page is playing (reported by the built-in extension). */
    private val _webVideoSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val webVideoSize: StateFlow<Pair<Int, Int>?> = _webVideoSize.asStateFlow()

    /** Live channels to the built-in extension's content script, one per page. */
    private val extensionPorts = ConcurrentHashMap<GeckoSession, WebExtension.Port>()

    // Dedicated full-screen YouTube section (separate from the tab list).
    private var youTubeGeckoSession: GeckoSession? = null
    private val _youTubeSession = MutableStateFlow<GeckoSession?>(null)
    val youTubeSession: StateFlow<GeckoSession?> = _youTubeSession.asStateFlow()
    private val _youTubeState = MutableStateFlow(YouTubeAppState())
    val youTubeState: StateFlow<YouTubeAppState> = _youTubeState.asStateFlow()

    private val promptDelegate = AuraPromptDelegate(appContext, promptQueue)
    private val permissionDelegate = AuraPermissionDelegate(engineScope, promptQueue, sitePermissionDao, urlSanitizer)

    /** Download events (started / completed / failed) for UI feedback. */
    val downloadEvents get() = downloadManager.events

    init {
        // Persisted settings drive the runtime; changes made in Settings/Privacy apply immediately.
        engineScope.launch {
            preferencesDataSource.userSettings.collect { settings ->
                cachedSettings = settings
                applyRuntimeSettings(settings)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Runtime
    // ---------------------------------------------------------------------------------------------

    @Synchronized
    fun getOrCreateRuntime(): GeckoRuntime {
        geckoRuntime?.let { return it }

        val settings = cachedSettings
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .consoleOutput(false)
            .javaScriptEnabled(true)
            .automaticFontSizeAdjustment(true)
            .enterpriseRootsEnabled(false)
            .allowInsecureConnections(
                if (settings.httpsOnlyMode) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL
            )
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(GeckoSettingsMapper.antiTracking(settings.trackingProtectionPreset))
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .cookieBehavior(GeckoSettingsMapper.cookieBehavior(settings.cookiePolicyMode))
                    .build()
            )
            .build()

        val createdRuntime = GeckoRuntime.create(appContext, runtimeSettings)
        geckoRuntime = createdRuntime
        runCatching {
            // Without a prompt delegate GeckoView never answers install requests (they hang forever).
            createdRuntime.webExtensionController.setPromptDelegate(AuraExtensionPromptDelegate.create(promptQueue))
        }.onFailure { Log.w(TAG, "Could not set extension prompt delegate", it) }
        ensureBuiltInMediaWebExtension(createdRuntime)
        return createdRuntime
    }

    private fun applyRuntimeSettings(settings: AuraUserSettings) {
        val runtime = geckoRuntime ?: return
        runCatching {
            runtime.settings.contentBlocking.setAntiTracking(GeckoSettingsMapper.antiTracking(settings.trackingProtectionPreset))
            runtime.settings.contentBlocking.setCookieBehavior(GeckoSettingsMapper.cookieBehavior(settings.cookiePolicyMode))
            runtime.settings.setAllowInsecureConnections(
                if (settings.httpsOnlyMode) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL
            )
        }.onFailure { Log.w(TAG, "Could not apply runtime settings", it) }
    }

    /** Kept for API compatibility; settings are normally applied automatically when they change. */
    fun applyPrivacySettings(preset: TrackingProtectionPreset, httpsOnly: Boolean) {
        applyRuntimeSettings(cachedSettings.copy(trackingProtectionPreset = preset, httpsOnlyMode = httpsOnly))
    }

    /** Answers configuration requests from the built-in media extension's content scripts. */
    private val extensionMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender,
        ): GeckoResult<Any>? {
            val settings = cachedSettings
            val config = JSONObject()
                .put("backgroundPlay", settings.youtubeBackgroundPlayEnabled)
                .put("hideShorts", settings.youtubeHideShortsShelf)
            return GeckoResult.fromValue<Any>(config)
        }

        override fun onConnect(port: WebExtension.Port) {
            val owner = port.sender.session ?: return
            extensionPorts[owner] = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    if (json.optString("type") == "videoSize") {
                        val w = json.optInt("width")
                        val h = json.optInt("height")
                        if (w > 0 && h > 0) _webVideoSize.value = w to h
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    extensionPorts.remove(owner, port)
                }
            })
        }
    }

    private fun ensureBuiltInMediaWebExtension(runtime: GeckoRuntime) {
        runtime.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/extensions/aura-media-enhancer/",
                "aura-media-enhancer@aura.browser",
            )
            .accept(
                { ext ->
                    Log.i(TAG, "Built-in extension ready: ${ext?.id}")
                    ext?.setMessageDelegate(extensionMessageDelegate, "aura")
                },
                { err -> Log.w(TAG, "Built-in extension registration failed: ${err?.message}") },
            )
    }

    // ---------------------------------------------------------------------------------------------
    // Startup / tabs
    // ---------------------------------------------------------------------------------------------

    /** Restores the previous tabs (or creates a first one). Safe to call repeatedly. */
    fun ensureDefaultTabAndLoad() {
        engineScope.launch { ensureInitialized() }
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            val settings = preferencesDataSource.userSettings.first()
            cachedSettings = settings

            if (settings.clearSessionOnExit) {
                clearPersistedBrowsingData()
            }
            // Private tabs must never survive a restart (older builds persisted them).
            tabDao.deletePrivateTabs()

            val stored = tabDao.getAllTabs()
            if (stored.isEmpty()) {
                createTabInternal(settings.homePageUrl, isPrivate = false, desktop = settings.desktopSiteDefault)
            } else {
                val toActivate = stored.firstOrNull { it.isActive } ?: stored.first()
                activateTab(toActivate.toBrowserTab())
            }
            initialized = true
        }
    }

    fun openNewTab(url: String? = null, isPrivate: Boolean = false) {
        engineScope.launch {
            ensureInitialized()
            val settings = preferencesDataSource.userSettings.first()
            createTabInternal(url ?: settings.homePageUrl, isPrivate, settings.desktopSiteDefault)
        }
    }

    fun switchToTab(tabId: String) {
        engineScope.launch {
            ensureInitialized()
            tabs.value.firstOrNull { it.id == tabId }?.let { activateTab(it) }
        }
    }

    fun closeTab(tabId: String) {
        engineScope.launch {
            val snapshot = tabs.value
            val closing = snapshot.firstOrNull { it.id == tabId } ?: return@launch
            val wasActive = tabId == _sessionState.value.activeTabId

            removeSession(tabId)
            if (closing.isPrivate) {
                privateTabs.update { list -> list.filterNot { it.id == tabId } }
            } else {
                tabDao.deleteTabById(tabId)
            }

            if (!wasActive) return@launch
            val remaining = snapshot.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                createTabInternal(cachedSettings.homePageUrl, isPrivate = false, desktop = cachedSettings.desktopSiteDefault)
            } else {
                val index = snapshot.indexOfFirst { it.id == tabId }.coerceIn(0, remaining.lastIndex)
                activateTab(remaining[index])
            }
        }
    }

    fun closeAllPrivateTabs() {
        engineScope.launch {
            val closing = privateTabs.value
            if (closing.isEmpty()) return@launch
            val activeWasPrivate = closing.any { it.id == _sessionState.value.activeTabId }

            closing.forEach { removeSession(it.id) }
            privateTabs.value = emptyList()
            // Private sessions keep cookies/cache in memory only; closing them discards that data.

            if (activeWasPrivate) {
                val fallback = tabDao.getAllTabs().firstOrNull()
                if (fallback != null) {
                    activateTab(fallback.toBrowserTab())
                } else {
                    createTabInternal(cachedSettings.homePageUrl, isPrivate = false, desktop = cachedSettings.desktopSiteDefault)
                }
            }
        }
    }

    private suspend fun createTabInternal(url: String, isPrivate: Boolean, desktop: Boolean) {
        val now = System.currentTimeMillis()
        val tab = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = url,
            title = if (isPrivate) "Private Tab" else "New Tab",
            isPrivate = isPrivate,
            isDesktopMode = desktop,
            isActive = true,
            createdAt = now,
        )
        registerTab(tab)
        activateTab(tab)
    }

    private suspend fun registerTab(tab: BrowserTab) {
        if (tab.isPrivate) {
            privateTabs.update { it + tab }
        } else {
            tabDao.upsertTab(
                TabEntity(
                    id = tab.id,
                    url = tab.url,
                    title = tab.title,
                    isPrivate = false,
                    isDesktopMode = tab.isDesktopMode,
                    isActive = false,
                    createdAt = tab.createdAt,
                    lastAccessedAt = tab.createdAt,
                )
            )
        }
    }

    private suspend fun activateTab(tab: BrowserTab) {
        activeSessions.forEach { (id, session) ->
            session.setPriorityHint(
                if (id == tab.id) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT
            )
        }

        val restoreJson = if (!tab.isPrivate && activeSessions[tab.id] == null) {
            tabDao.getTabById(tab.id)?.sessionStateJson
        } else null

        val session = getOrCreateSessionForTab(
            tabId = tab.id,
            initialUrl = tab.url,
            isPrivate = tab.isPrivate,
            isDesktopMode = tab.isDesktopMode,
            restoreStateJson = restoreJson,
        )
        runCatching { getOrCreateRuntime().webExtensionController.setTabActive(session, true) }
        if (!tab.isPrivate) tabDao.setActiveTab(tab.id)

        val live = liveStates[tab.id] ?: TabLive(url = tab.url, title = tab.title)
        _findState.value = FindInPageState()
        _contextMenu.value = null
        _sessionState.value = ActiveBrowserSessionState(
            activeTabId = tab.id,
            currentUrl = live.url.ifBlank { tab.url },
            pageTitle = live.title.ifBlank { tab.title },
            progress = live.progress,
            isLoading = live.isLoading,
            isSecureHttps = live.isSecure,
            canGoBack = live.canGoBack,
            canGoForward = live.canGoForward,
            isPrivate = tab.isPrivate,
            isDesktopMode = tab.isDesktopMode,
            isFullScreenMedia = live.isFullScreen,
            isWebMediaPlaying = _webMedia.value.isPlaying,
        )
        _activeSession.value = session
    }

    private fun removeSession(tabId: String) {
        val session = activeSessions.remove(tabId)
        liveStates.remove(tabId)
        lastRecordedUrl.remove(tabId)
        pendingSessionStates.remove(tabId)
        mediaSessions.remove(tabId)
        if (mediaStates.remove(tabId) != null) publishWebMedia()
        if (session == null) return

        val attached = _activeSession.value === session
        if (attached) _activeSession.value = null
        engineScope.launch {
            // Give the UI a moment to release its GeckoView before the session is closed.
            if (attached) delay(300)
            runCatching {
                session.setActive(false)
                session.close()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sessions
    // ---------------------------------------------------------------------------------------------

    private fun buildSession(tabId: String, isPrivate: Boolean, isDesktopMode: Boolean): GeckoSession {
        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .suspendMediaWhenInactive(false)
            .build()
        return GeckoSession(sessionSettings).also { attachDelegates(tabId, it, isPrivate) }
    }

    private fun getOrCreateSessionForTab(
        tabId: String,
        initialUrl: String,
        isPrivate: Boolean,
        isDesktopMode: Boolean,
        restoreStateJson: String? = null,
    ): GeckoSession {
        activeSessions[tabId]?.let { existing ->
            if (existing.isOpen) existing.setPriorityHint(GeckoSession.PRIORITY_HIGH)
            return existing
        }

        val session = buildSession(tabId, isPrivate, isDesktopMode)
        session.open(getOrCreateRuntime())
        session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        activeSessions[tabId] = session

        val restored = restoreStateJson?.let { json ->
            runCatching { GeckoSession.SessionState.fromString(json) }.getOrNull()
        }
        if (restored != null) session.restoreState(restored) else session.loadUri(initialUrl)
        return session
    }

    /** Re-creates a tab's session after the content process died. Background tabs are rebuilt lazily. */
    private fun recoverSession(tabId: String) {
        if (tabId == YOUTUBE_SESSION_ID) {
            val lastUrl = _youTubeState.value.url.ifBlank { YOUTUBE_HOME_URL }
            engineScope.launch {
                _youTubeSession.value = null
                runCatching { youTubeGeckoSession?.close() }
                youTubeGeckoSession = null
                liveStates.remove(YOUTUBE_SESSION_ID)
                openYouTube(lastUrl)
            }
            return
        }
        engineScope.launch {
            val tab = tabs.value.firstOrNull { it.id == tabId } ?: return@launch
            val wasActive = tabId == _sessionState.value.activeTabId
            val stale = activeSessions.remove(tabId)
            if (wasActive) _activeSession.value = null
            runCatching { stale?.close() }
            liveStates.remove(tabId)
            if (wasActive) activateTab(tab)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------------------------

    fun loadInputOrUrl(rawInput: String) {
        engineScope.launch {
            ensureInitialized()
            val settings = preferencesDataSource.userSettings.first()
            when (val resolved = urlSanitizer.resolveInput(rawInput, settings.searchEngine, settings.httpsOnlyMode)) {
                is ResolvedNavigationTarget.BlockedUnsafeScheme -> {
                    _sessionState.update {
                        it.copy(securityNotice = resolved.reason, errorDescription = resolved.reason)
                    }
                }
                is ResolvedNavigationTarget.ValidUrl -> {
                    if (resolved.wasUpgradedToHttps) preferencesDataSource.incrementHttpsUpgrades()
                    navigateActiveSession(resolved.url)
                }
                is ResolvedNavigationTarget.SearchQuery -> navigateActiveSession(resolved.searchUrl)
            }
        }
    }

    private suspend fun navigateActiveSession(targetUrl: String) {
        val tabId = _sessionState.value.activeTabId
        val tab = tabs.value.firstOrNull { it.id == tabId }
        if (tab == null) {
            createTabInternal(targetUrl, isPrivate = false, desktop = cachedSettings.desktopSiteDefault)
            return
        }
        val session = activeSessions[tabId]
            ?: getOrCreateSessionForTab(tabId, targetUrl, tab.isPrivate, tab.isDesktopMode).also {
                _activeSession.value = it
            }
        _sessionState.update { it.copy(currentUrl = targetUrl, errorDescription = null, securityNotice = null) }
        session.loadUri(targetUrl)
    }

    fun getActiveGeckoSession(): GeckoSession? = _activeSession.value

    fun goBack() {
        _activeSession.value?.goBack()
    }

    fun goForward() {
        _activeSession.value?.goForward()
    }

    fun reloadOrStop() {
        val session = _activeSession.value ?: return
        if (_sessionState.value.isLoading) session.stop() else session.reload()
    }

    fun exitFullScreen() {
        _activeSession.value?.exitFullScreen()
    }

    fun toggleDesktopSiteMode() {
        val session = _activeSession.value ?: return
        val tabId = _sessionState.value.activeTabId
        val enabled = !_sessionState.value.isDesktopMode
        session.settings.userAgentMode =
            if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        _sessionState.update { it.copy(isDesktopMode = enabled) }
        privateTabs.update { list -> list.map { if (it.id == tabId) it.copy(isDesktopMode = enabled) else it } }
        engineScope.launch {
            if (!_sessionState.value.isPrivate) tabDao.updateDesktopMode(tabId, enabled)
        }
        session.reload()
    }

    // ---------------------------------------------------------------------------------------------
    // Find in page, PDF, context menu
    // ---------------------------------------------------------------------------------------------

    fun findInPage(query: String, backwards: Boolean = false) {
        val session = _activeSession.value ?: return
        if (query.isBlank()) {
            clearFindInPage()
            return
        }
        session.finder
            .find(query, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS else 0)
            .accept(
                { result ->
                    _findState.value = FindInPageState(
                        query = query,
                        found = result?.found ?: false,
                        current = result?.current ?: 0,
                        total = result?.total ?: 0,
                    )
                },
                { err -> Log.w(TAG, "Find in page failed: ${err?.message}") },
            )
    }

    fun clearFindInPage() {
        _activeSession.value?.finder?.clear()
        _findState.value = FindInPageState()
    }

    fun savePageAsPdf() {
        val session = _activeSession.value ?: return
        val state = _sessionState.value
        val baseName = state.pageTitle.ifBlank { "page" }
        session.saveAsPdf().accept(
            { stream ->
                if (stream != null) {
                    downloadManager.enqueue(
                        DownloadRequest(
                            sourceUrl = state.currentUrl,
                            fileNameHint = "$baseName.pdf",
                            mimeType = "application/pdf",
                        ),
                        stream,
                    )
                }
            },
            { err -> Log.w(TAG, "Save as PDF failed: ${err?.message}") },
        )
    }

    fun dismissContextMenu() {
        _contextMenu.value = null
    }

    fun downloadFromUrl(url: String) = downloadManager.enqueueFromUrl(url)

    // ---------------------------------------------------------------------------------------------
    // Web media (Media Session API)
    // ---------------------------------------------------------------------------------------------

    fun toggleWebMedia() {
        val info = _webMedia.value
        val session = mediaSessions[info.tabId] ?: return
        if (info.isPlaying) session.pause() else session.play()
    }

    // ---------------------------------------------------------------------------------------------
    // YouTube section
    // ---------------------------------------------------------------------------------------------

    /** Creates the dedicated YouTube session on first use; loads [url] (or the YouTube home page). */
    fun openYouTube(url: String? = null) {
        engineScope.launch {
            val existing = youTubeGeckoSession
            if (existing != null && existing.isOpen) {
                if (url != null) existing.loadUri(url)
                return@launch
            }
            val session = buildSession(YOUTUBE_SESSION_ID, isPrivate = false, isDesktopMode = false)
            session.open(getOrCreateRuntime())
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
            youTubeGeckoSession = session
            session.loadUri(url ?: YOUTUBE_HOME_URL)
            _youTubeSession.value = session
        }
    }

    fun youTubeGoBack() {
        youTubeGeckoSession?.goBack()
    }

    fun youTubeReload() {
        val session = youTubeGeckoSession ?: return
        if (_youTubeState.value.isLoading) session.stop() else session.reload()
    }

    fun youTubeGoHome() {
        youTubeGeckoSession?.loadUri(YOUTUBE_HOME_URL)
    }

    fun exitYouTubeFullScreen() {
        youTubeGeckoSession?.exitFullScreen()
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
    }

    /**
     * Asks the built-in extension to stretch the playing video over the whole page so that
     * Picture-in-Picture shows only the video instead of a cropped copy of the page.
     * Returns true when the page's content script was reachable.
     */
    fun setWebPipLayout(enabled: Boolean): Boolean {
        val tabId = _webMedia.value.tabId
        val owner = when {
            tabId == YOUTUBE_SESSION_ID -> youTubeGeckoSession
            tabId.isNotEmpty() -> activeSessions[tabId]
            else -> _activeSession.value
        } ?: return false
        val port = extensionPorts[owner] ?: return false
        return runCatching {
            port.postMessage(JSONObject().put("type", "pipLayout").put("enabled", enabled))
            true
        }.getOrDefault(false)
    }

    fun playWebMedia() {
        mediaSessions[_webMedia.value.tabId]?.play()
    }

    fun pauseWebMedia() {
        mediaSessions[_webMedia.value.tabId]?.pause()
    }

    /** Skips to the next item of the page's playlist / suggested video (if the page supports it). */
    fun webNextTrack() {
        mediaSessions[_webMedia.value.tabId]?.nextTrack()
    }

    fun webPreviousTrack() {
        mediaSessions[_webMedia.value.tabId]?.previousTrack()
    }

    fun stopWebMedia() {
        mediaSessions[_webMedia.value.tabId]?.stop()
    }

    private fun publishWebMedia() {
        val all = mediaStates.values
        val chosen = all.firstOrNull { it.isPlaying } ?: all.firstOrNull { it.isActive } ?: WebMediaInfo()
        val previous = _webMedia.value
        _webMedia.value = chosen
        _sessionState.update { it.copy(isWebMediaPlaying = chosen.isPlaying) }
        if (chosen.isPlaying && !previous.isPlaying) startWebMediaService()
    }

    private fun startWebMediaService() {
        // Keeps the process at foreground priority so web audio continues with the screen off.
        runCatching {
            ContextCompat.startForegroundService(appContext, Intent(appContext, AuraWebMediaService::class.java))
        }.onFailure { Log.w(TAG, "Could not start web media service", it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle / persistence
    // ---------------------------------------------------------------------------------------------

    /** Writes pending session snapshots; call when the UI goes to the background. */
    fun persistSessionStates() {
        engineScope.launch { flushPendingStates() }
    }

    private suspend fun flushPendingStates() {
        val snapshot = pendingSessionStates.entries.toList()
        snapshot.forEach { (tabId, state) ->
            pendingSessionStates.remove(tabId, state)
            tabDao.updateSessionState(tabId, state.toString())
        }
    }

    private fun scheduleStateFlush() {
        stateFlushJob?.cancel()
        stateFlushJob = engineScope.launch {
            delay(STATE_FLUSH_DELAY_MS)
            flushPendingStates()
        }
    }

    /** Frees memory held by background tabs; they are restored lazily from their saved state. */
    fun releaseBackgroundSessions() {
        engineScope.launch {
            flushPendingStates()
            val activeId = _sessionState.value.activeTabId
            val restorable = tabs.value.filter { !it.isPrivate }.map { it.id }.toSet()
            activeSessions.keys
                .filter { it != activeId && it in restorable && mediaStates[it]?.isActive != true }
                .forEach { id ->
                    activeSessions.remove(id)?.let { runCatching { it.setActive(false); it.close() } }
                    liveStates.remove(id)
                }
        }
    }

    /** Called when the app is closing for good. Honors the "clear on exit" setting. */
    fun onAppExit() {
        engineScope.launch {
            flushPendingStates()
            if (!preferencesDataSource.userSettings.first().clearSessionOnExit) return@launch
            _activeSession.value = null
            activeSessions.keys.toList().forEach { removeSession(it) }
            _youTubeSession.value = null
            runCatching { youTubeGeckoSession?.close() }
            youTubeGeckoSession = null
            privateTabs.value = emptyList()
            clearPersistedBrowsingData()
            _sessionState.value = ActiveBrowserSessionState()
            initialized = false
        }
    }

    private suspend fun clearPersistedBrowsingData() {
        tabDao.deleteAllTabs()
        historyDao.clearAllHistory()
        geckoRuntime?.storageController?.clearData(StorageController.ClearFlags.ALL)
    }

    fun clearSiteDataAndCookies() {
        getOrCreateRuntime().storageController.clearData(StorageController.ClearFlags.ALL)
    }

    // ---------------------------------------------------------------------------------------------
    // Delegates
    // ---------------------------------------------------------------------------------------------

    private fun updateLive(tabId: String, transform: (TabLive) -> TabLive) {
        val updated = transform(liveStates[tabId] ?: TabLive())
        liveStates[tabId] = updated
        if (tabId == YOUTUBE_SESSION_ID) {
            _youTubeState.value = YouTubeAppState(
                url = updated.url,
                title = updated.title,
                progress = updated.progress,
                isLoading = updated.isLoading,
                canGoBack = updated.canGoBack,
                isFullScreen = updated.isFullScreen,
            )
            return
        }
        if (tabId != _sessionState.value.activeTabId) return
        _sessionState.update {
            it.copy(
                currentUrl = updated.url.ifBlank { it.currentUrl },
                pageTitle = updated.title.ifBlank { updated.url.ifBlank { it.pageTitle } },
                progress = updated.progress,
                isLoading = updated.isLoading,
                isSecureHttps = updated.isSecure,
                canGoBack = updated.canGoBack,
                canGoForward = updated.canGoForward,
                isFullScreenMedia = updated.isFullScreen,
            )
        }
    }

    private fun attachDelegates(tabId: String, session: GeckoSession, isPrivate: Boolean) {
        session.promptDelegate = promptDelegate
        session.permissionDelegate = permissionDelegate

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val safeUrl = url ?: return
                if (tabId == YOUTUBE_SESSION_ID) {
                    updateLive(tabId) { it.copy(url = safeUrl) }
                    return
                }
                updateLive(tabId) {
                    it.copy(
                        url = safeUrl,
                        isSecure = safeUrl.startsWith("https://", ignoreCase = true) || safeUrl.startsWith("about:"),
                    )
                }
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.update { it.copy(errorDescription = null) }
                }

                if (isPrivate) {
                    privateTabs.update { list -> list.map { if (it.id == tabId) it.copy(url = safeUrl) else it } }
                    return
                }
                engineScope.launch {
                    tabDao.updateTabUrl(tabId, safeUrl)
                    if (safeUrl.startsWith("http") && lastRecordedUrl.put(tabId, safeUrl) != safeUrl) {
                        // Title arrives later through onTitleChange and is written to this row then.
                        historyDao.recordVisit(title = "", url = safeUrl)
                    }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                updateLive(tabId) { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                updateLive(tabId) { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                return when (uri.substringBefore(':', "").lowercase()) {
                    "javascript", "vbscript", "jar", "intent" -> GeckoResult.fromValue(AllowOrDeny.DENY)
                    "mailto", "tel", "sms", "geo" -> {
                        if (request.hasUserGesture) openExternally(uri)
                        GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    else -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (tabId == YOUTUBE_SESSION_ID) {
                    // Stay inside the YouTube section for YouTube links; send everything else to a browser tab.
                    if (isYouTubeUrl(uri)) session.loadUri(uri) else openNewTab(uri)
                    return null
                }
                // target=_blank links and window.open(): the engine opens the returned session itself.
                val newTabId = UUID.randomUUID().toString()
                val newSession = buildSession(newTabId, isPrivate, isDesktopMode = false)
                activeSessions[newTabId] = newSession
                val tab = BrowserTab(
                    id = newTabId,
                    url = uri,
                    title = if (isPrivate) "Private Tab" else "New Tab",
                    isPrivate = isPrivate,
                    isDesktopMode = false,
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                )
                engineScope.launch {
                    registerTab(tab)
                    // Wait for the engine to open the session before showing it.
                    var attempts = 0
                    while (!newSession.isOpen && attempts < 100) {
                        delay(20)
                        attempts++
                    }
                    if (newSession.isOpen) activateTab(tab)
                }
                return GeckoResult.fromValue(newSession)
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError,
            ): GeckoResult<String>? {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.update {
                        it.copy(
                            isLoading = false,
                            errorDescription = "Unable to load ${uri ?: "page"} (error ${error.code})",
                        )
                    }
                }
                return GeckoResult.fromValue(buildErrorPageDataUrl(uri, error))
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                updateLive(tabId) { it.copy(isLoading = true, progress = 10) }
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.update { it.copy(errorDescription = null) }
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                updateLive(tabId) { it.copy(progress = progress.coerceIn(0, 100), isLoading = progress < 100) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                updateLive(tabId) { it.copy(isLoading = false, progress = 100) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                updateLive(tabId) { it.copy(isSecure = securityInfo.isSecure) }
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState,
            ) {
                if (isPrivate || tabId == YOUTUBE_SESSION_ID) return
                pendingSessionStates[tabId] = sessionState
                scheduleStateFlush()
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val live = liveStates[tabId]
                val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: live?.url.orEmpty()
                if (tabId == YOUTUBE_SESSION_ID) {
                    updateLive(tabId) { it.copy(title = resolvedTitle) }
                    return
                }
                updateLive(tabId) { it.copy(title = resolvedTitle) }

                if (isPrivate) {
                    privateTabs.update { list -> list.map { if (it.id == tabId) it.copy(title = resolvedTitle) else it } }
                    return
                }
                val pageUrl = live?.url.orEmpty()
                engineScope.launch {
                    tabDao.updateTabTitle(tabId, resolvedTitle)
                    if (pageUrl.startsWith("http") && !title.isNullOrBlank()) {
                        historyDao.updateTitleByUrl(pageUrl, title)
                    }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                updateLive(tabId) { it.copy(isFullScreen = fullScreen) }
            }

            override fun onCloseRequest(session: GeckoSession) {
                if (tabId == YOUTUBE_SESSION_ID) return
                closeTab(tabId)
            }

            override fun onCrash(session: GeckoSession) {
                recoverSession(tabId)
            }

            override fun onKill(session: GeckoSession) {
                recoverSession(tabId)
            }

            override fun onContextMenu(
                session: GeckoSession,
                screenX: Int,
                screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement,
            ) {
                if (tabId != _sessionState.value.activeTabId) return
                val link = element.linkUri?.takeIf { it.startsWith("http", ignoreCase = true) }
                val image = if (element.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE) {
                    element.srcUri?.takeIf { it.startsWith("https://", ignoreCase = true) }
                } else null
                if (link == null && image == null) return
                _contextMenu.value = ContextMenuTarget(linkUrl = link, imageUrl = image, title = element.title)
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val body = response.body ?: return
                fun header(name: String): String? =
                    response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

                downloadManager.enqueue(
                    DownloadRequest(
                        sourceUrl = response.uri,
                        contentDisposition = header("Content-Disposition"),
                        mimeType = header("Content-Type"),
                        contentLength = header("Content-Length")?.toLongOrNull() ?: -1L,
                    ),
                    body,
                )
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                // Batched: a single page can trigger hundreds of events.
                pendingTrackerCount.incrementAndGet()
                if (trackerFlushJob?.isActive != true) {
                    trackerFlushJob = engineScope.launch {
                        delay(TRACKER_FLUSH_DELAY_MS)
                        val count = pendingTrackerCount.getAndSet(0)
                        if (count > 0) preferencesDataSource.incrementBlockedTrackers(count)
                    }
                }
            }
        }

        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                mediaSessions[tabId] = mediaSession
                mediaStates[tabId] = (mediaStates[tabId] ?: WebMediaInfo(tabId = tabId)).copy(isActive = true)
                publishWebMedia()
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                mediaSessions.remove(tabId)
                mediaStates.remove(tabId)
                publishWebMedia()
            }

            override fun onMetadata(
                session: GeckoSession,
                mediaSession: MediaSession,
                meta: MediaSession.Metadata,
            ) {
                val current = mediaStates[tabId] ?: return
                mediaStates[tabId] = current.copy(title = meta.title.orEmpty(), artist = meta.artist.orEmpty())
                publishWebMedia()
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                mediaStates[tabId] = (mediaStates[tabId] ?: WebMediaInfo(tabId = tabId)).copy(isActive = true, isPlaying = true)
                publishWebMedia()
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                mediaStates[tabId]?.let { mediaStates[tabId] = it.copy(isPlaying = false) }
                publishWebMedia()
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                mediaSessions.remove(tabId)
                mediaStates.remove(tabId)
                publishWebMedia()
            }
        }
    }

    private fun openExternally(uri: String) {
        runCatching {
            appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "No app can handle $uri") }
    }

    private fun buildErrorPageDataUrl(failedUri: String?, error: WebRequestError): String {
        val (heading, detail) = when (error.category) {
            WebRequestError.ERROR_CATEGORY_NETWORK ->
                "No connection" to "Aura couldn\u2019t reach this site. Check your internet connection and try again."
            WebRequestError.ERROR_CATEGORY_SECURITY ->
                "Connection not secure" to "Aura blocked this page because a secure connection couldn\u2019t be established."
            WebRequestError.ERROR_CATEGORY_URI ->
                "Invalid address" to "The address looks malformed. Check it for typos."
            WebRequestError.ERROR_CATEGORY_SAFEBROWSING ->
                "Dangerous site blocked" to "This page was reported as unsafe and has been blocked."
            else ->
                "Page couldn\u2019t load" to "Something went wrong while loading this page."
        }
        val safeUri = TextUtils.htmlEncode(failedUri ?: "")
        val retryButton = if (failedUri.isNullOrBlank()) "" else """<a class="btn" href="$safeUri">Try again</a>"""
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { background:#090F19; color:#F3F7FC; font-family:sans-serif; display:flex;
                           align-items:center; justify-content:center; min-height:92vh; margin:0; padding:24px; }
                    .card { background:#172538; border:1px solid rgba(255,255,255,0.16); border-radius:22px;
                            padding:28px; max-width:420px; text-align:center; }
                    h2 { color:#2781E7; margin-top:0; }
                    p { color:#A8B8CC; line-height:1.5; word-break:break-all; }
                    small { color:#6F8299; }
                    .btn { display:inline-block; background:#2781E7; color:#fff; text-decoration:none;
                           border-radius:16px; padding:12px 24px; font-weight:600; margin-top:12px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>${TextUtils.htmlEncode(heading)}</h2>
                    <p>${TextUtils.htmlEncode(detail)}</p>
                    <p><small>$safeUri</small></p>
                    <p><small>Error ${error.code}</small></p>
                    $retryButton
                </div>
            </body>
            </html>
        """.trimIndent()
        return "data:text/html;charset=utf-8," + Uri.encode(html)
    }

    private data class TabLive(
        val url: String = "",
        val title: String = "",
        val progress: Int = 0,
        val isLoading: Boolean = false,
        val isSecure: Boolean = true,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val isFullScreen: Boolean = false,
    )

    private fun TabEntity.toBrowserTab() = BrowserTab(
        id = id,
        url = url,
        title = title,
        isPrivate = isPrivate,
        isDesktopMode = isDesktopMode,
        isActive = false,
        createdAt = createdAt,
    )

    private companion object {
        const val TAG = "AuraGeckoEngine"
        const val YOUTUBE_SESSION_ID = "aura-youtube-app"
        const val YOUTUBE_HOME_URL = "https://m.youtube.com/"
        const val STATE_FLUSH_DELAY_MS = 3_000L
        const val TRACKER_FLUSH_DELAY_MS = 2_000L
    }
}
