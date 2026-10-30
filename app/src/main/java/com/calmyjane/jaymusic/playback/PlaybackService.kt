package com.calmyjane.jaymusic.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.calmyjane.jaymusic.AppContainer
import com.calmyjane.jaymusic.JayMusicApp
import com.calmyjane.jaymusic.R
import com.calmyjane.jaymusic.ui.MainActivity
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays music in the background and publishes the media session that drives the notification
 * and the big lock screen player (play/pause, previous, next, shuffle and repeat buttons).
 *
 * Responsiveness rules implemented here:
 * - Upcoming songs (in shuffle order, if enabled) are downloaded ahead of time.
 * - When offline, songs that are not on the phone are skipped instantly.
 * - When a song does not start within [STALL_TIMEOUT_MS] (slow server), the next downloaded song
 *   is played instead of waiting.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private var prefetchJob: Job? = null
    private var stallJob: Job? = null
    private var saveJob: Job? = null

    /** Song whose play was already counted, so it is counted once per listen. */
    private var countedPath: String? = null
    private var currentPath: String? = null
    private val taggedPaths = HashSet<String>()
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        container = (application as JayMusicApp).container
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(container.mediaCache.playbackDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Start playing after half a second of audio instead of the default 2.5 s.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 120_000, 500, 1_500)
                    .build(),
            )
            .build()
        player.addListener(PlayerListener())

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, HistoryPlayer(player))
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(mediaButtons())
            .build()

        restoreQueue()
        startTicker()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Opens the big lock screen player when the screen turns off/on during playback. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!container.settings.state.value.lockScreenPlayer) return
            if (player.mediaItemCount == 0 || !player.playWhenReady) return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> LockScreenLauncher.onScreenOff(this@PlaybackService)
                Intent.ACTION_SCREEN_ON -> LockScreenLauncher.onScreenOn(this@PlaybackService)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        saveQueueNow()
        container.mediaCache.protectedPaths = emptySet()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    // Lock screen / notification buttons

    private fun mediaButtons(): List<CommandButton> {
        val shuffle = player.shuffleModeEnabled
        val shuffleButton = CommandButton.Builder(if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(getString(if (shuffle) R.string.shuffle_on else R.string.shuffle_off))
            .setSessionCommand(SessionCommand(if (shuffle) CMD_SHUFFLE_OFF else CMD_SHUFFLE_ON, Bundle.EMPTY))
            .build()
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            else -> CommandButton.ICON_REPEAT_OFF
        }
        val repeatButton = CommandButton.Builder(repeatIcon)
            .setDisplayName("Repeat")
            .setSessionCommand(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
            .build()
        // Previous and next are pinned to their usual places so shuffle/repeat never replace them.
        val previousButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Previous")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val nextButton = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Next")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
        return listOf(previousButton, nextButton, shuffleButton, repeatButton)
    }

    // Shuffle history

    /** Songs actually played, most recent last, so "previous" in shuffle mode goes back in time. */
    private val history = ArrayDeque<String>()

    /** Song we are going back to; its transition must not be added to the history. */
    private var backTarget: String? = null

    /** Queue size and song the shuffle order was last built for. */
    private var shuffledFor: Pair<Int, String?>? = null

    /**
     * Wraps the player for the media session (app, notification, lock screen, headset):
     * "previous" in shuffle mode returns to the song that was really played before.
     */
    private inner class HistoryPlayer(player: Player) : ForwardingPlayer(player) {
        override fun seekToPrevious() {
            if (currentPosition > RESTART_THRESHOLD_MS || !goBackInHistory()) super.seekToPrevious()
        }

        override fun seekToPreviousMediaItem() {
            if (!goBackInHistory()) super.seekToPreviousMediaItem()
        }
    }

    private fun goBackInHistory(): Boolean {
        if (!player.shuffleModeEnabled) return false
        while (history.isNotEmpty()) {
            val path = history.removeLast()
            val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == path } ?: continue
            backTarget = path
            player.seekTo(index, 0)
            return true
        }
        return false
    }

    /** In shuffle mode, moves the item at [index] directly behind the current song in play order. */
    private fun makeNextInShuffle(index: Int) {
        if (!player.shuffleModeEnabled || index !in 0 until player.mediaItemCount) return
        val timeline = player.currentTimeline
        val current = player.currentMediaItemIndex
        val order = ArrayList<Int>(player.mediaItemCount)
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET && order.size < player.mediaItemCount) {
            if (i != index) order += i
            if (i == current) order += index
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        if (order.size != player.mediaItemCount) return
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
        schedulePrefetch()
    }

    /**
     * Builds a new random order that starts with the current song, so "next" is random among all
     * other songs. Called when shuffle is switched on or a new list is started.
     */
    private fun reshuffleFromCurrent() {
        val count = player.mediaItemCount
        if (count < 2) return
        val current = player.currentMediaItemIndex
        val rest = (0 until count).filter { it != current }.shuffled()
        val order = IntArray(count).also { array ->
            array[0] = current
            rest.forEachIndexed { i, index -> array[i + 1] = index }
        }
        player.setShuffleOrder(DefaultShuffleOrder(order, System.nanoTime()))
        shuffledFor = count to player.currentMediaItem?.mediaId
    }

    private fun updateMediaButtons() {
        mediaSession?.setMediaButtonPreferences(mediaButtons())
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SHUFFLE_ON, Bundle.EMPTY))
                .add(SessionCommand(CMD_SHUFFLE_OFF, Bundle.EMPTY))
                .add(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_MAKE_NEXT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_MAKE_NEXT -> makeNextInShuffle(args.getInt(EXTRA_INDEX, -1))
                CMD_SHUFFLE_ON -> player.shuffleModeEnabled = true
                CMD_SHUFFLE_OFF -> player.shuffleModeEnabled = false
                CMD_CYCLE_REPEAT -> player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(MediaItems::resolve).toMutableList())

        /** Resume from the lock screen / headset button after the app was closed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val restored = loadSavedQueue() ?: throw UnsupportedOperationException("Nothing to resume")
            MediaSession.MediaItemsWithStartPosition(restored.items, restored.index, restored.positionMs)
        }
    }

    // Player events

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val path = mediaItem?.mediaId
            val previous = currentPath
            if (previous != null && previous != path) {
                if (backTarget == path) {
                    backTarget = null
                } else {
                    history.addLast(previous)
                    if (history.size > MAX_HISTORY) history.removeFirst()
                }
            }
            // A new list was started: shuffle it from the chosen song.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled &&
                shuffledFor != (player.mediaItemCount to path) && path != previous
            ) {
                reshuffleFromCurrent()
            }
            if (path != previous || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                countedPath = null
            }
            currentPath = path
            // The previous song may now be completely stored (it was cached while streaming).
            if (previous != null && previous != path) {
                scope.launch(Dispatchers.IO) {
                    container.mediaCache.refresh(previous)
                    container.mediaCache.enforceBudget()
                }
            }
            skipIfUnavailable()
            schedulePrefetch()
            saveQueueSoon()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                schedulePrefetch()
                saveQueueSoon()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // Switching shuffle on never changes the current song; only what comes next.
            if (shuffleModeEnabled) reshuffleFromCurrent()
            updateMediaButtons()
            schedulePrefetch()
            saveQueueSoon()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateMediaButtons()
            schedulePrefetch()
            saveQueueSoon()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            stallJob?.cancel()
            when (playbackState) {
                Player.STATE_BUFFERING -> if (player.playWhenReady) startStallWatchdog()
                Player.STATE_READY -> {
                    consecutiveErrors = 0
                    storeDuration()
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && player.playbackState == Player.STATE_BUFFERING) startStallWatchdog()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveQueueSoon()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error for $currentPath: ${error.errorCodeName}", error)
            currentPath?.let { path -> scope.launch(Dispatchers.IO) { container.mediaCache.refresh(path) } }
            if (++consecutiveErrors > player.mediaItemCount.coerceAtMost(MAX_CONSECUTIVE_ERRORS)) {
                consecutiveErrors = 0
                return // Give up; the user can pick something else.
            }
            val next = nextIndex { container.mediaCache.isComplete(it) }
                ?: if (container.connection.isOnline) nextIndex { true } else null
            if (next != null) {
                player.seekTo(next, 0)
                player.prepare()
                player.play()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            readTags(tracks)
        }
    }

    /** Skips the current song right away if it cannot be played (offline and not on the phone). */
    private fun skipIfUnavailable() {
        val path = player.currentMediaItem?.mediaId ?: return
        if (container.connection.isOnline || container.mediaCache.isComplete(path)) return
        val next = nextIndex { container.mediaCache.isComplete(it) }
        if (next != null) {
            player.seekTo(next, 0)
        } else {
            player.pause()
        }
    }

    private fun startStallWatchdog() {
        stallJob?.cancel()
        stallJob = scope.launch {
            delay(STALL_TIMEOUT_MS)
            val path = player.currentMediaItem?.mediaId ?: return@launch
            if (player.playbackState != Player.STATE_BUFFERING || !player.playWhenReady) return@launch
            if (container.mediaCache.isComplete(path)) return@launch
            Log.i(TAG, "$path does not start, skipping to a downloaded song")
            nextIndex { container.mediaCache.isComplete(it) }?.let { player.seekTo(it, 0) }
        }
    }

    /** Next queue index (following shuffle order) whose song satisfies [accept]. */
    private fun nextIndex(accept: (String) -> Boolean): Int? {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return null
        val start = player.currentMediaItemIndex
        var index = start
        repeat(timeline.windowCount) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_ALL, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == start) return null
            if (accept(player.getMediaItemAt(index).mediaId)) return index
        }
        return null
    }

    // Prefetching

    private fun upcomingPaths(count: Int): List<String> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || count <= 0) return emptyList()
        val repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        val result = mutableListOf<String>()
        var index = player.currentMediaItemIndex
        while (result.size < count) {
            index = timeline.getNextWindowIndex(index, repeatMode, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) break
            result += player.getMediaItemAt(index).mediaId
        }
        return result
    }

    private fun schedulePrefetch() {
        val upcoming = upcomingPaths(container.settings.state.value.prefetchCount)
        val cache = container.mediaCache
        cache.protectedPaths = upcoming.toSet() + listOfNotNull(player.currentMediaItem?.mediaId)
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            delay(PREFETCH_DEBOUNCE_MS) // Wait until the user stops skipping.
            val rules = container.library.folderRules.value
            for (path in upcoming) {
                if (!container.connection.isOnline) break
                if (cache.isComplete(path) || rules.isExcluded(path)) continue
                val size = container.library.track(path)?.sizeBytes ?: continue
                val fits = withContext(Dispatchers.IO) { cache.makeRoomFor(path, size) }
                if (fits) cache.download(path)
            }
        }
    }

    // Library updates from playback

    private fun readTags(tracks: Tracks) {
        val path = player.currentMediaItem?.mediaId ?: return
        if (path in taggedPaths || container.library.trackIndex.value[path]?.tagsFromFile == true) return
        val builder = MediaMetadata.Builder()
        var found = false
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    metadata.get(j).populateMediaMetadata(builder)
                    found = true
                }
            }
        }
        if (!found) return
        val tags = builder.build()
        val title = tags.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val artist = tags.artist?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val album = tags.albumTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        taggedPaths += path
        scope.launch { container.library.setTags(path, title, artist, album) }

        // Show the real tags on the lock screen right away. Same URI, so playback continues.
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val updated = item.mediaMetadata.buildUpon().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
        player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(updated).build())
    }

    private fun storeDuration() {
        val path = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration
        if (duration <= 0 || duration == C.TIME_UNSET) return
        if (container.library.trackIndex.value[path]?.durationMs == duration) return
        scope.launch { container.library.setDuration(path, duration) }
    }

    /** Counts plays (after 30 s or half the song) and periodically saves the queue position. */
    private fun startTicker() {
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(TICK_MS)
                if (!player.isPlaying) continue
                val path = player.currentMediaItem?.mediaId
                if (path != null && path != countedPath) {
                    val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    if (player.currentPosition >= minOf(PLAY_COUNT_THRESHOLD_MS, duration / 2)) {
                        countedPath = path
                        container.library.markPlayed(path)
                    }
                }
                if (++ticks % 6 == 0) saveQueueSoon()
            }
        }
    }

    // Queue persistence

    private fun currentQueue(): SavedQueue? {
        val count = player.mediaItemCount
        if (count == 0) return null
        return SavedQueue(
            paths = List(count) { player.getMediaItemAt(it).mediaId },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    private fun saveQueueSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1_000)
            val queue = currentQueue() ?: return@launch
            withContext(Dispatchers.IO) { container.queueStore.save(queue) }
        }
    }

    private fun saveQueueNow() {
        saveJob?.cancel()
        currentQueue()?.let { runCatching { container.queueStore.save(it) } }
    }

    private class RestoredQueue(val items: List<MediaItem>, val index: Int, val positionMs: Long, val saved: SavedQueue)

    private suspend fun loadSavedQueue(): RestoredQueue? {
        val saved = withContext(Dispatchers.IO) { container.queueStore.load() } ?: return null
        val tracks = container.library.tracksByPaths(saved.paths)
        if (tracks.isEmpty()) return null
        val currentPath = saved.paths.getOrNull(saved.index)
        val index = tracks.indexOfFirst { it.path == currentPath }
        return RestoredQueue(
            items = tracks.map(MediaItems::from),
            index = index.coerceAtLeast(0),
            positionMs = if (index >= 0) saved.positionMs else 0,
            saved = saved,
        )
    }

    /** Loads the last queue (paused) so the lock screen and the app show it immediately. */
    private fun restoreQueue() {
        scope.launch {
            val restored = loadSavedQueue() ?: return@launch
            if (player.mediaItemCount > 0) return@launch // The user already started something.
            player.shuffleModeEnabled = restored.saved.shuffle
            player.repeatMode = restored.saved.repeatMode
            player.setMediaItems(restored.items, restored.index, restored.positionMs)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val CMD_SHUFFLE_ON = "jaymusic.SHUFFLE_ON"
        const val CMD_SHUFFLE_OFF = "jaymusic.SHUFFLE_OFF"
        const val CMD_CYCLE_REPEAT = "jaymusic.CYCLE_REPEAT"
        const val CMD_MAKE_NEXT = "jaymusic.MAKE_NEXT"
        const val EXTRA_INDEX = "index"

        private const val STALL_TIMEOUT_MS = 8_000L
        private const val PREFETCH_DEBOUNCE_MS = 700L
        private const val TICK_MS = 5_000L
        private const val PLAY_COUNT_THRESHOLD_MS = 30_000L
        private const val MAX_CONSECUTIVE_ERRORS = 10
        private const val MAX_HISTORY = 500
        private const val RESTART_THRESHOLD_MS = 3_000L
    }
}
