package com.calmyjane.jaymusic.cache

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.calmyjane.jaymusic.remote.ServerConnection
import java.io.IOException

/** Thrown when a song that is not on the phone is requested while the NAS is unreachable. */
class ServerUnavailableException(path: String) : IOException("Not connected to the server, cannot load $path")

/**
 * Resolves [TrackUri]s to File Station download URLs of the current session.
 *
 * DSM answers a request with an expired session with HTTP 200 and a JSON error. That body must
 * never end up in the cache as "audio", so such answers are turned into an error and the session
 * is renewed.
 */
@OptIn(UnstableApi::class)
class SynologyDataSource(
    private val connection: ServerConnection,
    private val httpFactory: HttpDataSource.Factory,
) : DataSource {

    class Factory(
        private val connection: ServerConnection,
        private val httpFactory: HttpDataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SynologyDataSource(connection, httpFactory)
    }

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: HttpDataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val path = TrackUri.pathOf(dataSpec.uri) ?: throw IOException("Unsupported URI ${dataSpec.uri}")
        val session = connection.currentSession() ?: throw ServerUnavailableException(path)
        val url = connection.client().downloadUrl(session, path)
        val http = httpFactory.createDataSource().also { ds -> listeners.forEach(ds::addTransferListener) }
        delegate = http
        val length = http.open(dataSpec.buildUpon().setUri(Uri.parse(url.toString())).build())
        val contentType = http.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.firstOrNull().orEmpty()
        if (contentType.startsWith("application/json") || contentType.startsWith("text/")) {
            http.close()
            delegate = null
            connection.invalidate(session)
            throw IOException("Server rejected the request for $path (session expired?)")
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: throw IOException("Not opened")

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            delegate?.close()
        } finally {
            delegate = null
        }
    }
}
