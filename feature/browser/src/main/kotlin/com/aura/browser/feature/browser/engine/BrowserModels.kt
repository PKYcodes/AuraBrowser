package com.aura.browser.feature.browser.engine

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** UI-facing snapshot of the currently active tab. */
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

/** A tab as shown in the tab switcher. Private tabs only ever exist in memory. */
data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val isDesktopMode: Boolean,
    val isActive: Boolean,
    val createdAt: Long,
)

/** State of the audio/video element a web page is playing through the Media Session API. */
data class WebMediaInfo(
    val tabId: String = "",
    val title: String = "",
    val artist: String = "",
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
)

data class FindInPageState(
    val query: String = "",
    val found: Boolean = true,
    val current: Int = 0,
    val total: Int = 0,
)

data class ContextMenuTarget(
    val linkUrl: String?,
    val imageUrl: String?,
    val title: String?,
)

enum class SitePermissionKind(val label: String) {
    LOCATION("your location"),
    NOTIFICATIONS("send notifications"),
    CAMERA("your camera"),
    MICROPHONE("your microphone"),
}

data class ChoiceOption(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val selected: Boolean,
    val isHeader: Boolean = false,
    val isSeparator: Boolean = false,
)

/**
 * A dialog the web engine needs the user to answer. Each prompt carries its own completion callback;
 * the UI must invoke exactly one of them and then call [BrowserPromptQueue.remove].
 */
sealed interface BrowserPrompt {
    val id: Long

    data class Alert(
        override val id: Long,
        val title: String,
        val message: String,
        val onDismiss: () -> Unit,
    ) : BrowserPrompt

    data class Confirm(
        override val id: Long,
        val title: String,
        val message: String,
        val onResult: (Boolean) -> Unit,
    ) : BrowserPrompt

    data class TextInput(
        override val id: Long,
        val title: String,
        val message: String,
        val defaultValue: String,
        val onResult: (String?) -> Unit,
    ) : BrowserPrompt

    data class Choice(
        override val id: Long,
        val title: String,
        val options: List<ChoiceOption>,
        val multiple: Boolean,
        val onResult: (List<String>?) -> Unit,
    ) : BrowserPrompt

    data class FilePick(
        override val id: Long,
        val mimeTypes: List<String>,
        val multiple: Boolean,
        val onResult: (List<Uri>?) -> Unit,
    ) : BrowserPrompt

    data class Auth(
        override val id: Long,
        val title: String,
        val message: String,
        val onResult: (username: String?, password: String?) -> Unit,
    ) : BrowserPrompt

    data class SitePermission(
        override val id: Long,
        val origin: String,
        val kinds: List<SitePermissionKind>,
        val onResult: (allow: Boolean, remember: Boolean) -> Unit,
    ) : BrowserPrompt

    data class AndroidPermissions(
        override val id: Long,
        val permissions: List<String>,
        val onResult: (allGranted: Boolean) -> Unit,
    ) : BrowserPrompt
}

/** Ordered queue of pending [BrowserPrompt]s; the UI always renders the head. */
class BrowserPromptQueue {
    private val ids = AtomicLong(0)
    private val _prompts = MutableStateFlow<List<BrowserPrompt>>(emptyList())
    val prompts: StateFlow<List<BrowserPrompt>> = _prompts.asStateFlow()

    fun nextId(): Long = ids.incrementAndGet()
    fun enqueue(prompt: BrowserPrompt) = _prompts.update { it + prompt }
    fun remove(id: Long) = _prompts.update { list -> list.filterNot { it.id == id } }
}
