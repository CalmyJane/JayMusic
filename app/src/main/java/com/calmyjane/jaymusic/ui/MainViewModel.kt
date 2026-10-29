package com.calmyjane.jaymusic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calmyjane.jaymusic.appContainer
import com.calmyjane.jaymusic.cache.CacheInfo
import com.calmyjane.jaymusic.core.library.AudioFiles
import com.calmyjane.jaymusic.core.library.FolderRules
import com.calmyjane.jaymusic.data.AppSettings
import com.calmyjane.jaymusic.data.PlaylistEntity
import com.calmyjane.jaymusic.data.PlaylistSummary
import com.calmyjane.jaymusic.data.PlaylistTrack
import com.calmyjane.jaymusic.data.ThemeMode
import com.calmyjane.jaymusic.data.TrackEntity
import com.calmyjane.jaymusic.playback.PlayResult
import com.calmyjane.jaymusic.playback.PlayerConnection
import com.calmyjane.jaymusic.remote.ServerConnection
import com.calmyjane.jaymusic.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SongSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    RATING("Rating"),
    RECENT("Recently added"),
    MOST_PLAYED("Most played"),
}

data class SongFilter(
    val query: String = "",
    val sort: SongSort = SongSort.TITLE,
    val downloadedOnly: Boolean = false,
)

