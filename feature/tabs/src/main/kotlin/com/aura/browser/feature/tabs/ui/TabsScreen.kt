package com.aura.browser.feature.tabs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.feature.browser.engine.BrowserTab
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val engineManager: AuraGeckoEngineManager,
) : ViewModel() {

    val allTabs: StateFlow<List<BrowserTab>> = engineManager.tabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(tab: BrowserTab) {
        engineManager.switchToTab(tab.id)
    }

    fun createNewTab(isPrivate: Boolean) {
        engineManager.openNewTab(isPrivate = isPrivate)
    }

    fun closeTab(tabId: String) {
        engineManager.closeTab(tabId)
    }

    fun closeAllPrivateTabs() {
        engineManager.closeAllPrivateTabs()
    }
}

@Composable
fun TabsScreen(
    onTabSelected: () -> Unit,
    viewModel: TabsViewModel = hiltViewModel(),
) {
    val tabs by viewModel.allTabs.collectAsStateWithLifecycle()
    var showPrivateOnly by remember { mutableStateOf(false) }

    val filteredTabs = tabs.filter { it.isPrivate == showPrivateOnly }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = if (showPrivateOnly) "Private Tabs" else "Tabs",
            subtitle = "${filteredTabs.size} ${if (showPrivateOnly) "private" else "standard"} tab(s)",
            actions = {
                GlassPillButton(
                    text = if (showPrivateOnly) "Private" else "Standard",
                    icon = if (showPrivateOnly) Icons.Default.Shield else Icons.Default.Public,
                    isPrimary = showPrivateOnly,
                    accentColor = AuraColors.PrivatePurple,
                    onClick = { showPrivateOnly = !showPrivateOnly },
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassPillButton(
                text = if (showPrivateOnly) "New Private Tab" else "New Tab",
                icon = Icons.Default.Add,
                isPrimary = true,
                accentColor = if (showPrivateOnly) AuraColors.PrivatePurple else AuraColors.PrimaryAccent,
                onClick = {
                    viewModel.createNewTab(isPrivate = showPrivateOnly)
                    onTabSelected()
                },
            )
            if (showPrivateOnly && filteredTabs.isNotEmpty()) {
                GlassPillButton(
                    text = "Close all private tabs",
                    icon = Icons.Default.DeleteSweep,
                    onClick = { viewModel.closeAllPrivateTabs() },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(filteredTabs, key = { it.id }) { tab ->
                GlassCard(
                    isHighlighted = tab.isActive,
                    borderColor = if (tab.isPrivate) AuraColors.PrivatePurple else AuraColors.GlassBorder,
                    onClick = {
                        viewModel.selectTab(tab)
                        onTabSelected()
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (tab.isPrivate) Icons.Default.Shield else Icons.Default.Public,
                            contentDescription = null,
                            tint = if (tab.isPrivate) AuraColors.PrivatePurple else AuraColors.PrimaryAccent,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tab.title.ifBlank { "New Tab" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.closeTab(tab.id) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tab",
                                tint = AuraColors.TextSecondary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = tab.url,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (tab.isActive) "Current tab" else if (tab.isPrivate) "Private • not saved" else "Background",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (tab.isActive) AuraColors.SecureGreen else AuraColors.TextMuted,
                        ),
                    )
                }
            }
        }
    }
}
