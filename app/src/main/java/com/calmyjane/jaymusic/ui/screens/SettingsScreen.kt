package com.calmyjane.jaymusic.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmyjane.jaymusic.BuildConfig
import com.calmyjane.jaymusic.data.ThemeMode
import com.calmyjane.jaymusic.remote.ServerConnection
import com.calmyjane.jaymusic.sync.SyncStatus
import com.calmyjane.jaymusic.ui.MainViewModel
import com.calmyjane.jaymusic.ui.components.ConfirmDialog
import com.calmyjane.jaymusic.ui.components.formatBytes
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues, onOpenExclusions: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val usedBytes by vm.usedBytes.collectAsStateWithLifecycle()
    val rules by vm.folderRules.collectAsStateWithLifecycle()
    val trackCount = vm.allTracks.collectAsStateWithLifecycle().value.size

    var localUrl by rememberSaveable(settings.localUrl) { mutableStateOf(settings.localUrl) }
    var remoteUrl by rememberSaveable(settings.remoteUrl) { mutableStateOf(settings.remoteUrl) }
    var username by rememberSaveable(settings.username) { mutableStateOf(settings.username) }
    var folders by rememberSaveable(settings.musicFolders) { mutableStateOf(settings.musicFolders.joinToString(", ")) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Synology server")
        ConnectionCard(connection, onRetry = vm::reconnect)
        OutlinedTextField(
            value = localUrl, onValueChange = { localUrl = it },
            label = { Text("Home address (optional)") },
            placeholder = { Text("http://192.168.1.10:5000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = remoteUrl, onValueChange = { remoteUrl = it },
            label = { Text("Internet address") },
            placeholder = { Text("https://nas.example.com:5001") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("User name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text(if (vm.hasPassword()) "Password (saved – type to change)" else "Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = folders, onValueChange = { folders = it },
            label = { Text("Music folders on the NAS") },
            supportingText = { Text("Comma separated, e.g. /music, /homes/jay/Music") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                vm.saveServer(localUrl, remoteUrl, username, password, folders.split(',').map { it.trim() })
                password = ""
            },
            enabled = (localUrl.isNotBlank() || remoteUrl.isNotBlank()) && username.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Save and connect") }

        Section("Library")
        Text("$trackCount songs in library", style = MaterialTheme.typography.bodyLarge)
        SyncStatusText(syncStatus)
        OutlinedButton(onClick = vm::syncNow, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Filled.Sync, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sync now")
        }
        SwitchRow(
            title = "Background sync on Wi‑Fi only",
            subtitle = "Automatic downloads every 12 h. “Sync now” and songs you play work on any network.",
            checked = settings.syncOnWifiOnly,
            onChange = vm::setSyncOnWifiOnly,
        )
        NavRow(
            title = "Folders never downloaded",
            subtitle = if (rules.excludedFolders.isEmpty()) "None – all folders may be downloaded" else rules.excludedFolders.joinToString(", "),
            onClick = onOpenExclusions,
        )

        Section("Storage on this phone")
        StorageLimit(settings.storageLimitMb, usedBytes, vm.freeBytesOnDevice(), vm::setStorageLimitMb)
        PrefetchSetting(settings.prefetchCount, vm::setPrefetchCount)
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete all downloaded songs")
        }

        Section("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System")
            options.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = settings.theme == mode,
                    onClick = { vm.setTheme(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "JayMusic ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (connection is ServerConnection.State.NeedsOtp) {
        OtpDialog(onSubmit = vm::submitOtp, onDismiss = {})
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Delete downloads?",
            text = "All songs stored on this phone are deleted. They can be streamed and downloaded again later.",
            confirm = "Delete",
            onDismiss = { confirmClear = false },
            onConfirm = vm::clearDownloads,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground, thickness = 2.dp)
}

@Composable
private fun ConnectionCard(state: ServerConnection.State, onRetry: () -> Unit) {
    val (title, detail) = when (state) {
        ServerConnection.State.NotConfigured -> "Not set up" to "Enter your NAS address and account below."
        ServerConnection.State.Connecting -> "Connecting…" to "Downloaded songs can be played meanwhile."
        is ServerConnection.State.Online -> "Connected" to state.address
        is ServerConnection.State.Offline -> "Offline" to "${state.reason}. Playing downloaded songs; retrying automatically."
        ServerConnection.State.NeedsOtp -> "2-factor code needed" to "Enter the code from your authenticator app."
        is ServerConnection.State.LoginFailed -> "Login failed" to state.reason
    }
    Row(
        Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state !is ServerConnection.State.Online && state !is ServerConnection.State.NotConfigured) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun SyncStatusText(status: SyncStatus) {
    val text = when (status) {
        SyncStatus.Idle -> return
        is SyncStatus.Scanning -> "Scanning NAS… ${status.folders} folders, ${status.tracks} songs"
        is SyncStatus.Downloading -> "Downloading best rated songs… ${status.done} done"
        is SyncStatus.Finished -> status.message
        is SyncStatus.Failed -> "Sync failed: ${status.message}"
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Storage limit slider in 1 GB steps (0.5 GB minimum), capped at what the phone can hold. */
@Composable
private fun StorageLimit(limitMb: Long, usedBytes: Long, freeBytes: Long, onChange: (Long) -> Unit) {
    val maxGb = ((usedBytes + freeBytes) / (1L shl 30)).coerceIn(1, 1024).toFloat()
    var value by remember(limitMb) { mutableFloatStateOf((limitMb / 1024f).coerceIn(0.5f, maxGb)) }
    Text("Maximum space for music: ${"%.1f".format(value)} GB", style = MaterialTheme.typography.titleMedium)
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onChange((value * 1024).roundToLong().coerceAtLeast(512)) },
        valueRange = 0.5f..maxGb,
    )
    Text(
        "Used: ${formatBytes(usedBytes)} · Free on phone: ${formatBytes(freeBytes)}\n" +
            "When full, the worst rated songs are deleted first (unrated count as 2.5 stars).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PrefetchSetting(count: Int, onChange: (Int) -> Unit) {
    Text("Download ahead: $count songs", style = MaterialTheme.typography.titleMedium)
    var value by remember(count) { mutableFloatStateOf(count.toFloat()) }
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onChange(value.toInt()) },
        valueRange = 0f..10f,
        steps = 9,
    )
    Text(
        "The next songs in the queue are downloaded while online, so skipping is instant.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OtpDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("2-factor code") },
        text = {
            Column {
                Text("Your account uses 2-factor authentication. Enter the current code once; this phone is then remembered.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(8) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(code) }, enabled = code.length >= 6) { Text("Log in") } },
    )
}

/** Choose folders whose songs are never downloaded (they can still be streamed). */
@Composable
fun ExclusionsScreen(vm: MainViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val tree by vm.folderTree.collectAsStateWithLifecycle()
    val rules by vm.folderRules.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(tree) {
        // Show the top levels expanded initially.
        if (expanded.isEmpty()) expanded = tree.filter { it.depth == 0 }.map { it.path }.toSet()
    }

    val visible = remember(tree, expanded) {
        val known = tree.mapTo(HashSet()) { it.path }
        // A folder is visible when all its known ancestors are expanded.
        tree.filter { node ->
            var parent = node.path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                if (parent in known && parent !in expanded) return@filter false
                parent = parent.substringBeforeLast('/', "")
            }
            true
        }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Folders never downloaded", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Checked folders are not stored on the phone (existing downloads are removed). " +
                "Their songs can still be streamed when online.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (tree.isEmpty()) EmptyHint("Sync your library first to see its folders.")
        LazyColumn {
            items(visible, key = { it.path }) { node ->
                val excludedByParent = rules.excludedBy(node.path)?.let { it != node.path } == true
                val isExcluded = rules.isExcluded(node.path)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable(enabled = node.hasChildren) {
                            expanded = if (node.path in expanded) expanded - node.path else expanded + node.path
                        }
                        .padding(start = (8 + node.depth * 20).dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isExcluded,
                        enabled = !excludedByParent,
                        onCheckedChange = { vm.setFolderExcluded(node.path, it) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(node.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${node.songCount} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (node.hasChildren) {
                        Text(if (node.path in expanded) "−" else "+", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