/** One row of the folder browser / exclusion list. */
data class FolderNode(val path: String, val name: String, val depth: Int, val songCount: Int, val hasChildren: Boolean)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val library = container.library

    val player = PlayerConnection(application, container.mediaCache, container.connection, viewModelScope)

    val settings: StateFlow<AppSettings> = container.settings.state
    val connectionState: StateFlow<ServerConnection.State> = container.connection.state
    val syncStatus: StateFlow<SyncStatus> = container.sync.status
    val cacheEntries: StateFlow<Map<String, CacheInfo>> = container.mediaCache.entries
    val folderRules: StateFlow<FolderRules> = library.folderRules
    val allTracks: StateFlow<List<TrackEntity>> = library.allTracks
    val trackIndex: StateFlow<Map<String, TrackEntity>> = library.trackIndex

    val playlists: StateFlow<List<PlaylistSummary>> =
        library.playlistSummaries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short notices shown as snackbars. */
    val messages: SharedFlow<String> = _messages

    val songFilter = MutableStateFlow(SongFilter())

    /** Songs list after search, sort and filter; computed off the main thread. */
    val songs: StateFlow<List<TrackEntity>> =
        combine(library.allTracks, songFilter, container.mediaCache.entries) { tracks, filter, cache ->
            filterSongs(tracks, filter, cache)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Total size of the songs on the phone. */
    val usedBytes: StateFlow<Long> = container.mediaCache.entries
        .map { entries -> entries.values.sumOf { it.bytes } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        player.connect()
    }

    override fun onCleared() {
        player.release()
    }

    private fun filterSongs(tracks: List<TrackEntity>, filter: SongFilter, cache: Map<String, CacheInfo>): List<TrackEntity> {
        val terms = filter.query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val filtered = tracks.filter { t ->
            (!filter.downloadedOnly || cache[t.path]?.complete == true) &&
                terms.all { term ->
                    t.title.lowercase().contains(term) ||
                        t.artist?.lowercase()?.contains(term) == true ||
                        t.album?.lowercase()?.contains(term) == true
                }
        }
        return when (filter.sort) {
            SongSort.TITLE -> filtered // already sorted by the database
            SongSort.ARTIST -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { "${it.artist.orEmpty()}\u0000${it.album.orEmpty()}\u0000${it.path}" })
            SongSort.RATING -> filtered.sortedByDescending { it.rating }
            SongSort.RECENT -> filtered.sortedByDescending { it.addedAt }
            SongSort.MOST_PLAYED -> filtered.sortedByDescending { it.playCount }
        }
    }

    // Folders

    /** Direct sub folders and songs of [folder] for the folder browser. */
    fun folderContent(folder: String?): Flow<Pair<List<FolderNode>, List<TrackEntity>>> =
        library.allTracks.map { tracks -> computeFolderContent(tracks, folder) }.flowOn(Dispatchers.Default)

    private fun computeFolderContent(tracks: List<TrackEntity>, folder: String?): Pair<List<FolderNode>, List<TrackEntity>> {
        val roots = settings.value.musicFolders.map(FolderRules::normalize)
        if (folder == null) {
            val nodes = roots.map { root ->
                val count = tracks.count { it.path.startsWith("$root/") }
                FolderNode(root, root, 0, count, hasChildren = true)
            }
            return nodes to emptyList()
        }
        val prefix = "$folder/"
        val children = HashMap<String, Int>()
        val songs = mutableListOf<TrackEntity>()
        for (t in tracks) {
            if (!t.path.startsWith(prefix)) continue
            val rest = t.path.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash < 0) songs += t else children.merge(rest.substring(0, slash), 1, Int::plus)
        }
        val nodes = children.entries
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.key })
            .map { (name, count) -> FolderNode("$prefix$name", name, 0, count, hasChildren = true) }
        return nodes to songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
    }

    /** All folders as an indented tree, for choosing download exclusions. */
    val folderTree: StateFlow<List<FolderNode>> = library.allTracks.map { tracks ->
        val counts = HashMap<String, Int>()
        for (t in tracks) {
            var folder = AudioFiles.parentFolder(t.path)
            while (folder.length > 1) {
                counts.merge(folder, 1, Int::plus)
                folder = AudioFiles.parentFolder(folder)
            }
        }
        val paths = counts.keys.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
        paths.map { path ->
            FolderNode(
                path = path,
                name = path.substringAfterLast('/'),
                depth = path.count { it == '/' } - 1,
                songCount = counts.getValue(path),
                hasChildren = paths.any { it.startsWith("$path/") },
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setFolderExcluded(path: String, excluded: Boolean) = viewModelScope.launch {
        library.setFolderExcluded(path, excluded)
    }

    // Playback

    fun play(tracks: List<TrackEntity>, startIndex: Int) = report(player.play(tracks, startIndex, shuffle = false))

    fun shuffle(tracks: List<TrackEntity>) = report(player.play(tracks, startIndex = -1, shuffle = true))

    fun playNext(track: TrackEntity) {
        player.playNext(track)
        _messages.tryEmit("Plays next: ${track.title}")
    }

    fun addToQueue(track: TrackEntity) {
        player.addToQueue(track)
        _messages.tryEmit("Added to queue: ${track.title}")
    }

    private fun report(result: PlayResult) {
        when (result) {
            PlayResult.Started -> Unit
            is PlayResult.StartedOffline ->
                _messages.tryEmit("Offline: playing downloaded songs only (${result.skipped} not on phone)")
            PlayResult.NothingAvailable -> _messages.tryEmit("Offline and none of these songs are downloaded")
        }
    }

    fun setRating(path: String, rating: Int) = viewModelScope.launch { library.setRating(path, rating) }

    // Playlists

    fun playlist(id: Long): Flow<PlaylistEntity?> = library.playlist(id)
    fun playlistTracks(id: Long): Flow<List<PlaylistTrack>> = library.playlistTracks(id)

    fun createPlaylist(name: String, initialTrack: String? = null) = viewModelScope.launch {
        val id = library.createPlaylist(name)
        if (initialTrack != null) {
            library.addToPlaylist(id, listOf(initialTrack))
            _messages.tryEmit("Added to $name")
        }
    }

    fun addToPlaylist(playlist: PlaylistSummary, path: String) = viewModelScope.launch {
        library.addToPlaylist(playlist.playlist.id, listOf(path))
        _messages.tryEmit("Added to ${playlist.playlist.name}")
    }

    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { library.renamePlaylist(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { library.deletePlaylist(id) }
    fun removeFromPlaylist(entryId: Long) = viewModelScope.launch { library.removeFromPlaylist(entryId) }
    fun movePlaylistEntry(playlistId: Long, entryId: Long, delta: Int) =
        viewModelScope.launch { library.movePlaylistEntry(playlistId, entryId, delta) }

    // Settings

    fun saveServer(localUrl: String, remoteUrl: String, username: String, password: String, folders: List<String>) =
        viewModelScope.launch {
            if (password.isNotEmpty()) withContext(Dispatchers.IO) { container.secrets.password = password }
            container.settings.updateServer(localUrl, remoteUrl, username, folders)
            container.connection.connect(force = true)
            _messages.tryEmit("Saved. Connecting…")
        }

    fun hasPassword(): Boolean = container.secrets.password != null

    fun submitOtp(code: String) = container.connection.connectWithOtp(code)
    fun reconnect() = container.connection.connect(force = true)
    fun syncNow() = container.sync.syncNow(userInitiated = true)

    fun setStorageLimitMb(value: Long) = viewModelScope.launch { container.settings.setStorageLimitMb(value) }
    fun setPrefetchCount(value: Int) = viewModelScope.launch { container.settings.setPrefetchCount(value) }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { container.settings.setTheme(value) }

    fun setSyncOnWifiOnly(value: Boolean) = viewModelScope.launch {
        container.settings.setSyncOnWifiOnly(value)
        container.sync.schedulePeriodic(value)
    }

    fun clearDownloads() = viewModelScope.launch(Dispatchers.IO) {
        container.mediaCache.clearAll()
        _messages.tryEmit("Downloaded songs deleted")
    }

    fun freeBytesOnDevice(): Long = getApplication<Application>().filesDir.usableSpace
}
