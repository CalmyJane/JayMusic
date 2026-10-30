package com.calmyjane.jaymusic.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmyjane.jaymusic.data.TrackEntity
import com.calmyjane.jaymusic.ui.MainViewModel
import com.calmyjane.jaymusic.ui.SongSort
import com.calmyjane.jaymusic.ui.components.TrackActionsSheet
import com.calmyjane.jaymusic.ui.components.TrackRow

@Composable
fun LibraryScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("Songs", "Folders").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        if (mode == 0) SongsList(vm) else FolderBrowser(vm)
    }
}

@Composable
private fun SongsList(vm: MainViewModel) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val filter by vm.songFilter.collectAsStateWithLifecycle()
    val allCount = vm.allTracks.collectAsStateWithLifecycle().value.size

    Column {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { q -> vm.songFilter.value = filter.copy(query = q) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { vm.songFilter.value = filter.copy(query = "") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = filter.downloadedOnly,
                    onClick = { vm.songFilter.value = filter.copy(downloadedOnly = !filter.downloadedOnly) },
                    label = { Text("On phone") },
                    leadingIcon = { Icon(Icons.Filled.DownloadDone, contentDescription = null) },
                )
            }
            items(SongSort.entries) { sort ->
                FilterChip(
                    selected = filter.sort == sort,
                    onClick = { vm.songFilter.value = filter.copy(sort = sort) },
                    label = { Text(sort.label) },
                )
            }
        }
        PlayButtons(vm, songs)
        if (allCount == 0) {
            EmptyHint("No songs yet. Set up your Synology in Settings, then tap “Sync now”.")
        } else {
            TrackList(vm, songs)
        }
    }
}

/** "Play" plus the shuffle switch. Shuffle only decides whether the next song is random. */
@Composable
fun PlayButtons(vm: MainViewModel, tracks: List<TrackEntity>) {
    val shuffle = vm.player.state.collectAsStateWithLifecycle().value.shuffle
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { vm.playAll(tracks) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Play")
        }
        val shuffleContent: @Composable () -> Unit = {
            Icon(Icons.Filled.Shuffle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (shuffle) "Shuffle on" else "Shuffle off")
        }
        if (shuffle) {
            Button(onClick = vm::toggleShuffle, modifier = Modifier.weight(1f).height(52.dp)) { shuffleContent() }
        } else {
            OutlinedButton(onClick = vm::toggleShuffle, modifier = Modifier.weight(1f).height(52.dp)) { shuffleContent() }
        }
    }
    Text(
        "${tracks.size} songs",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A list of songs with tap-to-play and a long-press menu. */
@Composable
fun TrackList(
    vm: MainViewModel,
    tracks: List<TrackEntity>,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val cache by vm.cacheEntries.collectAsStateWithLifecycle()
    val rules by vm.folderRules.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<TrackEntity?>(null) }

    LazyColumn(modifier.fillMaxSize()) {
        if (header != null) item { header() }
        itemsIndexed(tracks, key = { _, t -> t.path }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.path == playerState.currentPath,
                isDownloaded = cache[track.path]?.complete == true,
                isExcluded = rules.isExcluded(track.path),
                onClick = { vm.play(tracks, index) },
                onMore = { menuFor = track },
            )
        }
    }
    menuFor?.let { selected ->
        TrackMenu(vm, selected) { menuFor = null }
    }
}

@Composable
fun TrackMenu(vm: MainViewModel, track: TrackEntity, extraActions: @Composable () -> Unit = {}, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    // Show the live rating while the sheet is open.
    val live = vm.trackIndex.collectAsState().value[track.path] ?: track
    TrackActionsSheet(
        track = live,
        playlists = playlists,
        onDismiss = onDismiss,
        onRate = { vm.setRating(track.path, it) },
        onPlayNext = { vm.playNext(track) },
        onAddToQueue = { vm.addToQueue(track) },
        onAddToPlaylist = { vm.addToPlaylist(it, track.path) },
        onCreatePlaylist = { vm.createPlaylist(it, track.path) },
        extraActions = extraActions,
    )
}

@Composable
private fun FolderBrowser(vm: MainViewModel) {
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val content by remember(folder) { vm.folderContent(folder) }.collectAsStateWithLifecycle(initialValue = null)
    val rules by vm.folderRules.collectAsStateWithLifecycle()

    Column {
        if (folder != null) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    val parent = folder!!.substringBeforeLast('/', "")
                    val roots = vm.settings.value.musicFolders
                    folder = if (parent.isEmpty() || roots.none { parent.startsWith(it.trimEnd('/')) }) null else parent
                }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up", modifier = Modifier.padding(8.dp))
                Text(folder!!, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val (folders, songs) = content ?: return@Column
        if (folders.isEmpty() && songs.isEmpty()) {
            EmptyHint("Nothing here yet.")
            return@Column
        }
        TrackList(vm, songs, header = {
            Column {
                for (node in folders) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { folder = node.path }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${node.songCount} songs" + if (rules.isExcluded(node.path)) " · not downloaded" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    PlayButtons(vm, songs)
                }
            }
        })
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}
