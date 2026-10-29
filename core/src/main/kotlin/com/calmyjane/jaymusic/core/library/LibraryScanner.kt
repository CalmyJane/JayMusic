package com.calmyjane.jaymusic.core.library

import com.calmyjane.jaymusic.core.synology.RemoteEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Anything that can list a remote folder. Implemented on top of [com.calmyjane.jaymusic.core.synology.SynologyClient]. */
fun interface RemoteBrowser {
    suspend fun list(folder: String): List<RemoteEntry>
}

data class ScanResult(
    val tracks: List<RemoteEntry>,
    val folders: List<String>,
)

/**
 * Walks the music folders breadth-first and collects all audio files. Several folders are listed
 * in parallel, which makes scanning large libraries over the internet much faster.
 */
class LibraryScanner(
    private val browser: RemoteBrowser,
    private val parallelism: Int = 4,
    private val maxDepth: Int = 32,
) {

    suspend fun scan(
        roots: List<String>,
        onProgress: (folders: Int, tracks: Int) -> Unit = { _, _ -> },
    ): ScanResult = coroutineScope {
        val semaphore = Semaphore(parallelism)
        val tracks = LinkedHashMap<String, RemoteEntry>()
        val folders = LinkedHashSet<String>()
        var level = roots.map(FolderRules::normalize).distinct()
        var depth = 0
        while (level.isNotEmpty() && depth <= maxDepth) {
            val listings = level.filter { folders.add(it) }.map { folder ->
                async { semaphore.withPermit { browser.list(folder) } }
            }.awaitAll()
            val next = mutableListOf<String>()
            for (entries in listings) {
                for (entry in entries) {
                    if (entry.isDir) {
                        if (!AudioFiles.isIgnoredFolder(entry.name)) next += entry.path
                    } else if (AudioFiles.isAudio(entry.name)) {
                        tracks[entry.path] = entry
                    }
                }
            }
            onProgress(folders.size, tracks.size)
            level = next
            depth++
        }
        ScanResult(tracks.values.toList(), folders.toList())
    }
}
