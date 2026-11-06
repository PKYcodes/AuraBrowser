package com.aura.browser.feature.extensions.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.feature.extensions.manager.AuraWebExtensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    val extensionManager: AuraWebExtensionManager,
) : ViewModel()

@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val installed by viewModel.extensionManager.extensions.collectAsStateWithLifecycle()
    val statusMessage by viewModel.extensionManager.statusMessage.collectAsStateWithLifecycle()
    var customXpiUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.extensionManager.refreshInstalledExtensions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Firefox WebExtensions",
            subtitle = "Mozilla GeckoView WebExtensionController",
            actions = {
                IconButton(onClick = { viewModel.extensionManager.refreshInstalledExtensions() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Extensions",
                        tint = AuraColors.TextPrimary,
                    )
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            statusMessage?.let { msg ->
                item {
                    GlassCard(borderColor = AuraColors.PrimaryAccent) {
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium.copy(color = AuraColors.TextPrimary))
                    }
                }
            }

            item {
                Text(
                    text = "Installed Extensions (${installed.size})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            items(installed, key = { it.id }) { ext ->
                GlassCard(isHighlighted = ext.isEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = if (ext.isEnabled) AuraColors.PrimaryAccent else AuraColors.TextMuted,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "${ext.name} (v${ext.version})", style = MaterialTheme.typography.titleMedium)
                            Text(text = ext.id, style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(
                            checked = ext.isEnabled,
                            onCheckedChange = { viewModel.extensionManager.setExtensionEnabled(ext.id, it) },
                        )
                        if (!ext.isBuiltIn) {
                            IconButton(onClick = { viewModel.extensionManager.uninstallExtension(ext.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Uninstall Extension",
                                    tint = AuraColors.DangerRed,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = ext.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permissions: ${ext.permissions.joinToString(", ").ifEmpty { "None required" }}",
                        style = MaterialTheme.typography.labelMedium.copy(color = AuraColors.SecondaryCyan),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Allow in Private Browsing",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = ext.allowedInPrivateBrowsing,
                            onCheckedChange = {
                                viewModel.extensionManager.setAllowedInPrivateBrowsing(ext.id, it)
                            },
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Verified Android-Compatible Add-ons (AMO)",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            items(viewModel.extensionManager.curatedAndroidCatalog, key = { it.id }) { catalogItem ->
                GlassCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = AuraColors.SecureGreen,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = catalogItem.name, style = MaterialTheme.typography.titleMedium)
                            Text(text = catalogItem.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassPillButton(
                            text = "Install",
                            isPrimary = true,
                            onClick = { viewModel.extensionManager.installFromUrl(catalogItem.amoXpiUrl) },
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Requested Permissions: ${catalogItem.recommendedPermissions.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Install Signed .XPI from Mozilla Add-ons URL",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AuraColors.ElevatedBackground)
                            .border(1.dp, AuraColors.GlassBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (customXpiUrl.isEmpty()) {
                            Text(
                                text = "https://addons.mozilla.org/firefox/downloads/...",
                                style = MaterialTheme.typography.bodyMedium.copy(color = AuraColors.TextMuted),
                            )
                        }
                        BasicTextField(
                            value = customXpiUrl,
                            onValueChange = { customXpiUrl = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuraColors.TextPrimary),
                            cursorBrush = SolidColor(AuraColors.PrimaryAccent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassPillButton(
                        text = "Verify & Install XPI",
                        icon = Icons.Default.AddLink,
                        isPrimary = true,
                        onClick = { viewModel.extensionManager.installFromUrl(customXpiUrl) },
                    )
                }
            }
        }
    }
}
