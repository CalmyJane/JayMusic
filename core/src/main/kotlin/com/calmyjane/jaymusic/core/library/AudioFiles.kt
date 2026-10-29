package com.calmyjane.jaymusic.core.library

/** Recognises playable audio files and derives display metadata from their paths. */
object AudioFiles {

    /** Formats ExoPlayer can decode on all supported Android versions. */
    val EXTENSIONS = setOf("mp3", "flac", "m4a", "mp4", "aac", "ogg", "oga", "opus", "wav", "mka", "amr")

    fun isAudio(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /**
     * Folders that never contain music: Synology thumbnails/indexes (`@eaDir`), the recycle bin
     * and hidden folders.
     */
    fun isIgnoredFolder(name: String): Boolean =
        name.startsWith("@") || name.startsWith(".") || name.equals("#recycle", ignoreCase = true) ||
            name.equals("#snapshot", ignoreCase = true)

    data class GuessedTags(val title: String, val artist: String?, val album: String?)

    private val TRACK_NUMBER = Regex("""^\s*(?:\d{1,2}[-.]\s*)?\d{1,3}\s*(?:[-._)]\s*|\s+)""")

    /**
     * Best-effort metadata from the file path, used until the real tags have been read.
     *
     * Understands the common `Artist/Album/01 - Title.mp3` and `Artist - Title.mp3` layouts.
     */
    fun guessTags(path: String): GuessedTags {
        val segments = path.trim('/').split('/')
        val fileName = segments.last()
        var base = fileName.substringBeforeLast('.', fileName)
        val stripped = base.replaceFirst(TRACK_NUMBER, "")
        if (stripped.isNotBlank()) base = stripped
        // Folder segments below the share itself (segments[0] is the shared folder).
        val folders = segments.drop(1).dropLast(1)
        val album = folders.lastOrNull()
        val folderArtist = if (folders.size >= 2) folders[folders.size - 2] else null

        val dash = base.indexOf(" - ")
        return if (dash > 0) {
            GuessedTags(
                title = base.substring(dash + 3).trim(),
                artist = base.substring(0, dash).trim(),
                album = album,
            )
        } else {
            GuessedTags(title = base.trim(), artist = folderArtist, album = album)
        }
    }

    fun parentFolder(path: String): String = path.substringBeforeLast('/', "").ifEmpty { "/" }
}
