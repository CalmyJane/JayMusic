package com.calmyjane.jaymusic.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmyjane.jaymusic.BuildConfig
import com.calmyjane.jaymusic.data.ThemeMode
import com.calmyjane.jaymusic.remote.ServerConnection
import com.calmyjane.jaymusic.sync.SyncStatus
import com.calmyjane.jaymusic.ui.MainViewModel
import com.calmyjane.jaymusic.ui.components.ConfirmDialog
import com.calmyjane.jaymusic.ui.components.formatBytes
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToLong

/*
 * Settings are a plain list of rows that all have the same fixed height, so nothing moves when a
 * status changes. Details live on their own pages (server, excluded folders).
 */

private val RowHeight = 72.dp

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpenServer: () -> Unit,
    onOpenExclusions: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val usedBytes by vm.usedBytes.collectAsStateWithLifecycle()
    val rules by vm.folderRules.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var canShowOnLockScreen by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canShowOnLockScreen = Settings.canDrawOverlays(context) }

    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())) {
        SectionHeader("Server")
        SettingRow(
            icon = if (connection is ServerConnection.State.Online) Icons.Filled.Cloud else Icons.Filled.CloudOff,
            title = "Synology",
            subtitle = connectionSummary(connection),
            onClick = onOpenServer,
            trailing = { Chevron() },
        )

        SectionHeader("Library")
        SettingRow(
            icon = Icons.Filled.Sync,
            title = "Sync now",
            subtitle = syncSummary(syncStatus, settings.lastScanAt),
            onClick = vm::syncNow,
        )
        SettingRow(
            icon = Icons.Filled.Wifi,
            title = "Sync on Wi‑Fi only",
            subtitle = if (settings.syncOnWifiOnly) "Automatic sync waits for Wi‑Fi" else "Automatic sync on any network",
            onClick = { vm.setSyncOnWifiOnly(!settings.syncOnWifiOnly) },
            trailing = { Switch(checked = settings.syncOnWifiOnly, onCheckedChange = vm::setSyncOnWifiOnly) },
        )
        SettingRow(
            icon = Icons.Filled.FolderOff,
            title = "Excluded folders",
            subtitle = when (val n = rules.excludedFolders.size) {
                0 -> "None"
                1 -> rules.excludedFolders.first()
                else -> "$n folders"
            },
            onClick = onOpenExclusions,
            trailing = { Chevron() },
        )

        SectionHeader("Storage")
        StorageLimit(settings.storageLimitMb, usedBytes, vm.freeBytesOnDevice(), vm::setStorageLimitMb)
        PrefetchSetting(settings.prefetchCount, vm::setPrefetchCount)
        SettingRow(
            icon = Icons.Filled.DeleteOutline,
            title = "Delete downloads",
            subtitle = formatBytes(usedBytes) + " on this phone",
            onClick = { confirmClear = true },
        )

        SectionHeader("Lock screen")
        val lockOn = settings.lockScreenPlayer && canShowOnLockScreen
        SettingRow(
            icon = Icons.Filled.Lock,
            title = "Big lock screen player",
            subtitle = if (canShowOnLockScreen) "Large buttons while the phone is locked" else "Tap to allow",
            onClick = {
                if (!canShowOnLockScreen) {
                    vm.setLockScreenPlayer(true)
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                } else {
                    vm.setLockScreenPlayer(!settings.lockScreenPlayer)
                }
            },
            trailing = {
                Switch(
                    checked = lockOn,
                    onCheckedChange = { on ->
                        if (on && !canShowOnLockScreen) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                            )
                        }
                        vm.setLockScreenPlayer(on)
                    },
                )
            },
        )

        SectionHeader("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            val options = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System")
            options.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = settings.theme == mode,
                    onClick = { vm.setTheme(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }

        Text(
            "JayMusic ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (connection is ServerConnection.State.NeedsOtp) OtpDialog(onSubmit = vm::submitOtp)
    if (confirmClear) {
        ConfirmDialog(
            title = "Delete downloads?",
            text = "All songs stored on this phone are deleted. They download again when played.",
            confirm = "Delete",
            onDismiss = { confirmClear = false },
            onConfirm = vm::clearDownloads,
        )
    }
}

/** Server address and account. */
@Composable
fun ServerScreen(vm: MainViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    var localUrl by rememberSaveable(settings.localUrl) { mutableStateOf(settings.localUrl) }
    var remoteUrl by rememberSaveable(settings.remoteUrl) { mutableStateOf(settings.remoteUrl) }
    var username by rememberSaveable(settings.username) { mutableStateOf(settings.username) }
    var folders by rememberSaveable(settings.musicFolders) { mutableStateOf(settings.musicFolders.joinToString(", ")) }
    var password by rememberSaveable { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }
    val hasPassword = remember(settings) { vm.hasPassword() }

    Column(Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
        PageHeader("Synology", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status: fixed two-line height; tap for the full message.
            Row(
                Modifier.fillMaxWidth().height(RowHeight).clickable { showDetails = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (connection is ServerConnection.State.Online) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                    contentDescription = null,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(connectionSummary(connection), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        connectionDetail(connection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = vm::reconnect, enabled = connection !is ServerConnection.State.NotConfigured) {
                    Text("Retry")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Field("Home address", localUrl, { localUrl = it }, "http://192.168.1.10:5000", KeyboardType.Uri)
            Field("Internet address or QuickConnect ID", remoteUrl, { remoteUrl = it }, "myname", KeyboardType.Uri)
            Field("User name", username, { username = it })
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { if (hasPassword) Text("••••••••  (saved)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Field("Music folders", folders, { folders = it }, "/music")
            Button(
                onClick = {
                    vm.saveServer(localUrl, remoteUrl, username, password, folders.split(',').map { it.trim() })
                    password = ""
                },
                enabled = (localUrl.isNotBlank() || remoteUrl.isNotBlank()) && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save and connect") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (connection is ServerConnection.State.NeedsOtp) OtpDialog(onSubmit = vm::submitOtp)
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(connectionSummary(connection)) },
            text = { Text(connectionDetail(connection)) },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("OK") } },
        )
    }
}

private fun connectionSummary(state: ServerConnection.State): String = when (state) {
    ServerConnection.State.NotConfigured -> "Not set up"
    ServerConnection.State.Connecting -> "Connecting…"
    is ServerConnection.State.Online -> "Connected"
    is ServerConnection.State.Offline -> "Offline"
    ServerConnection.State.NeedsOtp -> "2-factor code needed"
    is ServerConnection.State.LoginFailed -> "Login failed"
}

private fun connectionDetail(state: ServerConnection.State): String = when (state) {
    ServerConnection.State.NotConfigured -> "Enter address and account"
    ServerConnection.State.Connecting -> " "
    is ServerConnection.State.Online -> state.address
    is ServerConnection.State.Offline -> state.reason
    ServerConnection.State.NeedsOtp -> "Enter the code from your authenticator app"
    is ServerConnection.State.LoginFailed -> state.reason
}

private fun syncSummary(status: SyncStatus, lastScanAt: Long): String = when (status) {
    SyncStatus.Idle -> if (lastScanAt > 0) {
        "Last sync " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastScanAt))
    } else {
        "Not synced yet"
    }
    is SyncStatus.Scanning -> "Scanning… ${status.tracks} songs"
    is SyncStatus.Downloading -> "Downloading… ${status.done} songs"
    is SyncStatus.Finished -> status.message
    is SyncStatus.Failed -> "Failed: ${status.message}"
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)

/** A settings row with fixed height: icon, title, one-line subtitle and optional trailing control. */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A labelled slider row with the value on the right; fixed height. */
@Composable
private fun SliderRow(title: String, value: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun StorageLimit(limitMb: Long, usedBytes: Long, freeBytes: Long, onChange: (Long) -> Unit) {
    val maxGb = ((usedBytes + freeBytes) / (1L shl 30)).coerceIn(1, 1024).toFloat()
    var value by remember(limitMb) { mutableFloatStateOf((limitMb / 1024f).coerceIn(0.5f, maxGb)) }
    SliderRow("Space for music", "%.1f GB".format(value)) {
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange((value * 1024).roundToLong().coerceAtLeast(512)) },
            valueRange = 0.5f..maxGb,
        )
    }
}

@Composable
private fun PrefetchSetting(count: Int, onChange: (Int) -> Unit) {
    var value by remember(count) { mutableFloatStateOf(count.toFloat()) }
    SliderRow("Download ahead", "${value.toInt()} songs") {
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
    }
}

@Composable
private fun OtpDialog(onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("2-factor code") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(8) },
                singleLine = true,
                label = { Text("Code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
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
        PageHeader("Excluded folders", onBack)
        Text(
            "Checked folders are only streamed, never stored.",
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
