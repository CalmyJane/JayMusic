package com.calmyjane.jaymusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.res.painterResource
import com.calmyjane.jaymusic.R
import com.calmyjane.jaymusic.data.TrackEntity
import com.calmyjane.jaymusic.ui.MainViewModel
import com.calmyjane.jaymusic.ui.SongSort
import com.calmyjane.jaymusic.ui.components.DownloadToggle
import com.calmyjane.jaymusic.ui.components.FolderActions
import com.calmyjane.jaymusic.ui.components.FolderActionsSheet
import com.calmyjane.jaymusic.ui.components.SheetAction
import com.calmyjane.jaymusic.ui.FolderNode
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Download
import com.calmyjane.jaymusic.ui.components.TrackActionsSheet
import com.calmyjane.jaymusic.ui.components.TrackRow

@Composable
fun LibraryScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val filter by vm.songFilter.collectAsStateWithLifecycle()
    val allCount = vm.allTracks.collectAsStateWithLifecycle().value.size

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        // One slim bar: view switch, sort, "on phone" filter and search.
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                IconButton(onClick = {
                    searching = false
                    vm.songFilter.value = filter.copy(query = "")
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search") }
                BasicSearchField(
                    value = filter.query,
                    onValueChange = { q -> vm.songFilter.value = filter.copy(query = q) },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { vm.songFilter.value = filter.copy(query = "") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            } else {
                // Logo left, songs/folders centered, sort and search right.
                Box(Modifier.fillMaxSize()) {
                    Icon(
                        painterResource(R.drawable.ic_logo),
                        contentDescription = "JayMusic",
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(26.dp),
                    )
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ViewToggle(Icons.Filled.MusicNote, "Songs", mode == 0) { mode = 0 }
                        ViewToggle(Icons.Filled.Folder, "Folders", mode == 1) { mode = 1 }
                    }
                    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                        SortMenu(
                            current = filter.sort,
                            downloadedOnly = filter.downloadedOnly,
                            onSelect = { vm.songFilter.value = filter.copy(sort = it) },
                            onToggleDownloadedOnly = { vm.songFilter.value = filter.copy(downloadedOnly = !filter.downloadedOnly) },
                        )
                        IconButton(onClick = { searching = true; mode = 0 }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when {
            allCount == 0 -> EmptyHint("No songs yet. Set up your Synology in Settings, then tap “Sync now”.")
            mode == 0 || searching -> TrackList(vm, vm.songs.collectAsStateWithLifecycle().value)
            else -> FolderBrowser(vm)
        }
    }
}

/** Icon button that is drawn inverted while [selected]. */
@Composable
private fun ViewToggle(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        )
    } else {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        )
    }
    FilledIconButton(onClick = onClick, colors = colors, modifier = Modifier.size(44.dp).semantics { contentDescription = description }) {
        Icon(icon, contentDescription = null)
    }
}

/** Sort order plus the "only songs on this phone" filter. */
@Composable
private fun SortMenu(
    current: SongSort,
    downloadedOnly: Boolean,
    onSelect: (SongSort) -> Unit,
    onToggleDownloadedOnly: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort and filter")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Only on this phone") },
                onClick = { onToggleDownloadedOnly(); open = false },
                leadingIcon = { Icon(Icons.Filled.DownloadDone, contentDescription = null) },
                trailingIcon = { if (downloadedOnly) Icon(Icons.Filled.Check, contentDescription = "On") },
            )
            HorizontalDivider()
            SongSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = { onSelect(sort); open = false },
                    trailingIcon = { if (sort == current) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                )
            }
        }
    }
}

