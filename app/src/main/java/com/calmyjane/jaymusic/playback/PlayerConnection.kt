package com.calmyjane.jaymusic.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.calmyjane.jaymusic.cache.MediaCache
import com.calmyjane.jaymusic.data.TrackEntity
import com.calmyjane.jaymusic.remote.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Everything the UI shows about playback. */
data class PlayerUiState(
    val connected: Boolean = false,
    val currentPath: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: ByteArray? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueSize: Int = 0,
) {
    val hasMedia: Boolean get() = currentPath != null

    // ByteArray uses identity equality; good enough for change detection here.
    override fun equals(other: Any?): Boolean = other is PlayerUiState &&
        connected == other.connected && currentPath == other.currentPath && title == other.title &&
        artist == other.artist && album == other.album && artwork === other.artwork &&
        isPlaying == other.isPlaying && isBuffering == other.isBuffering && durationMs == other.durationMs &&
        shuffle == other.shuffle && repeatMode == other.repeatMode && queueSize == other.queueSize

    override fun hashCode(): Int = currentPath.hashCode() * 31 + isPlaying.hashCode()
}

/** Result of a play request; the UI shows a hint when songs had to be left out. */
sealed interface PlayResult {
    data object Started : PlayResult
    data class StartedOffline(val skipped: Int) : PlayResult
    data object NothingAvailable : PlayResult
}

/**
 * UI-side handle to the [PlaybackService] through a [MediaController]. The controller connects
 * asynchronously within milliseconds; commands issued before that are queued.
 */
class PlayerConnection(
    private val context: Context,
    private val cache: MediaCache,
    private val connection: ServerConnection,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            publish(c)
            pending.forEach { it(c) }
            pending.clear()
        }
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlayerUiState()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending += action
    }

    private fun publish(player: Player) {
        val metadata = player.mediaMetadata
        val item = player.currentMediaItem
        _state.value = PlayerUiState(
            connected = true,
            currentPath = item?.mediaId,
            title = (metadata.title ?: item?.mediaMetadata?.title)?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            artwork = metadata.artworkData,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueSize = player.mediaItemCount,
        )
    }

    /** Current position; polled by the UI while visible. */
    fun positionMs(): Long = controller?.currentPosition ?: 0

    /**
     * Plays [tracks] starting at [startIndex]. When offline, songs that are not on the phone are
     * left out, so playback starts instantly with what is available.
     */
    fun play(tracks: List<TrackEntity>, startIndex: Int = 0, shuffle: Boolean? = null): PlayResult {
        if (tracks.isEmpty()) return PlayResult.NothingAvailable
        val start = tracks.getOrNull(startIndex) ?: tracks.first()
        val (playable, skipped) = if (connection.isOnline) {
            tracks to 0
        } else {
            val available = tracks.filter { cache.isComplete(it.path) }
            available to (tracks.size - available.size)
        }
        if (playable.isEmpty()) return PlayResult.NothingAvailable
        val index = playable.indexOfFirst { it.path == start.path }
        val items = playable.map(MediaItems::from)
        withController { c ->
            shuffle?.let { c.shuffleModeEnabled = it }
            if (shuffle == true && startIndex < 0) {
                // "Shuffle all": start with a random song.
                c.setMediaItems(items, items.indices.random(), 0)
            } else {
                c.setMediaItems(items, index.coerceAtLeast(0), 0)
            }
            c.prepare()
            c.play()
        }
        return if (skipped > 0) PlayResult.StartedOffline(skipped) else PlayResult.Started
    }

    fun playNext(track: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) {
            play(listOf(track))
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, MediaItems.from(track))
        }
    }

    fun addToQueue(track: TrackEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(track)) else c.addMediaItem(MediaItems.from(track))
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    /** Restarts the song if more than 3 s in, otherwise goes to the previous song. */
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun setShuffle(enabled: Boolean) = withController {
        it.sendCustomCommand(
            SessionCommand(if (enabled) PlaybackService.CMD_SHUFFLE_ON else PlaybackService.CMD_SHUFFLE_OFF, Bundle.EMPTY),
            Bundle.EMPTY,
        )
    }

    fun cycleRepeat() = withController {
        it.sendCustomCommand(SessionCommand(PlaybackService.CMD_CYCLE_REPEAT, Bundle.EMPTY), Bundle.EMPTY)
    }

    /** The current queue in play order, for the queue view. */
    fun queue(): List<MediaItem> {
        val c = controller ?: return emptyList()
        return List(c.mediaItemCount) { c.getMediaItemAt(it) }
    }

    fun skipToQueueIndex(index: Int) = withController { it.seekTo(index, 0) }
}
