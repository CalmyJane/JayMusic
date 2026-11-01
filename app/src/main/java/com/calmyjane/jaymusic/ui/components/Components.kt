package com.calmyjane.jaymusic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calmyjane.jaymusic.data.PlaylistSummary
import com.calmyjane.jaymusic.data.TrackEntity

/** Five tappable stars. Tapping the current rating again clears it. */
@Composable
fun RatingBar(rating: Int, onRate: (Int) -> Unit, starSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        for (star in 1..5) {
            IconButton(onClick = { onRate(if (star == rating) 0 else star) }, modifier = Modifier.size(starSize + 8.dp)) {
                Icon(
                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Rate $star of 5",
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}

/** Compact read-only rating, e.g. "★★★" in lists. */
@Composable
fun SmallRating(rating: Int) {
    if (rating <= 0) return
    Text(
        text = "★".repeat(rating),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    isDownloaded: Boolean,
    isExcluded: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
    /** Toggles "never download"; null if inherited from a folder (then it is shown, not tappable). */
    onToggleDownload: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Icon(Icons.Filled.Equalizer, contentDescription = "Playing", modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(track.artist, track.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                SmallRating(track.rating)
            }
        }
        DownloadToggle(
            excluded = isExcluded,
            onPhone = isDownloaded,
            enabled = onToggleDownload != null,
            onToggle = { onToggleDownload?.invoke() },
        )
    }
}

/**
 * Cloud on the right of songs and folders: crossed out = never downloaded, with check = on the
 * phone, outline = may be downloaded. Tap to switch "never download".
 */
@Composable
fun DownloadToggle(excluded: Boolean, onPhone: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val (icon, description) = when {
        excluded -> Icons.Filled.CloudOff to "Never downloaded – tap to allow"
        onPhone -> Icons.Filled.CloudDone to "On this phone – tap to never download"
        else -> Icons.Outlined.Cloud to "Not on this phone – tap to never download"
    }
    IconButton(onClick = onToggle, enabled = enabled) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (excluded || onPhone) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

/** Long-press menu of a song: rate, play next, queue, add to playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: TrackEntity,
    playlists: List<PlaylistSummary>,
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    extraActions: @Composable () -> Unit = {},
) {
    var choosingPlaylist by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(track.artist, track.album).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RatingBar(track.rating, onRate, starSize = 36.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            HorizontalDivider()
            if (!choosingPlaylist) {
                SheetAction(Icons.Filled.SkipNext, "Play next") { onPlayNext(); onDismiss() }
                SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { onAddToQueue(); onDismiss() }
                SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist…") { choosingPlaylist = true }
                extraActions()
            } else {
                SheetAction(Icons.Filled.Add, "New playlist…") { creating = true }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.playlist.id }) { p ->
                        SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "${p.playlist.name} (${p.trackCount})") {
                            onAddToPlaylist(p)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                creating = false
                onDismiss()
            },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}

/** What a folder menu can do; null hides the entry. */
class FolderActions(
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onAddToPlaylist: (PlaylistSummary) -> Unit,
    val onCreatePlaylist: (String) -> Unit,
    val onDownloadNow: (() -> Unit)?,
    /** Switches "never download"; null when inherited from a parent folder. */
    val onToggleExcluded: (() -> Unit)?,
)

/** Menu of a folder (long-press or ⋮): play, queue, playlist, download, exclude. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderActionsSheet(
    name: String,
    songCount: Int,
    excluded: Boolean,
    excludedByParent: String?,
    playlists: List<PlaylistSummary>,
    actions: FolderActions,
    onDismiss: () -> Unit,
) {
    var choosingPlaylist by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "$songCount songs" + if (excluded) " · never downloaded" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(top = 8.dp))
            if (!choosingPlaylist) {
                SheetAction(Icons.Filled.PlayArrow, "Play") { actions.onPlay(); onDismiss() }
                SheetAction(Icons.Filled.Shuffle, "Shuffle play") { actions.onShuffle(); onDismiss() }
                SheetAction(Icons.Filled.SkipNext, "Play next") { actions.onPlayNext(); onDismiss() }
                SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue") { actions.onAddToQueue(); onDismiss() }
                SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add all to playlist…") { choosingPlaylist = true }
                actions.onDownloadNow?.let { download ->
                    SheetAction(Icons.Filled.Download, "Download to phone now") { download(); onDismiss() }
                }
                when {
                    actions.onToggleExcluded == null && excludedByParent != null ->
                        Text(
                            "Never downloaded because of folder “${excludedByParent.substringAfterLast('/')}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    excluded -> SheetAction(Icons.Filled.CloudDownload, "Allow download") { actions.onToggleExcluded?.invoke(); onDismiss() }
                    else -> SheetAction(Icons.Filled.CloudOff, "Never download (deletes from phone)") {
                        actions.onToggleExcluded?.invoke()
                        onDismiss()
                    }
                }
            } else {
                SheetAction(Icons.Filled.Add, "New playlist…") { creating = true }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.playlist.id }) { p ->
                        SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "${p.playlist.name} (${p.trackCount})") {
                            actions.onAddToPlaylist(p)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = name,
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { playlistName ->
                actions.onCreatePlaylist(playlistName)
                creating = false
                onDismiss()
            },
        )
    }
}
