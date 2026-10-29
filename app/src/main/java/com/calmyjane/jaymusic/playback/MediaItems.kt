package com.calmyjane.jaymusic.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.calmyjane.jaymusic.cache.TrackUri
import com.calmyjane.jaymusic.data.TrackEntity

object MediaItems {

    private const val EXTRA_RATING = "jaymusic.rating"

    fun from(track: TrackEntity): MediaItem = MediaItem.Builder()
        .setMediaId(track.path)
        .setUri(TrackUri.of(track.path))
        .setCustomCacheKey(track.path)
        .setMediaMetadata(metadata(track))
        .build()

    fun metadata(track: TrackEntity): MediaMetadata = MediaMetadata.Builder()
        .setTitle(track.title)
        .setArtist(track.artist)
        .setAlbumTitle(track.album)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(Bundle().apply { putInt(EXTRA_RATING, track.rating) })
        .build()

    /**
     * Media items coming from a controller lose their URI on the way to the session; this
     * restores it from the media id (the NAS path).
     */
    fun resolve(item: MediaItem): MediaItem = item.buildUpon()
        .setUri(TrackUri.of(item.mediaId))
        .setCustomCacheKey(item.mediaId)
        .build()
}
