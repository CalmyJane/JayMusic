package com.calmyjane.jaymusic.core.library

import com.calmyjane.jaymusic.core.synology.RemoteEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryScannerTest {

    private val tree = mapOf(
        "/music" to listOf(
            RemoteEntry("/music/Rock", "Rock", isDir = true),
            RemoteEntry("/music/@eaDir", "@eaDir", isDir = true),
            RemoteEntry("/music/a.mp3", "a.mp3", isDir = false, size = 5),
            RemoteEntry("/music/cover.jpg", "cover.jpg", isDir = false),
        ),
        "/music/Rock" to listOf(
            RemoteEntry("/music/Rock/b.flac", "b.flac", isDir = false, size = 7),
        ),
        "/music/@eaDir" to listOf(
            RemoteEntry("/music/@eaDir/thumb.mp3", "thumb.mp3", isDir = false),
        ),
    )

    @Test
    fun `collects audio files recursively and skips system folders`() = runTest {
        val listed = mutableListOf<String>()
        val scanner = LibraryScanner({ folder -> listed += folder; tree[folder].orEmpty() })
        val result = scanner.scan(listOf("/music/"))
        assertEquals(setOf("/music/a.mp3", "/music/Rock/b.flac"), result.tracks.map { it.path }.toSet())
        assertEquals(setOf("/music", "/music/Rock"), result.folders.toSet())
        assertEquals(setOf("/music", "/music/Rock"), listed.toSet())
    }
}