@Composable
private fun BasicSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onBackground),
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("Search songs, artists, albums", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
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
            val excludedBy = rules.excludedBy(track.path)
            TrackRow(
                track = track,
                isCurrent = track.path == playerState.currentPath,
                isDownloaded = cache[track.path]?.complete == true,
                isExcluded = excludedBy != null,
                onClick = { vm.play(tracks, index) },
                onMore = { menuFor = track },
                onToggleDownload = if (excludedBy == null || excludedBy == track.path) {
                    { vm.setFolderExcluded(track.path, excludedBy == null); Unit }
                } else {
                    null
                },
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
    val rules by vm.folderRules.collectAsState()
    val excludedBy = rules.excludedBy(track.path)
    TrackActionsSheet(
        track = live,
        playlists = playlists,
        onDismiss = onDismiss,
        onRate = { vm.setRating(track.path, it) },
        onPlayNext = { vm.playNext(track) },
        onAddToQueue = { vm.addToQueue(track) },
        onAddToPlaylist = { vm.addToPlaylist(it, track.path) },
        onCreatePlaylist = { vm.createPlaylist(it, track.path) },
        extraActions = {
            extraActions()
            if (excludedBy == null) {
                SheetAction(Icons.Filled.Download, "Download to phone now") {
                    vm.downloadNow(listOf(track), track.title)
                    onDismiss()
                }
            }
            when {
                excludedBy == null -> SheetAction(Icons.Filled.CloudOff, "Never download") {
                    vm.setFolderExcluded(track.path, true)
                    onDismiss()
                }
                excludedBy == track.path -> SheetAction(Icons.Outlined.CloudDownload, "Allow download") {
                    vm.setFolderExcluded(track.path, false)
                    onDismiss()
                }
                // Excluded through its folder; that is changed in the folder view.
                else -> Unit
            }
        },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderBrowser(vm: MainViewModel) {
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val content by remember(folder) { vm.folderContent(folder) }.collectAsStateWithLifecycle(initialValue = null)
    val rules by vm.folderRules.collectAsStateWithLifecycle()
    var folderMenu by remember { mutableStateOf<FolderNode?>(null) }

    folderMenu?.let { node -> FolderMenu(vm, node) { folderMenu = null } }

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
                    val excludedBy = rules.excludedBy(node.path)
                    Row(
                        Modifier.fillMaxWidth().height(54.dp)
                            .combinedClickable(onClick = { folder = node.path }, onLongClick = { folderMenu = node })
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${node.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DownloadToggle(
                            excluded = excludedBy != null,
                            onPhone = false,
                            // Inherited from a parent folder: change it there.
                            enabled = excludedBy == null || excludedBy == node.path,
                            onToggle = { vm.setFolderExcluded(node.path, excludedBy == null) },
                        )
                    }
                }
                }
            }
        })
    }
}

/** Long-press / ⋮ menu of a folder; all actions apply to the songs in it and its sub folders. */
@Composable
private fun FolderMenu(vm: MainViewModel, node: FolderNode, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsState()
    val rules by vm.folderRules.collectAsState()
    val excludedBy = rules.excludedBy(node.path)
    val tracks = remember(node.path) { vm.tracksIn(node.path) }
    FolderActionsSheet(
        name = node.name,
        songCount = tracks.size,
        excluded = excludedBy != null,
        excludedByParent = excludedBy?.takeIf { it != node.path },
        playlists = playlists,
        actions = FolderActions(
            onPlay = { vm.play(tracks, 0) },
            onShuffle = {
                if (!vm.player.state.value.shuffle) vm.toggleShuffle()
                vm.play(tracks, -1)
            },
            onPlayNext = { vm.playNextAll(tracks, node.name) },
            onAddToQueue = { vm.addAllToQueue(tracks, node.name) },
            onAddToPlaylist = { vm.addAllToPlaylist(it, tracks) },
            onCreatePlaylist = { vm.createPlaylistWith(it, tracks) },
            onDownloadNow = if (excludedBy == null) ({ vm.downloadNow(tracks, node.name); Unit }) else null,
            onToggleExcluded = if (excludedBy == null || excludedBy == node.path) {
                { vm.setFolderExcluded(node.path, excludedBy == null); Unit }
            } else {
                null
            },
        ),
        onDismiss = onDismiss,
    )
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
