package com.calmyjane.jaymusic.cache

import android.net.Uri

/**
 * Stable URI for a song: `jaymusic://track?p=/music/a.mp3`.
 *
 * Media items and cache entries use this instead of the real download URL, which contains the
 * session id and changes on every login.
 */
object TrackUri {
    private const val SCHEME = "jaymusic"
    private const val HOST = "track"
    private const val PARAM = "p"

    fun of(path: String): Uri = Uri.Builder().scheme(SCHEME).authority(HOST).appendQueryParameter(PARAM, path).build()

    fun pathOf(uri: Uri): String? =
        if (uri.scheme == SCHEME && uri.authority == HOST) uri.getQueryParameter(PARAM) else null
}
