package com.calmyjane.jaymusic.ui.lockscreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.calmyjane.jaymusic.AppContainer
import com.calmyjane.jaymusic.data.PlaylistSummary
import com.calmyjane.jaymusic.data.TrackEntity
import com.calmyjane.jaymusic.playback.PlayerConnection
import com.calmyjane.jaymusic.ui.components.NameDialog
import com.calmyjane.jaymusic.ui.components.RatingBar
import com.calmyjane.jaymusic.ui.components.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Lock screen player, modelled on car-mode players (e.g. Spotify's car view): the two most used
 * actions, previous and next, are the largest targets at the top half; the queue sits in the middle
 * like a "now playing" list; play/pause is a wide bar at the bottom with small shuffle/repeat
 * toggles. Swipe up or tap an empty black area to get to the normal lock screen.
 */

private val Gray = Color(0xFFBDBDBD)
private val DimGray = Color(0xFF616161)

@Composable
fun LockScreenPlayer(
    container: AppContainer,
    player: PlayerConnection,
    scope: CoroutineScope,
    onClose: () -> Unit,
    onUnlock: () -> Unit,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val index by container.library.trackIndex.collectAsStateWithLifecycle()
    val track = state.currentPath?.let(index::get)
    var searching by rememberSaveable { mutableStateOf(false) }
    var playlistFor by remember { mutableStateOf<TrackEntity?>(null) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Empty black areas: tap closes, swipe up unlocks.
            .pointerInput(searching) { if (!searching) detectTapGestures(onTap = { onClose() }) }
            .pointerInput(searching) {
                if (searching) return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -swipeThreshold) onUnlock() },
                ) { change, dragAmount ->
                    total += dragAmount
                    change.consume()
                }
            },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp),
        ) {
            // Song, rating, search, add to playlist.
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        state.title.ifEmpty { "Nothing playing" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(state.artist, style = MaterialTheme.typography.bodyMedium, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { track?.let { playlistFor = it } }, enabled = track != null, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { searching = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                if (track != null) {
                    RatingBar(track.rating, { value -> scope.launch { container.library.setRating(track.path, value) } }, starSize = 28.dp)
                }
            }

            // Previous / next: the biggest targets.
            Row(Modifier.fillMaxWidth().weight(1.2f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(Icons.Filled.SkipPrevious, "Previous", Modifier.weight(1f).fillMaxHeight(), onClick = player::previous)
                BigButton(Icons.Filled.SkipNext, "Next", Modifier.weight(1f).fillMaxHeight(), onClick = player::next)
            }

            Progress(player, state.isPlaying, state.currentPath, state.durationMs)

            // Up next.
            QueueList(player, state, Modifier.weight(1f).fillMaxWidth())

            // Play/pause bar with small toggles.
            Row(
                Modifier.fillMaxWidth().height(88.dp).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallToggle(Icons.Filled.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", state.shuffle) {
                    player.setShuffle(!state.shuffle)
                }
                BigButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    filled = true,
                    iconSize = 56.dp,
                    onClick = player::togglePlayPause,
                )
                SmallToggle(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        else -> "Repeat off"
                    },
                    state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = player::cycleRepeat,
                )
            }

            // Swipe hint.
            Column(Modifier.fillMaxWidth().height(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = DimGray, modifier = Modifier.size(20.dp))
                Text("Swipe up to unlock", style = MaterialTheme.typography.labelSmall, color = DimGray)
            }
        }

        if (searching) {
            SearchOverlay(
                container = container,
                onDismiss = { searching = false },
                onPlayNow = { player.playNow(it); searching = false },
                onPlayNext = { player.playNext(it) },
                onAddToPlaylist = { playlistFor = it },
            )
        }
    }

    playlistFor?.let { selected ->
        PlaylistPicker(
            container = container,
            onDismiss = { playlistFor = null },
            onPick = { playlist ->
                scope.launch { container.library.addToPlaylist(playlist.playlist.id, listOf(selected.path)) }
                playlistFor = null
            },
            onCreate = { name ->
                scope.launch {
                    val id = container.library.createPlaylist(name)
                    container.library.addToPlaylist(id, listOf(selected.path))
                }
                playlistFor = null
            },
        )
    }
}

