package com.aura.browser.feature.privacy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.common.model.CookiePolicyMode
import com.aura.browser.core.common.model.TrackingProtectionPreset
import com.aura.browser.core.database.dao.SitePermissionDao
import com.aura.browser.core.database.entity.SitePermissionEntity
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.datastore.AuraUserSettings
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassStatBadge
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val preferencesDataSource: AuraPreferencesDataSource,
    private val sitePermissionDao: SitePermissionDao,
    private val engineManager: AuraGeckoEngineManager,
) : ViewModel() {

    val settings: StateFlow<AuraUserSettings> = preferencesDataSource.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuraUserSettings())

    val sitePermissions: StateFlow<List<SitePermissionEntity>> = sitePermissionDao.observeSitePermissions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTrackingPreset(preset: TrackingProtectionPreset) {
        viewModelScope.launch {
            preferencesDataSource.setTrackingProtectionPreset(preset)
            engineManager.applyPrivacySettings(preset, settings.value.httpsOnlyMode)
        }
    }

    fun selectCookiePolicy(mode: CookiePolicyMode) {
        viewModelScope.launch {
            preferencesDataSource.setCookiePolicyMode(mode)
        }
    }

    fun setHttpsOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setHttpsOnlyMode(enabled)
            engineManager.applyPrivacySettings(settings.value.trackingProtectionPreset, enabled)
        }
    }

    fun clearAllCookiesAndSiteData() {
        viewModelScope.launch {
            engineManager.clearSiteDataAndCookies()
            sitePermissionDao.clearAllSitePermissions()
        }
    }
}

@Composable
fun PrivacyDashboardScreen(
    viewModel: PrivacyDashboardViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sitePermissions by viewModel.sitePermissions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Privacy & Security Shield",
            subtitle = "Zero Analytics • HTTPS-First • GeckoView ContentBlocking",
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassStatBadge(
                        label = "Trackers Blocked",
                        value = settings.blockTrackersCount.toString(),
                        accentColor = AuraColors.SecureGreen,
                        modifier = Modifier.weight(1f),
                    )
                    GlassStatBadge(
                        label = "HTTPS Upgrades",
                        value = settings.httpsUpgradesCount.toString(),
                        accentColor = AuraColors.PrimaryAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                GlassCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AuraColors.SecureGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "HTTPS-Only Mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Automatically upgrades cleartext HTTP navigation and strips UTM/fbclid/gclid tracking parameters.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.httpsOnlyMode,
                            onCheckedChange = viewModel::setHttpsOnly,
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Enhanced Tracking Protection Preset",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TrackingProtectionPreset.entries.forEach { preset ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = preset.title, style = MaterialTheme.typography.titleMedium)
                                Text(text = preset.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            GlassPillButton(
                                text = if (settings.trackingProtectionPreset == preset) "Active" else "Select",
                                isPrimary = settings.trackingProtectionPreset == preset,
                                onClick = { viewModel.selectTrackingPreset(preset) },
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Cookie Isolation Policy",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CookiePolicyMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            GlassPillButton(
                                text = if (settings.cookiePolicyMode == mode) "Selected" else "Use",
                                isPrimary = settings.cookiePolicyMode == mode,
                                onClick = { viewModel.selectCookiePolicy(mode) },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item {
                GlassCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AuraColors.DangerRed,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Purge Cookies, Cache & Site Permissions", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Clears GeckoRuntime StorageController caches, cookies, and ${sitePermissions.size} stored origin rules.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        GlassPillButton(
                            text = "Purge Now",
                            icon = Icons.Default.DeleteForever,
                            isPrimary = true,
                            accentColor = AuraColors.DangerRed,
                            onClick = { viewModel.clearAllCookiesAndSiteData() },
                        )
                    }
                }
            }
        }
    }
}
