package com.aura.browser.feature.settings.ui

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
import com.aura.browser.core.common.model.SearchEngine
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.datastore.AuraUserSettings
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataSource: AuraPreferencesDataSource,
) : ViewModel() {

    val settings: StateFlow<AuraUserSettings> = preferencesDataSource.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuraUserSettings())

    fun selectSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { preferencesDataSource.setSearchEngine(engine) }
    }

    fun setDesktopDefault(enabled: Boolean) {
        viewModelScope.launch { preferencesDataSource.setDesktopSiteDefault(enabled) }
    }

    fun setAutoPip(enabled: Boolean) {
        viewModelScope.launch { preferencesDataSource.setAutoEnterPip(enabled) }
    }

    fun setClearOnExit(enabled: Boolean) {
        viewModelScope.launch { preferencesDataSource.setClearSessionOnExit(enabled) }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Settings",
            subtitle = "Search • Browsing • Media",
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                GlassCard {
                    Text(
                        text = "Search engine",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchEngine.entries.forEach { engine ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = engine.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(text = engine.homeUrl, style = MaterialTheme.typography.bodyMedium)
                            }
                            GlassPillButton(
                                text = if (settings.searchEngine == engine) "Default" else "Choose",
                                isPrimary = settings.searchEngine == engine,
                                onClick = { viewModel.selectSearchEngine(engine) },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item {
                GlassCard {
                    Text(
                        text = "Browsing & media",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Request desktop sites by default", style = MaterialTheme.typography.titleMedium)
                            Text(text = "New tabs load the desktop version of websites.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = settings.desktopSiteDefault, onCheckedChange = viewModel::setDesktopDefault)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Automatic Picture-in-Picture", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Keeps a playing video in a small window when you leave the app.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = settings.autoEnterPipOnBackground, onCheckedChange = viewModel::setAutoPip)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Clear tabs and browsing data on exit", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Closes all tabs and removes history, cookies and cache when you close Aura. Private tabs are never saved.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = settings.clearSessionOnExit, onCheckedChange = viewModel::setClearOnExit)
                    }
                }
            }
        }
    }
}