@Composable
private fun Progress(player: PlayerConnection, isPlaying: Boolean, path: String?, durationMs: Long) {
    var position by remember { mutableLongStateOf(player.positionMs()) }
    LaunchedEffect(isPlaying, path) {
        position = player.positionMs()
        while (isPlaying) {
            delay(500)
            position = player.positionMs()
        }
    }
    Column(Modifier.fillMaxWidth().height(32.dp), verticalArrangement = Arrangement.Center) {
        LinearProgressIndicator(
            progress = { if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Color.White,
            trackColor = Color(0xFF303030),
            drawStopIndicator = {},
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(formatDuration(position), style = MaterialTheme.typography.labelSmall, color = Gray)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall, color = Gray)
        }
    }
}

@Composable
private fun QueueList(player: PlayerConnection, state: com.calmyjane.jaymusic.playback.PlayerUiState, modifier: Modifier) {
    // Re-read whenever the player state changes (new song, shuffle, queue edits).
    val entries = remember(state) { player.upcoming(50) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentPath) { listState.scrollToItem(0) }
    Column(modifier.border(1.dp, DimGray, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Gray, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("UP NEXT", style = MaterialTheme.typography.labelMedium, color = Gray)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(entries, key = { "${it.index}:${it.mediaId}" }) { entry ->
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable { player.skipToQueueIndex(entry.index) }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.isCurrent) {
                        Icon(Icons.Filled.Equalizer, contentDescription = "Playing", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (entry.isCurrent) FontWeight.Black else FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    filled: Boolean = false,
    iconSize: Dp = 112.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .background(if (filled) Color.White else Color(0xFF141414), shape)
            .border(2.dp, Color.White, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(56.dp)
            .background(if (active) Color.White else Color.Black, shape)
            .border(2.dp, if (active) Color.White else DimGray, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(28.dp))
    }
}

/** Quick search: jump to a song, queue it next or add it to a playlist. */
@Composable
private fun SearchOverlay(
    container: AppContainer,
    onDismiss: () -> Unit,
    onPlayNow: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val tracks by container.library.allTracks.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var queued by remember { mutableStateOf(setOf<String>()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(query, tracks) {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
            tracks.filter { it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt }.take(30)
        } else {
            tracks.asSequence().filter { t ->
                terms.all { term ->
                    t.title.lowercase().contains(term) ||
                        t.artist?.lowercase()?.contains(term) == true ||
                        t.album?.lowercase()?.contains(term) == true
                }
            }.take(100).toList()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().imePadding()
            // Swallow taps so they do not close the player underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search songs, artists, albums") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C1C),
                    unfocusedContainerColor = Color(0xFF1C1C1C),
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        Text(
            if (query.isBlank()) "RECENTLY PLAYED" else "${results.size}${if (results.size == 100) "+" else ""} RESULTS",
            style = MaterialTheme.typography.labelMedium,
            color = Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.path }) { track ->
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable { onPlayNow(track) }.padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(track.artist, track.album).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val isQueued = track.path in queued
                    IconButton(
                        onClick = {
                            onPlayNext(track)
                            queued = queued + track.path
                        },
                        enabled = !isQueued,
                    ) {
                        Icon(
                            if (isQueued) Icons.Filled.Equalizer else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = if (isQueued) "Plays next" else "Play next",
                            tint = if (isQueued) DimGray else Color.White,
                        )
                    }
                    IconButton(onClick = { onAddToPlaylist(track) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.White)
                    }
                }
                HorizontalDivider(color = Color(0xFF1C1C1C))
            }
        }
    }
}

@Composable
private fun PlaylistPicker(
    container: AppContainer,
    onDismiss: () -> Unit,
    onPick: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
) {
    val playlists by container.library.playlistSummaries.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", "", "Create", onDismiss = { creating = false }, onConfirm = onCreate)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn(Modifier.height(320.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable { creating = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("New playlist…", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(playlists, key = { it.playlist.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clickable { onPick(p) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${p.playlist.name} (${p.trackCount})",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
