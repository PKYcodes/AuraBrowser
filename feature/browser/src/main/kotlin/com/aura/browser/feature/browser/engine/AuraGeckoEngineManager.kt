package com.aura.browser.feature.browser.engine

import android.content.Context
import android.util.Log
import com.aura.browser.core.common.model.TrackingProtectionPreset
import com.aura.browser.core.database.dao.DownloadDao
import com.aura.browser.core.database.dao.HistoryDao
import com.aura.browser.core.database.dao.SitePermissionDao
import com.aura.browser.core.database.dao.TabDao
import com.aura.browser.core.database.entity.DownloadEntity
import com.aura.browser.core.database.entity.DownloadStatus
import com.aura.browser.core.database.entity.TabEntity
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.network.download.AuraDownloadValidator
import com.aura.browser.core.network.url.AuraUrlSanitizer
import com.aura.browser.core.network.url.ResolvedNavigationTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveBrowserSessionState(
    val activeTabId: String = "",
    val currentUrl: String = "https://duckduckgo.com",
    val pageTitle: String = "Aura Browser",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val isSecureHttps: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isFullScreenMedia: Boolean = false,
    val isWebMediaPlaying: Boolean = false,
    val errorDescription: String? = null,
    val securityNotice: String? = null,
)

/**
 * Process-wide singleton managing [GeckoRuntime] and active [GeckoSession] instances per tab.
 * Prevents memory leaks by binding a single [GeckoRuntime] to application Context and closing
 * [GeckoSession] instances cleanly when tabs are closed.
 */
