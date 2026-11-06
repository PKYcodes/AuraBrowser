package com.aura.browser.feature.downloads.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.database.dao.DownloadDao
import com.aura.browser.core.database.entity.DownloadEntity
import com.aura.browser.core.database.entity.DownloadStatus
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import com.aura.browser.core.network.download.AuraDownloadValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val validator: AuraDownloadValidator,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun enqueueSystemDownload(url: String, rawFileName: String? = null) {
        viewModelScope.launch {
            val validated = validator.validateDownload(rawFileName, url, null)
            val systemDownloadId = runCatching {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(validated.sanitizedFileName)
                    .setMimeType(validated.mimeType)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        validated.sanitizedFileName,
                    )
                dm.enqueue(request)
            }.getOrNull()

            downloadDao.insertDownload(
                DownloadEntity(
                    systemDownloadId = systemDownloadId,
                    fileName = validated.sanitizedFileName,
                    sourceUrl = url,
                    mimeType = validated.mimeType,
                    destinationUri = "content://downloads/public_downloads/${validated.sanitizedFileName}",
                    status = DownloadStatus.COMPLETED,
                )
            )
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { downloadDao.deleteDownload(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { downloadDao.clearFinishedDownloads() }
    }
}

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Download Manager",
            subtitle = "${downloads.size} file(s) validated by AuraDownloadValidator",
            actions = {
                if (downloads.isNotEmpty()) {
                    GlassPillButton(
                        text = "Clear Done",
                        icon = Icons.Default.DeleteSweep,
                        onClick = { viewModel.clearCompleted() },
                    )
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (downloads.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "No downloads yet. Files downloaded from GeckoView or saved for offline reading appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(downloads, key = { it.id }) { dl ->
                    GlassCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (dl.status == DownloadStatus.COMPLETED)
                                    Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (dl.status == DownloadStatus.COMPLETED)
                                    AuraColors.SecureGreen else AuraColors.PrimaryAccent,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dl.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${dl.mimeType} • ${dl.status.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dl.sourceUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteDownload(dl.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove Download",
                                    tint = AuraColors.DangerRed,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
