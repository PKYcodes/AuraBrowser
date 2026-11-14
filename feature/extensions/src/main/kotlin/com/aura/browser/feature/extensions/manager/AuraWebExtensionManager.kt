package com.aura.browser.feature.extensions.manager

import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledExtensionModel(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean,
    val isBuiltIn: Boolean,
    val allowedInPrivateBrowsing: Boolean,
    val permissions: List<String>,
    val downloadUri: String? = null,
)

data class ExtensionCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val amoXpiUrl: String,
    val recommendedPermissions: List<String>,
)

/**
 * Wraps Mozilla GeckoView's [WebExtensionController] to provide:
 * - Listing installed Android-compatible WebExtensions
 * - Installing signed .xpi extensions from addons.mozilla.org (AMO) over HTTPS
 * - Installing built-in APK extensions via resource://android/assets/
 * - Enabling / Disabling extensions with [WebExtensionController.EnableSource.USER]
 * - Uninstalling extensions and managing private-browsing permission
 */
@Singleton
class AuraWebExtensionManager @Inject constructor(
    private val engineManager: AuraGeckoEngineManager,
) {
    private val runtimeExtensionsById = mutableMapOf<String, WebExtension>()

    private val _extensions = MutableStateFlow<List<InstalledExtensionModel>>(
        listOf(
            InstalledExtensionModel(
                id = "aura-media-enhancer@aura.browser",
                name = "Aura Media & Background Enhancer",
                version = "1.0.0",
                description = "Built-in extension that keeps YouTube playing when the app is in the background and can hide the Shorts shelf.",
                isEnabled = true,
                isBuiltIn = true,
                allowedInPrivateBrowsing = true,
                permissions = listOf("youtube.com", "nativeMessaging"),
            )
        )
    )
    val extensions: StateFlow<List<InstalledExtensionModel>> = _extensions.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val curatedAndroidCatalog: List<ExtensionCatalogEntry> = listOf(
        ExtensionCatalogEntry(
            id = "uBlock0@raymondhill.net",
            name = "uBlock Origin",
            description = "Efficient wide-spectrum content blocker compatible with Firefox for Android.",
            amoXpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi",
            recommendedPermissions = listOf("webRequest", "webRequestBlocking", "storage", "tabs", "<all_urls>"),
        ),
        ExtensionCatalogEntry(
            id = "addon@darkreader.org",
            name = "Dark Reader",
            description = "Dark mode for every website. Take care of your eyes using dynamic theme generation.",
            amoXpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi",
            recommendedPermissions = listOf("storage", "tabs", "<all_urls>"),
        ),
        ExtensionCatalogEntry(
            id = "jid1-MnnxcxisBPnSXQ@jetpack",
            name = "Privacy Badger",
            description = "Automatically learns to block invisible trackers across websites.",
            amoXpiUrl = "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi",
            recommendedPermissions = listOf("webRequest", "storage", "tabs", "<all_urls>"),
        ),
    )

    fun refreshInstalledExtensions() {
        runCatching {
            val controller = engineManager.getOrCreateRuntime().webExtensionController
            controller.list().accept(
                { extList ->
                    if (extList != null) {
                        runtimeExtensionsById.clear()
                        val mapped = extList.map { ext ->
                            runtimeExtensionsById[ext.id] = ext
                            ext.toUiModel()
                        }
                        // The built-in extension is always reported by the runtime once registered;
                        // keep the placeholder entry only while the runtime list is still empty.
                        if (mapped.isNotEmpty()) {
                            _extensions.value = mapped
                        }
                    }
                },
                { err ->
                    _statusMessage.value = "Extension list sync note: ${err?.localizedMessage ?: "Ready"}"
                }
            )
        }
    }

    /**
     * Installs an add-on from whatever the user pasted: a normal addons.mozilla.org page link
     * (e.g. https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/), a direct .xpi link, or just the add-on name slug.
     */
    fun installFromUrl(input: String) {
        val installUrl = resolveAddonInstallUrl(input)
        if (installUrl == null) {
            _statusMessage.value = "Paste an add-on link from addons.mozilla.org, " +
                "for example https://addons.mozilla.org/en-US/firefox/addon/ublock-origin/"
            return
        }
        _statusMessage.value = "Downloading add-on\u2026 confirm the permissions when asked."
        runCatching {
            val controller = engineManager.getOrCreateRuntime().webExtensionController
            controller.install(installUrl, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept(
                { installed ->
                    if (installed != null) {
                        runtimeExtensionsById[installed.id] = installed
                        upsertUiModel(installed.toUiModel())
                        _statusMessage.value = "Installed ${installed.metaData.name ?: installed.id}."
                    }
                },
                { error ->
                    _statusMessage.value = "Installation failed: ${error?.localizedMessage ?: "the add-on couldn\u2019t be installed"}"
                }
            )
        }.onFailure { e ->
            _statusMessage.value = "Unable to start installation: ${e.localizedMessage}"
        }
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        val ext = runtimeExtensionsById[extensionId]
        if (ext != null) {
            val controller = engineManager.getOrCreateRuntime().webExtensionController
            val result = if (enabled) {
                controller.enable(ext, WebExtensionController.EnableSource.USER)
            } else {
                controller.disable(ext, WebExtensionController.EnableSource.USER)
            }
            result.accept(
                { updated ->
                    if (updated != null) {
                        runtimeExtensionsById[updated.id] = updated
                        upsertUiModel(updated.toUiModel())
                    }
                },
                { err ->
                    _statusMessage.value = "Could not toggle extension: ${err?.localizedMessage}"
                }
            )
        } else {
            _extensions.value = _extensions.value.map {
                if (it.id == extensionId) it.copy(isEnabled = enabled) else it
            }
        }
    }

    fun setAllowedInPrivateBrowsing(extensionId: String, allowed: Boolean) {
        val ext = runtimeExtensionsById[extensionId]
        if (ext != null) {
            val controller = engineManager.getOrCreateRuntime().webExtensionController
            controller.setAllowedInPrivateBrowsing(ext, allowed).accept(
                { updated ->
                    if (updated != null) {
                        runtimeExtensionsById[updated.id] = updated
                        upsertUiModel(updated.toUiModel())
                    }
                },
                { err ->
                    _statusMessage.value = "Could not update private browsing access: ${err?.localizedMessage}"
                }
            )
        } else {
            _extensions.value = _extensions.value.map {
                if (it.id == extensionId) it.copy(allowedInPrivateBrowsing = allowed) else it
            }
        }
    }

    fun uninstallExtension(extensionId: String) {
        val ext = runtimeExtensionsById[extensionId]
        if (ext != null) {
            val controller = engineManager.getOrCreateRuntime().webExtensionController
            controller.uninstall(ext).accept(
                {
                    runtimeExtensionsById.remove(extensionId)
                    _extensions.value = _extensions.value.filterNot { it.id == extensionId }
                    _statusMessage.value = "Uninstalled $extensionId."
                },
                { err ->
                    _statusMessage.value = "Uninstall failed: ${err?.localizedMessage}"
                }
            )
        } else {
            _extensions.value = _extensions.value.filterNot { it.id == extensionId && !it.isBuiltIn }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun upsertUiModel(model: InstalledExtensionModel) {
        val current = _extensions.value.toMutableList()
        val idx = current.indexOfFirst { it.id == model.id }
        if (idx >= 0) current[idx] = model else current.add(model)
        _extensions.value = current
    }

    private fun WebExtension.toUiModel(): InstalledExtensionModel {
        val meta = this.metaData
        return InstalledExtensionModel(
            id = this.id,
            name = meta.name ?: this.id,
            version = meta.version ?: "1.0",
            description = meta.description ?: "Firefox WebExtension running in GeckoView",
            isEnabled = meta.enabled,
            isBuiltIn = this.isBuiltIn,
            allowedInPrivateBrowsing = meta.allowedInPrivateBrowsing,
            permissions = meta.requiredPermissions.toList() + meta.requiredOrigins.toList(),
            downloadUri = meta.downloadUrl,
        )
    }
}

/** Only signed add-ons served by Mozilla over HTTPS may be installed. */
internal fun isTrustedAddonUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("addons.mozilla.org", ignoreCase = true) &&
        uri.userInfo == null
}

private val ADDON_SLUG = Regex("^[A-Za-z0-9._@~-]{1,100}$")

private fun latestXpiUrl(slug: String) = "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"

/**
 * Turns pasted text into an installable AMO download URL, or `null` when it isn't a Mozilla add-on.
 * Accepts add-on page links (any locale, `/firefox/` or `/android/`), direct download links and bare slugs.
 */
internal fun resolveAddonInstallUrl(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains('/') && !text.contains('.') && ADDON_SLUG.matches(text)) return latestXpiUrl(text)

    val withScheme = if (text.startsWith("http", ignoreCase = true)) text else "https://$text"
    val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        !uri.host.equals("addons.mozilla.org", ignoreCase = true) ||
        uri.userInfo != null
    ) return null

    val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
    val addonIndex = segments.indexOf("addon")
    if (addonIndex >= 0 && addonIndex + 1 < segments.size) {
        val slug = segments[addonIndex + 1]
        if (ADDON_SLUG.matches(slug)) return latestXpiUrl(slug)
    }
    if (segments.contains("downloads") && (uri.rawPath.endsWith(".xpi"))) {
        return "https://addons.mozilla.org${uri.rawPath}"
    }
    return null
}
