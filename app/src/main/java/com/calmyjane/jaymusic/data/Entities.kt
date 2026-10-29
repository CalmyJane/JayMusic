package com.calmyjane.jaymusic.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A song on the NAS. Its id is the full File Station path, e.g. `/music/Artist/Album/01.flac`. */
@Entity(tableName = "tracks", indices = [Index("folder")])
data class TrackEntity(
    @PrimaryKey val path: String,
    val folder: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val sizeBytes: Long,
    /** Modification time on the NAS (epoch seconds); a change invalidates the downloaded copy. */
    val modified: Long,
    val durationMs: Long = 0,
    /** 0 = unrated, 1..5 stars. */
    val rating: Int = 0,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
    val addedAt: Long,
    /** True once title/artist/album were read from the file's own tags. */
    val tagsFromFile: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    indices = [Index("playlistId"), Index("trackPath")],
)
data class PlaylistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackPath: String,
    val position: Int,
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val path: String,
)

data class PlaylistSummary(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "trackCount") val trackCount: Int,
)

data class PlaylistTrack(
    @ColumnInfo(name = "entryId") val entryId: Long,
    @Embedded val track: TrackEntity,
)
