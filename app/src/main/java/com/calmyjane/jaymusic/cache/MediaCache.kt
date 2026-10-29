package com.calmyjane.jaymusic.cache

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.calmyjane.jaymusic.core.cache.CachedSong
import com.calmyjane.jaymusic.core.cache.SongStore
import com.calmyjane.jaymusic.core.cache.StoragePolicy
import com.calmyjane.jaymusic.core.library.FolderRules
import com.calmyjane.jaymusic.data.LibraryRepository
import com.calmyjane.jaymusic.data.SettingsRepository
import com.calmyjane.jaymusic.remote.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** How much of a song is stored on the phone. */
data class CacheInfo(val bytes: Long, val complete: Boolean)

/**
 * The on-device song store, built on Media3's [SimpleCache].
 *
 * Every song that is played or prefetched is written into this cache, keyed by its NAS path. The
 * cache never deletes anything by itself; [StoragePolicy] decides what to delete when the user's
 * storage limit is reached (worst rated first).
 */
@OptIn(UnstableApi::class)
class MediaCache(
    context: Context,
    http: OkHttpClient,
    private val connection: ServerConnection,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : SongStore {

    private val dir = File(context.filesDir, "media")
    private val cache = SimpleCache(dir, NoOpCacheEvictor(), StandaloneDatabaseProvider(context))

    private val httpFactory = OkHttpDataSource.Factory(http).setUserAgent("JayMusic")
    private val upstreamFactory = SynologyDataSource.Factory(connection, httpFactory)

    private val cacheKeyFactory = CacheKeyFactory { spec ->
        spec.key ?: TrackUri.pathOf(spec.uri) ?: spec.uri.toString()
    }

    private val cachingFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setCacheKeyFactory(cacheKeyFactory)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Data source for the player: cached songs come from disk, others are streamed and stored. */
    val playbackDataSourceFactory: DataSource.Factory = DataSource.Factory { RoutingDataSource() }

    private val _entries = MutableStateFlow<Map<String, CacheInfo>>(emptyMap())

    /** What is stored on the phone, by song path. */
    val entries: StateFlow<Map<String, CacheInfo>> = _entries.asStateFlow()

    /** Songs playing now or queued next; set by the playback service. Never deleted. */
    @Volatile
    var protectedPaths: Set<String> = emptySet()

    private val inProgress = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch(Dispatchers.IO) {
            reloadAll()
            // Completeness of entries without stored length is judged by the library's file
            // size, which is only known once the database has loaded.
            library.trackIndex.first { it.isNotEmpty() }
            reloadAll()
        }
        // Excluding a folder deletes its downloaded songs.
        scope.launch(Dispatchers.IO) {
            library.folderRules.collect { rules -> removeExcluded(rules) }
        }
        // Lowering the storage limit frees space right away.
        scope.launch(Dispatchers.IO) {
            settings.flow.map { it.storageLimitMb }.distinctUntilChanged().collect { enforceBudget() }
        }
    }

    fun isComplete(path: String): Boolean = _entries.value[path]?.complete == true

    fun usedBytes(): Long = cache.cacheSpace

    // SongStore

    override fun cachedSongs(): List<CachedSong> {
        val index = library.trackIndex.value
        return _entries.value.map { (path, info) ->
            val track = index[path]
            CachedSong(
                id = path,
                bytes = info.bytes,
                rating = track?.rating ?: 0,
                lastPlayedAt = track?.lastPlayedAt ?: 0,
                complete = info.complete,
            )
        }
    }

    override fun budgetBytes(): Long {
        val limit = settings.state.value.storageLimitBytes
        // Never fill the phone completely, whatever the configured limit.
        val physical = cache.cacheSpace + dir.usableSpace - MIN_FREE_BYTES
        return minOf(limit, physical).coerceAtLeast(0)
    }

    override fun protectedIds(): Set<String> = protectedPaths + inProgress

    override fun delete(id: String) {
        if (id in protectedIds()) return
        try {
            cache.removeResource(id)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete $id", e)
        }
        refresh(id)
    }

    /** Downloads [id] completely. Cancelling the coroutine stops the download. */
    override suspend fun download(id: String): Boolean {
        if (isComplete(id)) return true
        if (library.folderRules.value.isExcluded(id)) return false
        if (!inProgress.add(id)) return false
        try {
            val spec = DataSpec.Builder().setUri(TrackUri.of(id)).setKey(id).build()
            val writer = CacheWriter(cachingFactory.createDataSource(), spec, null, null)
            coroutineScope {
                val canceller = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        writer.cancel()
                    }
                }
                try {
                    runInterruptible(Dispatchers.IO) { writer.cache() }
                } finally {
                    canceller.cancel()
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "Download of $id failed: $e")
        } finally {
            inProgress.remove(id)
            withContext(Dispatchers.IO) { refresh(id) }
        }
        return isComplete(id)
    }

    /**
     * Makes room for a song the user is about to hear by deleting the worst rated other songs.
     * Returns false if it cannot fit.
     */
    fun makeRoomFor(path: String, sizeBytes: Long): Boolean {
        val others = cachedSongs().filter { it.id != path }
        val plan = StoragePolicy.planEviction(others, budgetBytes(), sizeBytes, protectedIds())
        if (plan.fits) plan.evict.forEach(::delete)
        return plan.fits
    }

    /** Deletes the worst rated songs until the cache fits into the budget again. */
    fun enforceBudget() {
        StoragePolicy.shrinkTo(cachedSongs(), budgetBytes(), protectedIds()).forEach(::delete)
    }

    fun removePaths(paths: Collection<String>) = paths.forEach(::delete)

    fun clearAll() = _entries.value.keys.toList().forEach(::delete)

    private fun removeExcluded(rules: FolderRules) {
        _entries.value.keys.filter(rules::isExcluded).forEach(::delete)
    }

    /** Re-reads the state of one song from the cache. Call after playback or downloads. */
    fun refresh(path: String) {
        val info = compute(path)
        _entries.update { current ->
            if (info == null) current - path else current + (path to info)
        }
    }

    private fun reloadAll() {
        _entries.value = cache.keys.mapNotNull { key -> compute(key)?.let { key to it } }.toMap()
    }

    private fun compute(path: String): CacheInfo? {
        val bytes = cache.getCachedSpans(path).sumOf { it.length }
        if (bytes <= 0) return null
        val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(path))
        val expected = if (contentLength > 0) contentLength else library.trackIndex.value[path]?.sizeBytes ?: 0
        val complete = expected > 0 && cache.isCached(path, 0, expected)
        return CacheInfo(bytes, complete)
    }

    /** Plays excluded folders straight from the NAS, everything else through the cache. */
    private inner class RoutingDataSource : DataSource {
        private val cached = cachingFactory.createDataSource()
        private val direct = upstreamFactory.createDataSource()
        private var current: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            direct.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val path = TrackUri.pathOf(dataSpec.uri)
            val target = if (path != null && library.folderRules.value.isExcluded(path)) direct else cached
            current = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            current?.read(buffer, offset, length) ?: throw IOException("Not opened")

        override fun getUri(): Uri? = current?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                current?.close()
            } finally {
                current = null
            }
        }
    }

    private companion object {
        const val TAG = "MediaCache"
        const val MIN_FREE_BYTES = 500L * 1024 * 1024
    }
}
