package com.calmyjane.jaymusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmyjane.jaymusic.data.PlaylistTrack
import com.calmyjane.jaymusic.ui.MainViewModel
import com.calmyjane.jaymusic.ui.components.ConfirmDialog
import com.calmyjane.jaymusic.ui.components.NameDialog
import com.calmyjane.jaymusic.ui.components.SheetAction
import com.calmyjane.jaymusic.ui.components.TrackRow

@Composable
fun PlaylistsScreen(vm: MainViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Button(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New playlist")
        }
        if (playlists.isEmpty()) {
            EmptyHint("No playlists yet. Create one here, or long-press any song and choose “Add to playlist”.")
        }
        LazyColumn {
            items(playlists, key = { it.playlist.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(p.playlist.id) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.trackCount} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (creating) {
        NameDialog("New playlist", "", "Create", onDismiss = { creating = false }) {
            vm.createPlaylist(it)
            creating = false
        }
    }
}

@Composable
fun PlaylistDetailScreen(vm: MainViewModel, playlistId: Long, contentPadding: PaddingValues, onBack: () -> Unit) {
    val playlist by remember(playlistId) { vm.playlist(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val entries by remember(playlistId) { vm.playlistTracks(playlistId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val cache by vm.cacheEntries.collectAsStateWithLifecycle()
    val rules by vm.folderRules.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<PlaylistTrack?>(null) }
    val tracks = entries.map { it.track }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                playlist?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete playlist") }
        }
        PlayButtons(vm, tracks)
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(entries, key = { _, e -> e.entryId }) { index, entry ->
                TrackRow(
                    track = entry.track,
                    isCurrent = entry.track.path == playerState.currentPath,
                    isDownloaded = cache[entry.track.path]?.complete == true,
                    isExcluded = rules.isExcluded(entry.track.path),
                    onClick = { vm.play(tracks, index) },
                    onMore = { menuFor = entry },
                )
            }
        }
    }

    menuFor?.let { entry ->
        TrackMenu(vm, entry.track, extraActions = {
            SheetAction(Icons.Filled.ArrowUpward, "Move up") {
                vm.movePlaylistEntry(playlistId, entry.entryId, -1)
                menuFor = null
            }
            SheetAction(Icons.Filled.ArrowDownward, "Move down") {
                vm.movePlaylistEntry(playlistId, entry.entryId, +1)
                menuFor = null
            }
            SheetAction(Icons.Filled.RemoveCircleOutline, "Remove from playlist") {
                vm.removeFromPlaylist(entry.entryId)
                menuFor = null
            }
        }) { menuFor = null }
    }
    if (renaming) {
        NameDialog("Rename playlist", playlist?.name.orEmpty(), "Rename", onDismiss = { renaming = false }) {
            vm.renamePlaylist(playlistId, it)
            renaming = false
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete playlist?",
            text = "“${playlist?.name.orEmpty()}” will be deleted. The songs stay in your library.",
            confirm = "Delete",
            onDismiss = { deleting = false },
        ) {
            vm.deletePlaylist(playlistId)
            onBack()
        }
    }
}