@Singleton
class AuraGeckoEngineManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val tabDao: TabDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val sitePermissionDao: SitePermissionDao,
    private val preferencesDataSource: AuraPreferencesDataSource,
    private val urlSanitizer: AuraUrlSanitizer,
    private val downloadValidator: AuraDownloadValidator,
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeSessions = ConcurrentHashMap<String, GeckoSession>()

    @Volatile
    private var geckoRuntime: GeckoRuntime? = null

    private val _sessionState = MutableStateFlow(ActiveBrowserSessionState())
    val sessionState: StateFlow<ActiveBrowserSessionState> = _sessionState.asStateFlow()

    /**
     * Lazily initializes a single process-lifetime [GeckoRuntime] instance.
     * Per Mozilla GeckoView documentation, GeckoRuntime may only be created once per process.
     */
    @Synchronized
    fun getOrCreateRuntime(): GeckoRuntime {
        geckoRuntime?.let { return it }

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .consoleOutput(false)
            .javaScriptEnabled(true)
            .automaticFontSizeAdjustment(true)
            .enterpriseRootsEnabled(false)
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(
                        ContentBlocking.AntiTracking.DEFAULT or
                            ContentBlocking.AntiTracking.STP
                    )
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                    .httpsOnlyMode(ContentBlocking.HttpsOnlyMode.HTTPS_ONLY_ENABLED)
                    .build()
            )
            .build()

        val createdRuntime = GeckoRuntime.create(appContext, runtimeSettings)
        geckoRuntime = createdRuntime
        ensureBuiltInMediaWebExtension(createdRuntime)
        return createdRuntime
    }

    private fun ensureBuiltInMediaWebExtension(runtime: GeckoRuntime) {
        runtime.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/extensions/aura-media-enhancer/",
                "aura-media-enhancer@aura.browser",
            )
            .accept(
                { ext -> Log.i("AuraGeckoEngine", "Built-in extension ready: ${ext?.id}") },
                { err -> Log.w("AuraGeckoEngine", "Built-in extension registration note: ${err?.message}") },
            )
    }

    fun getOrCreateSessionForTab(
        tabId: String,
        initialUrl: String = "https://duckduckgo.com",
        isPrivate: Boolean = false,
        isDesktopMode: Boolean = false,
    ): GeckoSession {
        activeSessions[tabId]?.let { existing ->
            existing.setPriorityHint(GeckoSession.PRIORITY_HIGH)
            return existing
        }

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

        val session = GeckoSession(sessionSettings)
        attachDelegates(tabId, session, isPrivate)
        session.open(getOrCreateRuntime())
        session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        activeSessions[tabId] = session
        session.loadUri(initialUrl)
        return session
    }

    fun ensureDefaultTabAndLoad(initialUrl: String? = null) {
        engineScope.launch {
            val settings = preferencesDataSource.userSettings.first()
            val existingTabs = tabDao.observeAllTabs().first()
            val targetUrl = initialUrl ?: settings.homePageUrl

            if (existingTabs.isEmpty()) {
                val newTabId = UUID.randomUUID().toString()
                val entity = TabEntity(
                    id = newTabId,
                    url = targetUrl,
                    title = "New Tab",
                    isPrivate = false,
                    isDesktopMode = settings.desktopSiteDefault,
                    isActive = true,
                )
                tabDao.upsertTab(entity)
                switchToTab(entity)
            } else {
                val active = existingTabs.firstOrNull { it.isActive } ?: existingTabs.first()
                switchToTab(active)
                if (!initialUrl.isNullOrBlank()) {
                    loadInputOrUrl(initialUrl)
                }
            }
        }
    }

    fun openNewTab(url: String? = null, isPrivate: Boolean = false) {
        engineScope.launch {
            val settings = preferencesDataSource.userSettings.first()
            val resolvedUrl = url ?: settings.homePageUrl
            val newTabId = UUID.randomUUID().toString()
            val newTab = TabEntity(
                id = newTabId,
                url = resolvedUrl,
                title = if (isPrivate) "Private Tab" else "New Tab",
                isPrivate = isPrivate,
                isDesktopMode = settings.desktopSiteDefault,
                isActive = true,
            )
            tabDao.upsertTab(newTab)
            tabDao.setActiveTab(newTabId)
            switchToTab(newTab)
        }
    }

    fun switchToTab(tab: TabEntity) {
        engineScope.launch {
            activeSessions.forEach { (id, session) ->
                session.setPriorityHint(
                    if (id == tab.id) GeckoSession.PRIORITY_HIGH else GeckoSession.PRIORITY_DEFAULT
                )
            }
            val session = getOrCreateSessionForTab(
                tabId = tab.id,
                initialUrl = tab.url,
                isPrivate = tab.isPrivate,
                isDesktopMode = tab.isDesktopMode,
            )
            runCatching {
                getOrCreateRuntime().webExtensionController.setTabActive(session, true)
            }
            tabDao.setActiveTab(tab.id)
            _sessionState.value = _sessionState.value.copy(
                activeTabId = tab.id,
                currentUrl = tab.url,
                pageTitle = tab.title,
                isPrivate = tab.isPrivate,
                isDesktopMode = tab.isDesktopMode,
                errorDescription = null,
            )
        }
    }

    fun closeTab(tabId: String) {
        engineScope.launch {
            activeSessions.remove(tabId)?.let { session ->
                session.setActive(false)
                session.close()
            }
            tabDao.deleteTabById(tabId)
            val remaining = tabDao.observeAllTabs().first()
            if (remaining.isEmpty()) {
                openNewTab()
            } else {
                switchToTab(remaining.first())
            }
        }
    }

    fun closeAllPrivateTabs() {
        engineScope.launch {
            val allTabs = tabDao.observeAllTabs().first()
            allTabs.filter { it.isPrivate }.forEach { privateTab ->
                activeSessions.remove(privateTab.id)?.close()
            }
            tabDao.deletePrivateTabs()
            getOrCreateRuntime().storageController.clearData(StorageController.ClearFlags.ALL_CACHES)
            val remaining = tabDao.observeAllTabs().first()
            if (remaining.isEmpty()) openNewTab() else switchToTab(remaining.first())
        }
    }

    fun loadInputOrUrl(rawInput: String) {
        engineScope.launch {
            val settings = preferencesDataSource.userSettings.first()
            val resolved = urlSanitizer.resolveInput(
                rawInput = rawInput,
                searchEngine = settings.searchEngine,
                httpsOnlyMode = settings.httpsOnlyMode,
            )
            when (resolved) {
                is ResolvedNavigationTarget.BlockedUnsafeScheme -> {
                    _sessionState.value = _sessionState.value.copy(
                        securityNotice = resolved.reason,
                        errorDescription = resolved.reason,
                    )
                }
                is ResolvedNavigationTarget.ValidUrl -> {
                    if (resolved.wasUpgradedToHttps) {
                        preferencesDataSource.incrementHttpsUpgrades()
                    }
                    navigateActiveSession(resolved.url)
                }
                is ResolvedNavigationTarget.SearchQuery -> {
                    navigateActiveSession(resolved.searchUrl)
                }
            }
        }
    }

    private fun navigateActiveSession(targetUrl: String) {
        val currentTabId = _sessionState.value.activeTabId
        if (currentTabId.isBlank()) {
            ensureDefaultTabAndLoad(targetUrl)
            return
        }
        val session = getOrCreateSessionForTab(
            tabId = currentTabId,
            initialUrl = targetUrl,
            isPrivate = _sessionState.value.isPrivate,
            isDesktopMode = _sessionState.value.isDesktopMode,
        )
        _sessionState.value = _sessionState.value.copy(
            currentUrl = targetUrl,
            errorDescription = null,
            securityNotice = null,
        )
        session.loadUri(targetUrl)
    }

    fun getActiveGeckoSession(): GeckoSession? {
        val tabId = _sessionState.value.activeTabId
        if (tabId.isBlank()) return null
        return activeSessions[tabId]
    }

    fun goBack() {
        getActiveGeckoSession()?.goBack()
    }

    fun goForward() {
        getActiveGeckoSession()?.goForward()
    }

    fun reloadOrStop() {
        val session = getActiveGeckoSession() ?: return
        if (_sessionState.value.isLoading) {
            session.stop()
        } else {
            session.reload()
        }
    }

    fun toggleDesktopSiteMode() {
        val session = getActiveGeckoSession() ?: return
        val newDesktopState = !_sessionState.value.isDesktopMode
        session.settings.userAgentMode = if (newDesktopState) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.settings.viewportMode = if (newDesktopState) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        _sessionState.value = _sessionState.value.copy(isDesktopMode = newDesktopState)
        session.reload()
    }

    fun applyPrivacySettings(preset: TrackingProtectionPreset, httpsOnly: Boolean) {
        val runtime = getOrCreateRuntime()
        val antiTrackingFlags = when (preset) {
            TrackingProtectionPreset.STANDARD -> ContentBlocking.AntiTracking.DEFAULT
            TrackingProtectionPreset.STRICT -> ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.STP
            TrackingProtectionPreset.CUSTOM -> ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP
        }
        runtime.settings.contentBlocking.setAntiTracking(antiTrackingFlags)
        runtime.settings.contentBlocking.setHttpsOnlyMode(
            if (httpsOnly) ContentBlocking.HttpsOnlyMode.HTTPS_ONLY_ENABLED
            else ContentBlocking.HttpsOnlyMode.HTTPS_ONLY_DISABLED
        )
    }

    fun clearSiteDataAndCookies() {
        getOrCreateRuntime().storageController.clearData(StorageController.ClearFlags.ALL)
    }

    private fun attachDelegates(tabId: String, session: GeckoSession, isPrivate: Boolean) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                val safeUrl = url ?: return
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(
                        currentUrl = safeUrl,
                        isSecureHttps = safeUrl.startsWith("https://", ignoreCase = true) || safeUrl.startsWith("about:"),
                        errorDescription = null,
                    )
                }
                engineScope.launch {
                    val existing = tabDao.observeAllTabs().first().firstOrNull { it.id == tabId }
                    if (existing != null) {
                        tabDao.upsertTab(existing.copy(url = safeUrl, lastAccessedAt = System.currentTimeMillis()))
                    }
                    if (!isPrivate && safeUrl.startsWith("http")) {
                        historyDao.recordVisit(title = _sessionState.value.pageTitle, url = safeUrl)
                    }
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(canGoBack = canGoBack)
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(canGoForward = canGoForward)
                }
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<org.mozilla.geckoview.AllowOrDeny> {
                val uri = request.uri
                if (uri.startsWith("javascript:", ignoreCase = true) || uri.startsWith("vbscript:", ignoreCase = true)) {
                    return GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.ALLOW)
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: org.mozilla.geckoview.WebRequestError,
            ): GeckoResult<String>? {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(
                        isLoading = false,
                        errorDescription = "Unable to load ${uri ?: "page"} (Error code: ${error.code})",
                    )
                }
                val fallbackHtml = buildOfflineErrorDataUrl(uri ?: "Unknown URL", error.code)
                return GeckoResult.fromValue(fallbackHtml)
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(
                        isLoading = true,
                        progress = 10,
                        errorDescription = null,
                    )
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(
                        progress = progress.coerceIn(0, 100),
                        isLoading = progress < 100,
                    )
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(
                        isLoading = false,
                        progress = 100,
                    )
                }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(
                        isSecureHttps = securityInfo.isSecure,
                    )
                }
            }

            override fun onSessionStateChange(
                session: GeckoSession,
                sessionState: GeckoSession.SessionState,
            ) {
                engineScope.launch {
                    val existing = tabDao.observeAllTabs().first().firstOrNull { it.id == tabId }
                    if (existing != null && !isPrivate) {
                        tabDao.upsertTab(existing.copy(sessionStateJson = sessionState.toString()))
                    }
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: _sessionState.value.currentUrl
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(pageTitle = resolvedTitle)
                }
                engineScope.launch {
                    val existing = tabDao.observeAllTabs().first().firstOrNull { it.id == tabId }
                    if (existing != null) {
                        tabDao.upsertTab(existing.copy(title = resolvedTitle))
                    }
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (tabId == _sessionState.value.activeTabId) {
                    _sessionState.value = _sessionState.value.copy(isFullScreenMedia = fullScreen)
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                engineScope.launch {
                    val contentType = response.headers["Content-Type"] ?: "application/octet-stream"
                    val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
                    val validated = downloadValidator.validateDownload(
                        rawFileName = response.uri.substringAfterLast('/'),
                        sourceUrl = response.uri,
                        mimeType = contentType,
                    )
                    downloadDao.insertDownload(
                        DownloadEntity(
                            fileName = validated.sanitizedFileName,
                            sourceUrl = response.uri,
                            mimeType = validated.mimeType,
                            destinationUri = "content://downloads/public_downloads/${validated.sanitizedFileName}",
                            totalBytes = contentLength,
                            downloadedBytes = if (contentLength > 0) contentLength else 0L,
                            status = DownloadStatus.COMPLETED,
                        )
                    )
                }
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                engineScope.launch {
                    preferencesDataSource.incrementBlockedTrackers()
                }
            }
        }

        session.mediaSessionDelegate = object : org.mozilla.geckoview.MediaSession.Delegate {
            override fun onActivated(
                session: GeckoSession,
                mediaSession: org.mozilla.geckoview.MediaSession,
            ) {
                _sessionState.value = _sessionState.value.copy(isWebMediaPlaying = true)
            }

            override fun onDeactivated(
                session: GeckoSession,
                mediaSession: org.mozilla.geckoview.MediaSession,
            ) {
                _sessionState.value = _sessionState.value.copy(isWebMediaPlaying = false)
            }

            override fun onPlay(
                session: GeckoSession,
                mediaSession: org.mozilla.geckoview.MediaSession,
            ) {
                _sessionState.value = _sessionState.value.copy(isWebMediaPlaying = true)
            }

            override fun onPause(
                session: GeckoSession,
                mediaSession: org.mozilla.geckoview.MediaSession,
            ) {
                _sessionState.value = _sessionState.value.copy(isWebMediaPlaying = false)
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> {
                val origin = urlSanitizer.extractOrigin(perm.uri)
                val result = GeckoResult<Int>()
                engineScope.launch {
                    val saved = sitePermissionDao.getPermissionForOrigin(origin)
                    val allowed = when (perm.permission) {
                        GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                        GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                        GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> true
                        GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> saved?.locationAllowed == true
                        GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> saved?.notificationsAllowed == true
                        else -> false
                    }
                    result.complete(
                        if (allowed) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    )
                }
                return result
            }
        }
    }

    private fun buildOfflineErrorDataUrl(failedUri: String, errorCode: Int): String {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        background: #090F19;
                        color: #F3F7FC;
                        font-family: sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        height: 92vh;
                        margin: 0;
                        padding: 24px;
                    }
                    .card {
                        background: #172538;
                        border: 1px solid rgba(255,255,255,0.16);
                        border-radius: 22px;
                        padding: 28px;
                        max-width: 420px;
                        text-align: center;
                    }
                    h2 { color: #2781E7; margin-top: 0; }
                    p { color: #A8B8CC; line-height: 1.5; word-break: break-all; }
                    button {
                        background: #2781E7;
                        color: #fff;
                        border: none;
                        border-radius: 16px;
                        padding: 12px 24px;
                        font-weight: 600;
                        margin-top: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Connection Interrupted</h2>
                    <p>Aura Browser could not reach <strong>$failedUri</strong>.</p>
                    <p>Gecko Error Code: $errorCode</p>
                    <button onclick="window.location.reload()">Retry Connection</button>
                </div>
            </body>
            </html>
        """.trimIndent()
        return "data:text/html;charset=utf-8," + android.net.Uri.encode(html)
    }
}
