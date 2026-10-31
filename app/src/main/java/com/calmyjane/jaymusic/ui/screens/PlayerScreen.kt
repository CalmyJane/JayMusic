package com.calmyjane.jaymusic.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.calmyjane.jaymusic.ui.LockScreenActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.calmyjane.jaymusic.data.TrackEntity
import com.calmyjane.jaymusic.playback.PlayerUiState
import com.calmyjane.jaymusic.ui.MainViewModel
import com.calmyjane.jaymusic.ui.components.RatingBar
import com.calmyjane.jaymusic.ui.components.TrackActionsSheet
import com.calmyjane.jaymusic.ui.components.formatDuration
import kotlinx.coroutines.delay

/** Live playback position, refreshed while the composable is shown. */
@Composable
private fun rememberPosition(vm: MainViewModel, state: PlayerUiState): Long {
    var position by remember { mutableLongStateOf(vm.player.positionMs()) }
    LaunchedEffect(state.isPlaying, state.currentPath) {
        position = vm.player.positionMs()
        while (state.isPlaying) {
            delay(250)
            position = vm.player.positionMs()
        }
    }
    return position
}

@Composable
private fun rememberArtwork(data: ByteArray?): ImageBitmap? = remember(data) {
    data?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
}

/**
 * Player bar at the bottom of the app: song (tap to open the full player), a seek slider that
 * works while playing or paused, and shuffle / previous / play / next / repeat. Always the same height so the layout never jumps.
 */
@Composable
fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val position = rememberPosition(vm, state)
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable(enabled = state.hasMedia, onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.hasMedia) state.title else "Nothing playing",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.hasMedia) state.artist else " ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${formatDuration(if (seeking) (seekValue * duration).toLong() else position)} / ${formatDuration(state.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = if (seeking) seekValue else (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { seeking = true; seekValue = it },
            onValueChangeFinished = {
                vm.player.seekTo((seekValue * duration).toLong())
                seeking = false
            },
            enabled = state.hasMedia,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallModeButton(Icons.Filled.Shuffle, if (state.shuffle) "Shuffle on" else "Shuffle off", state.shuffle) {
                vm.player.setShuffle(!state.shuffle)
            }
            IconButton(onClick = vm.player::previous, enabled = state.hasMedia, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(44.dp))
            }
            FilledIconButton(
                onClick = vm.player::togglePlayPause,
                enabled = state.hasMedia,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.size(60.dp),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.background, strokeWidth = 3.dp)
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            IconButton(onClick = vm.player::next, enabled = state.hasMedia, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(44.dp))
            }
            SmallModeButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = vm.player::cycleRepeat,
            )
        }
    }
}

/** Small shuffle/repeat toggle, filled while active. */
@Composable
private fun SmallModeButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val colors = if (active) {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
        )
    } else {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        )
    }
    FilledIconButton(onClick = onClick, colors = colors, modifier = Modifier.size(40.dp).semantics { contentDescription = description }) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

/** Full screen player with big controls. */
@Composable
fun PlayerScreen(vm: MainViewModel, onClose: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val index by vm.trackIndex.collectAsStateWithLifecycle()
    val track: TrackEntity? = state.currentPath?.let(index::get)
    val position = rememberPosition(vm, state)
    val artwork = rememberArtwork(state.artwork)
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var showPlaylistSheet by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player", modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.weight(1f))
            if (track != null) {
                IconButton(onClick = { showPlaylistSheet = true }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist", modifier = Modifier.size(32.dp))
                }
            }
        }

        Box(
            Modifier.weight(1f, fill = true).fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier.aspectRatio(1f).clip(shape).border(2.dp, MaterialTheme.colorScheme.onBackground, shape),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    Image(artwork, contentDescription = "Cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(120.dp))
                }
            }
        }

        Text(
            state.title.ifEmpty { "Nothing playing" },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOf(state.artist, state.album).filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Fixed height so the controls below never move.
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            if (track != null) {
                RatingBar(track.rating, onRate = { vm.setRating(track.path, it) }, starSize = 44.dp)
            }
        }

        val duration = state.durationMs.coerceAtLeast(1)
        Slider(
            value = if (seeking) seekValue else (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { seeking = true; seekValue = it },
            onValueChangeFinished = {
                vm.player.seekTo((seekValue * duration).toLong())
                seeking = false
            },
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(if (seeking) (seekValue * duration).toLong() else position), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleButton(
                active = state.shuffle,
                onClick = { vm.player.setShuffle(!state.shuffle) },
                description = if (state.shuffle) "Shuffle on" else "Shuffle off",
            ) { Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(30.dp)) }

            IconButton(onClick = vm.player::previous, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(52.dp))
            }
            FilledIconButton(
                onClick = vm.player::togglePlayPause,
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                if (state.isBuffering) {
                    CircularProgressIndicator(
                        Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.background,
                        strokeWidth = 4.dp,
                    )
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            IconButton(onClick = vm.player::next, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(52.dp))
            }
            ToggleButton(
                active = state.repeatMode != Player.REPEAT_MODE_OFF,
                onClick = vm.player::cycleRepeat,
                description = when (state.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else -> "Repeat off"
                },
            ) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "${state.queueSize} songs in queue",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }

    if (showPlaylistSheet && track != null) {
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        TrackActionsSheet(
            track = track,
            playlists = playlists,
            onDismiss = { showPlaylistSheet = false },
            onRate = { vm.setRating(track.path, it) },
            onPlayNext = {},
            onAddToQueue = {},
            onAddToPlaylist = { vm.addToPlaylist(it, track.path) },
            onCreatePlaylist = { vm.createPlaylist(it, track.path) },
        )
    }
}

/** Icon button that is drawn inverted (filled) when [active], so the state is obvious at a glance. */
@Composable
private fun ToggleButton(active: Boolean, onClick: () -> Unit, description: String, icon: @Composable () -> Unit) {
    val colors = if (active) {
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
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).semantics { contentDescription = description },
        colors = colors,
    ) { icon() }
}
