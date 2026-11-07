package com.calmyjane.jaymusic.data

/**
 * Sort orders for the library and for the queue when shuffle is turned off. Persisted in the
 * settings so the playback service uses the same order the library shows.
 */
enum class SongSort(val label: String, val comparator: Comparator<TrackEntity>) {
    TITLE(
        "Title A–Z",
        compareBy<TrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.path },
    ),
    FOLDER(
        "Folders (albums together)",
        compareBy<TrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.path },
    ),
    ARTIST(
        "Artist",
        compareBy<TrackEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.orEmpty() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album.orEmpty() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.path },
    ),
    RATING(
        "Best rated first",
        compareByDescending<TrackEntity> { it.rating }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    ),
    NEWEST(
        "Newest first",
        compareByDescending<TrackEntity> { it.modified }.thenBy { it.path },
    ),
    OLDEST(
        "Oldest first",
        compareBy<TrackEntity> { it.modified }.thenBy { it.path },
    ),
    MOST_PLAYED(
        "Most played",
        compareByDescending<TrackEntity> { it.playCount }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    ),
}
