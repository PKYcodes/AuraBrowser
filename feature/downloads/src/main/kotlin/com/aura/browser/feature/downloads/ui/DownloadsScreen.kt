package com.aura.browser.feature.downloads.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.aura.browser.core.network.download.AuraDownloadManager
import com.aura.browser.core.network.download.AuraDownloadValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadManager: AuraDownloadManager,
    private val validator: AuraDownloadValidator,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(download: DownloadEntity) = downloadManager.cancel(download.id)

    fun delete(download: DownloadEntity) {
        viewModelScope.launch { downloadManager.delete(download) }
    }

    /** Removes finished entries from the list; the downloaded files are kept. */
    fun clearFinished() {
        viewModelScope.launch { downloadDao.clearFinishedDownloads() }
    }

    fun isRisky(download: DownloadEntity): Boolean =
        validator.isHighRiskFileName(download.fileName, download.mimeType)

    fun viewIntent(download: DownloadEntity) = downloadManager.buildViewIntent(download)
}

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var pendingRisky by remember { mutableStateOf<DownloadEntity?>(null) }

    fun open(download: DownloadEntity) {
        val intent = viewModel.viewIntent(download)
        if (intent == null) {
            Toast.makeText(context, "File is no longer available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }

    pendingRisky?.let { download ->
        AlertDialog(
            onDismissRequest = { pendingRisky = null },
            title = { Text("Open potentially unsafe file?") },
            text = {
                Text(
                    "\u201c${download.fileName}\u201d is an executable or package file. " +
                        "Only open it if you trust the source:\n${download.sourceUrl}"
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingRisky = null; open(download) }) { Text("Open anyway") }
            },
            dismissButton = { TextButton(onClick = { pendingRisky = null }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Downloads",
            subtitle = "${downloads.size} file(s)",
            actions = {
                if (downloads.any { it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.QUEUED }) {
                    GlassPillButton(
                        text = "Clear list",
                        icon = Icons.Default.DeleteSweep,
                        onClick = viewModel::clearFinished,
                    )
                }
            },
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
                            text = "No downloads yet. Files you download or save as PDF appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(downloads, key = { it.id }) { dl ->
                    DownloadRow(
                        context = context,
                        download = dl,
                        isRisky = viewModel.isRisky(dl),
                        onOpen = { if (viewModel.isRisky(dl)) pendingRisky = dl else open(dl) },
                        onCancel = { viewModel.cancel(dl) },
                        onDelete = { viewModel.delete(dl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    context: Context,
    download: DownloadEntity,
    isRisky: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val inProgress = download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED
    val completed = download.status == DownloadStatus.COMPLETED

    GlassCard(
        onClick = if (completed) onOpen else null,
        borderColor = if (isRisky && completed) AuraColors.WarningAmber else AuraColors.GlassBorder,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = when (download.status) {
                    DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when (download.status) {
                    DownloadStatus.COMPLETED -> AuraColors.SecureGreen
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> AuraColors.DangerRed
                    else -> AuraColors.PrimaryAccent
                },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildStatusLine(context, download),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = download.sourceUrl,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (completed && isRisky) {
                    Text(
                        text = "Executable file \u2013 verify the source before opening",
                        style = MaterialTheme.typography.labelMedium.copy(color = AuraColors.WarningAmber),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                download.sha256Checksum?.let { sha ->
                    Text(
                        text = "SHA-256 ${sha.take(16)}\u2026",
                        style = MaterialTheme.typography.labelMedium.copy(color = AuraColors.TextMuted),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (inProgress) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel download", tint = AuraColors.TextSecondary)
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete download", tint = AuraColors.DangerRed)
                }
            }
        }
        if (inProgress) {
            Spacer(modifier = Modifier.height(10.dp))
            if (download.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = AuraColors.PrimaryAccent,
                    trackColor = AuraColors.ElevatedBackground,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AuraColors.PrimaryAccent,
                    trackColor = AuraColors.ElevatedBackground,
                )
            }
        }
    }
}

private fun buildStatusLine(context: Context, download: DownloadEntity): String {
    val done = Formatter.formatShortFileSize(context, download.downloadedBytes)
    return when (download.status) {
        DownloadStatus.COMPLETED -> "${Formatter.formatShortFileSize(context, download.totalBytes.coerceAtLeast(download.downloadedBytes))} \u2022 Completed"
        DownloadStatus.DOWNLOADING ->
            if (download.totalBytes > 0) "$done of ${Formatter.formatShortFileSize(context, download.totalBytes)}"
            else "$done downloaded"
        DownloadStatus.QUEUED -> "Waiting\u2026"
        DownloadStatus.FAILED -> "Failed"
        DownloadStatus.CANCELLED -> "Cancelled"
        DownloadStatus.PAUSED -> "Paused"
    }
}
