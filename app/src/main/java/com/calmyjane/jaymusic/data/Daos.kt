package com.calmyjane.jaymusic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun all(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE path = :path")
    suspend fun get(path: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE path IN (:paths)")
    suspend fun getAll(paths: List<String>): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Update
    suspend fun updateAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    suspend fun deleteAll(paths: List<String>)

    @Query("UPDATE tracks SET rating = :rating WHERE path = :path")
    suspend fun setRating(path: String, rating: Int)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :now WHERE path = :path")
    suspend fun markPlayed(path: String, now: Long)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, tagsFromFile = 1 WHERE path = :path")
    suspend fun setTags(path: String, title: String, artist: String?, album: String?)

    @Query("UPDATE tracks SET durationMs = :durationMs WHERE path = :path")
    suspend fun setDuration(path: String, durationMs: Long)
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.*, (SELECT COUNT(*) FROM playlist_entries e
                     JOIN tracks t ON t.path = e.trackPath
                     WHERE e.playlistId = p.id) AS trackCount
        FROM playlists p ORDER BY p.name COLLATE NOCASE
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT e.id AS entryId, t.* FROM playlist_entries e
        JOIN tracks t ON t.path = e.trackPath
        WHERE e.playlistId = :playlistId ORDER BY e.position
        """,
    )
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrack>>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun deleteEntries(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("DELETE FROM playlist_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId ORDER BY position")
    suspend fun entries(playlistId: Long): List<PlaylistEntryEntity>

    @Update
    suspend fun updateEntries(entries: List<PlaylistEntryEntity>)

    @Transaction
    suspend fun delete(id: Long) {
        deleteEntries(id)
        deletePlaylist(id)
    }

    @Transaction
    suspend fun append(playlistId: Long, paths: List<String>) {
        val start = maxPosition(playlistId) + 1
        insertEntries(paths.mapIndexed { i, p -> PlaylistEntryEntity(playlistId = playlistId, trackPath = p, position = start + i) })
    }

    /** Moves the entry [entryId] one step up (-1) or down (+1). */
    @Transaction
    suspend fun move(playlistId: Long, entryId: Long, delta: Int) {
        val list = entries(playlistId).toMutableList()
        val from = list.indexOfFirst { it.id == entryId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        updateEntries(list.mapIndexed { i, e -> e.copy(position = i) })
    }
}

@Dao
interface ExcludedFolderDao {
    @Query("SELECT path FROM excluded_folders ORDER BY path")
    fun observe(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: ExcludedFolderEntity)

    @Query("DELETE FROM excluded_folders WHERE path = :path")
    suspend fun delete(path: String)
}
