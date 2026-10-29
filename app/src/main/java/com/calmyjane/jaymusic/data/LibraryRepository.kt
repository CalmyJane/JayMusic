package com.calmyjane.jaymusic.data

import androidx.room.withTransaction
import com.calmyjane.jaymusic.core.cache.LibrarySong
import com.calmyjane.jaymusic.core.library.AudioFiles
import com.calmyjane.jaymusic.core.library.FolderRules
import com.calmyjane.jaymusic.core.synology.RemoteEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Result of applying a scan; songs whose file changed or disappeared must be removed from the cache. */
data class LibraryChanges(val added: Int, val removed: List<String>, val changed: List<String>)

class LibraryRepository(private val db: AppDatabase, scope: CoroutineScope) {

    private val tracks = db.tracks()
    private val playlists = db.playlists()
    private val excluded = db.excludedFolders()

    /** All songs, sorted by title. Shared so every screen and the cache use one query. */
    val allTracks: StateFlow<List<TrackEntity>> =
        tracks.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** path -> track, for fast lookups from the player and the cache. */
    val trackIndex: StateFlow<Map<String, TrackEntity>> =
        allTracks.map { list -> list.associateBy { it.path } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val folderRules: StateFlow<FolderRules> =
        excluded.observe().map { FolderRules(it) }.stateIn(scope, SharingStarted.Eagerly, FolderRules.NONE)

    val playlistSummaries: Flow<List<PlaylistSummary>> = playlists.observeSummaries()

    fun playlist(id: Long): Flow<PlaylistEntity?> = playlists.observe(id)
    fun playlistTracks(id: Long): Flow<List<PlaylistTrack>> = playlists.observeTracks(id)

    suspend fun tracksByPaths(paths: List<String>): List<TrackEntity> {
        val byPath = paths.chunked(500).flatMap { tracks.getAll(it) }.associateBy { it.path }
        return paths.mapNotNull(byPath::get)
    }

    suspend fun track(path: String): TrackEntity? = trackIndex.value[path] ?: tracks.get(path)

    fun librarySongs(): List<LibrarySong> = allTracks.value.map {
        LibrarySong(it.path, it.sizeBytes, it.rating, it.playCount, it.lastPlayedAt)
    }

    suspend fun setRating(path: String, rating: Int) = tracks.setRating(path, rating.coerceIn(0, 5))
    suspend fun markPlayed(path: String) = tracks.markPlayed(path, System.currentTimeMillis())
    suspend fun setDuration(path: String, durationMs: Long) = tracks.setDuration(path, durationMs)
    suspend fun setTags(path: String, title: String, artist: String?, album: String?) =
        tracks.setTags(path, title, artist, album)

    /**
     * Replaces the library with the result of a full scan. Ratings, play counts and tags of
     * unchanged songs are kept.
     */
    suspend fun applyScan(remote: List<RemoteEntry>): LibraryChanges = db.withTransaction {
        val existing = tracks.all().associateBy { it.path }
        val now = System.currentTimeMillis()
        val inserts = mutableListOf<TrackEntity>()
        val updates = mutableListOf<TrackEntity>()
        val changed = mutableListOf<String>()
        for (entry in remote) {
            val old = existing[entry.path]
            if (old == null) {
                val tags = AudioFiles.guessTags(entry.path)
                inserts += TrackEntity(
                    path = entry.path,
                    folder = AudioFiles.parentFolder(entry.path),
                    title = tags.title,
                    artist = tags.artist,
                    album = tags.album,
                    sizeBytes = entry.size,
                    modified = entry.modified,
                    addedAt = now,
                )
            } else if (old.sizeBytes != entry.size || old.modified != entry.modified) {
                changed += entry.path
                // Re-read tags from the new file; keep rating and play statistics.
                updates += old.copy(sizeBytes = entry.size, modified = entry.modified, tagsFromFile = false, durationMs = 0)
            }
        }
        val remotePaths = remote.mapTo(HashSet()) { it.path }
        val removed = existing.keys.filter { it !in remotePaths }
        if (inserts.isNotEmpty()) inserts.chunked(500).forEach { tracks.insertAll(it) }
        if (updates.isNotEmpty()) tracks.updateAll(updates)
        removed.chunked(500).forEach { tracks.deleteAll(it) }
        LibraryChanges(added = inserts.size, removed = removed, changed = changed)
    }

    // Playlists

    suspend fun createPlaylist(name: String): Long =
        playlists.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renamePlaylist(id: Long, name: String) = playlists.rename(id, name.trim())
    suspend fun deletePlaylist(id: Long) = playlists.delete(id)
    suspend fun addToPlaylist(playlistId: Long, paths: List<String>) = playlists.append(playlistId, paths)
    suspend fun removeFromPlaylist(entryId: Long) = playlists.deleteEntry(entryId)
    suspend fun movePlaylistEntry(playlistId: Long, entryId: Long, delta: Int) =
        playlists.move(playlistId, entryId, delta)

    // Download exclusions

    suspend fun setFolderExcluded(path: String, isExcluded: Boolean) {
        val normalized = FolderRules.normalize(path)
        if (isExcluded) excluded.insert(ExcludedFolderEntity(normalized)) else excluded.delete(normalized)
    }
}
