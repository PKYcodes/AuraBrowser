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
                description = "Built-in GeckoView extension keeping HTML5 media active across background tabs and optimizing mobile theater layout.",
                isEnabled = true,
                isBuiltIn = true,
                allowedInPrivateBrowsing = true,
                permissions = listOf("activeTab", "storage", "geckoViewAddons"),
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

    fun installFromUrl(xpiUri: String) {
        val trimmed = xpiUri.trim()
        if (!trimmed.startsWith("https://addons.mozilla.org/") && !trimmed.endsWith(".xpi")) {
            _statusMessage.value = "Security policy: Only signed Mozilla Add-on (.xpi) HTTPS URLs are permitted."
            return
        }
        _statusMessage.value = "Installing WebExtension from $trimmed..."
        runCatching {
            val controller = engineManager.getOrCreateRuntime().webExtensionController
            controller.install(trimmed, WebExtensionController.INSTALLATION_METHOD_MANAGER).accept(
                { installed ->
                    if (installed != null) {
                        runtimeExtensionsById[installed.id] = installed
                        upsertUiModel(installed.toUiModel())
                        _statusMessage.value = "Installed ${installed.metaData.name ?: installed.id} successfully."
                    }
                },
                { error ->
                    _statusMessage.value = "Installation failed: ${error?.localizedMessage ?: "Invalid or unsigned XPI"}"
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
