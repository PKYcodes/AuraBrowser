package com.aura.browser.feature.filemanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aura.browser.core.datastore.AuraPreferencesDataSource
import com.aura.browser.core.designsystem.component.GlassCard
import com.aura.browser.core.designsystem.component.GlassPillButton
import com.aura.browser.core.designsystem.component.GlassTopBar
import com.aura.browser.core.designsystem.theme.AuraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AuraFileEntry(
    val name: String,
    val pathOrUri: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val mimeType: String,
)

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: AuraPreferencesDataSource,
) : ViewModel() {

    private val _files = MutableStateFlow<List<AuraFileEntry>>(emptyList())
    val files: StateFlow<List<AuraFileEntry>> = _files.asStateFlow()

    private val _currentRootLabel = MutableStateFlow("App Scoped Downloads & Media")
    val currentRootLabel: StateFlow<String> = _currentRootLabel.asStateFlow()

    fun loadScopedAppDirectories() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) {
                val roots = listOfNotNull(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    context.filesDir,
                )
                roots.flatMap { dir ->
                    dir.listFiles()?.map { file ->
                        AuraFileEntry(
                            name = file.name,
                            pathOrUri = file.absolutePath,
                            isDirectory = file.isDirectory,
                            sizeBytes = file.length(),
                            mimeType = if (file.isDirectory) "inode/directory" else "application/octet-stream",
                        )
                    } ?: emptyList()
                }
            }
            _currentRootLabel.value = "App Scoped Storage (${entries.size} items)"
            _files.value = entries
        }
    }

    fun inspectSafTree(treeUri: Uri) {
        viewModelScope.launch {
            preferencesDataSource.setSafDownloadsTreeUri(treeUri.toString())
            val entries = withContext(Dispatchers.IO) {
                val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
                docTree.listFiles().map { doc ->
                    AuraFileEntry(
                        name = doc.name ?: "Unnamed",
                        pathOrUri = doc.uri.toString(),
                        isDirectory = doc.isDirectory,
                        sizeBytes = doc.length(),
                        mimeType = doc.type ?: "application/octet-stream",
                    )
                }
            }
            _currentRootLabel.value = "SAF Directory (${entries.size} items)"
            _files.value = entries
        }
    }
}

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsStateWithLifecycle()
    val rootLabel by viewModel.currentRootLabel.collectAsStateWithLifecycle()

    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.inspectSafTree(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadScopedAppDirectories()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.DeepBackground),
    ) {
        GlassTopBar(
            title = "Aura File Manager",
            subtitle = rootLabel,
            actions = {
                GlassPillButton(
                    text = "Browse SAF Folder",
                    icon = Icons.Default.FolderOpen,
                    isPrimary = true,
                    onClick = { safTreeLauncher.launch(null) },
                )
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (files.isEmpty()) {
                item {
                    GlassCard {
                        Text(
                            text = "Directory is empty or no SAF folder selected yet. Tap 'Browse SAF Folder' to grant scoped directory access without broad storage permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(files, key = { it.pathOrUri }) { file ->
                    GlassCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val icon = when {
                                file.isDirectory -> Icons.Default.Folder
                                file.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
                                file.mimeType.startsWith("video/") -> Icons.Default.VideoFile
                                else -> Icons.Default.Description
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = AuraColors.PrimaryAccent,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${file.mimeType} • ${file.sizeBytes / 1024} KB",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
