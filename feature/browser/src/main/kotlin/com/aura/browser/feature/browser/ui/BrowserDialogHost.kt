package com.aura.browser.feature.browser.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.browser.feature.browser.engine.AuraGeckoEngineManager
import com.aura.browser.feature.browser.engine.BrowserPrompt
import com.aura.browser.feature.browser.engine.ContextMenuTarget

/**
 * Renders the head of the engine's prompt queue (page dialogs, permission requests) and the
 * long-press context menu. Place once inside the browser screen.
 */
@Composable
fun BrowserDialogHost(engine: AuraGeckoEngineManager) {
    val prompts by engine.prompts().collectAsStateWithLifecycle()
    val contextMenu by engine.contextMenu.collectAsStateWithLifecycle()
    val queue = engine.promptQueue

    prompts.firstOrNull()?.let { prompt ->
        val finish: () -> Unit = { queue.remove(prompt.id) }
        when (prompt) {
            is BrowserPrompt.Alert -> AlertDialog(
                onDismissRequest = { prompt.onDismiss(); finish() },
                title = prompt.title.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = { Text(prompt.message) },
                confirmButton = { TextButton(onClick = { prompt.onDismiss(); finish() }) { Text("OK") } },
            )

            is BrowserPrompt.Confirm -> AlertDialog(
                onDismissRequest = { prompt.onResult(false); finish() },
                title = prompt.title.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = { Text(prompt.message) },
                confirmButton = { TextButton(onClick = { prompt.onResult(true); finish() }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { prompt.onResult(false); finish() }) { Text("Cancel") } },
            )

            is BrowserPrompt.TextInput -> {
                var value by remember(prompt.id) { mutableStateOf(prompt.defaultValue) }
                AlertDialog(
                    onDismissRequest = { prompt.onResult(null); finish() },
                    title = prompt.title.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                    text = {
                        Column {
                            if (prompt.message.isNotBlank()) {
                                Text(prompt.message)
                                Spacer(Modifier.padding(4.dp))
                            }
                            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true)
                        }
                    },
                    confirmButton = { TextButton(onClick = { prompt.onResult(value); finish() }) { Text("OK") } },
                    dismissButton = { TextButton(onClick = { prompt.onResult(null); finish() }) { Text("Cancel") } },
                )
            }

            is BrowserPrompt.Choice -> ChoiceDialog(prompt, onFinished = finish)

            is BrowserPrompt.Auth -> {
                var user by remember(prompt.id) { mutableStateOf("") }
                var pass by remember(prompt.id) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { prompt.onResult(null, null); finish() },
                    title = { Text(prompt.title) },
                    text = {
                        Column {
                            if (prompt.message.isNotBlank()) {
                                Text(prompt.message)
                                Spacer(Modifier.padding(4.dp))
                            }
                            OutlinedTextField(value = user, onValueChange = { user = it }, singleLine = true, label = { Text("Username") })
                            OutlinedTextField(
                                value = pass,
                                onValueChange = { pass = it },
                                singleLine = true,
                                label = { Text("Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            )
                        }
                    },
                    confirmButton = { TextButton(onClick = { prompt.onResult(user, pass); finish() }) { Text("Sign in") } },
                    dismissButton = { TextButton(onClick = { prompt.onResult(null, null); finish() }) { Text("Cancel") } },
                )
            }

            is BrowserPrompt.SitePermission -> {
                var rememberChoice by remember(prompt.id) { mutableStateOf(true) }
                val host = prompt.origin.substringAfter("://")
                AlertDialog(
                    onDismissRequest = { prompt.onResult(false, false); finish() },
                    title = { Text("Allow $host to use ${prompt.kinds.joinToString(" and ") { it.label }}?") },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberChoice = !rememberChoice },
                        ) {
                            Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                            Text("Remember my choice for this site")
                        }
                    },
                    confirmButton = { TextButton(onClick = { prompt.onResult(true, rememberChoice); finish() }) { Text("Allow") } },
                    dismissButton = { TextButton(onClick = { prompt.onResult(false, false); finish() }) { Text("Block") } },
                )
            }

            is BrowserPrompt.FilePick -> {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    prompt.onResult(uris.takeIf { it.isNotEmpty() })
                    finish()
                }
                LaunchedEffect(prompt.id) {
                    val types = prompt.mimeTypes.filter { it.contains('/') }.ifEmpty { listOf("*/*") }
                    picker.launch(types.toTypedArray())
                }
            }

            is BrowserPrompt.AndroidPermissions -> {
                val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    prompt.onResult(result.isNotEmpty() && result.values.all { it })
                    finish()
                }
                LaunchedEffect(prompt.id) { request.launch(prompt.permissions.toTypedArray()) }
            }
        }
    }

    contextMenu?.let { target ->
        ContextMenuDialog(
            target = target,
            onDismiss = engine::dismissContextMenu,
            onOpenInNewTab = { url, private -> engine.openNewTab(url, private) },
            onDownloadImage = engine::downloadFromUrl,
        )
    }
}

private fun AuraGeckoEngineManager.prompts() = promptQueue.prompts

@Composable
private fun ChoiceDialog(prompt: BrowserPrompt.Choice, onFinished: () -> Unit) {
    val selected: SnapshotStateList<String> = remember(prompt.id) {
        mutableStateListOf<String>().apply { addAll(prompt.options.filter { it.selected }.map { it.id }) }
    }
    AlertDialog(
        onDismissRequest = { prompt.onResult(null); onFinished() },
        title = prompt.title.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                prompt.options.forEach { option ->
                    when {
                        option.isSeparator -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        option.isHeader -> Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        else -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = option.enabled) {
                                    if (prompt.multiple) {
                                        if (option.id in selected) selected.remove(option.id) else selected.add(option.id)
                                    } else {
                                        prompt.onResult(listOf(option.id))
                                        onFinished()
                                    }
                                },
                        ) {
                            if (prompt.multiple) {
                                Checkbox(checked = option.id in selected, onCheckedChange = null, enabled = option.enabled)
                            } else {
                                RadioButton(selected = option.selected, onClick = null, enabled = option.enabled)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(option.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (prompt.multiple) {
                TextButton(onClick = { prompt.onResult(selected.toList()); onFinished() }) { Text("OK") }
            }
        },
        dismissButton = { TextButton(onClick = { prompt.onResult(null); onFinished() }) { Text("Cancel") } },
    )
}

@Composable
private fun ContextMenuDialog(
    target: ContextMenuTarget,
    onDismiss: () -> Unit,
    onOpenInNewTab: (url: String, isPrivate: Boolean) -> Unit,
    onDownloadImage: (String) -> Unit,
) {
    val context = LocalContext.current
    val entries = buildList<Pair<String, () -> Unit>> {
        target.linkUrl?.let { link ->
            add("Open in new tab" to { onOpenInNewTab(link, false) })
            add("Open in private tab" to { onOpenInNewTab(link, true) })
            add("Copy link" to { copyToClipboard(context, link) })
            add("Share link" to { shareText(context, link) })
        }
        target.imageUrl?.let { image ->
            add("Open image in new tab" to { onOpenInNewTab(image, false) })
            add("Download image" to { onDownloadImage(image) })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = (target.linkUrl ?: target.imageUrl).orEmpty(), maxLines = 2, style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                entries.forEach { (label, action) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { action(); onDismiss() }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

internal fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Aura link", text))
}

internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
