package com.calmyjane.jaymusic.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The play queue as it was when the app was last used, so playback can resume after a restart. */
data class SavedQueue(
    val paths: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    /** The list's original order, restored when shuffle is turned off. */
    val linear: List<String> = emptyList(),
)

class QueueStore(context: Context) {

    private val file = File(context.filesDir, "queue.json")

    fun save(queue: SavedQueue) {
        val json = JSONObject()
            .put("paths", JSONArray(queue.paths))
            .put("index", queue.index)
            .put("position", queue.positionMs)
            .put("shuffle", queue.shuffle)
            .put("repeat", queue.repeatMode)
            .put("linear", JSONArray(queue.linear))
        // Write to a temp file first so a crash never leaves a half written queue.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    fun load(): SavedQueue? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("paths")
        SavedQueue(
            paths = List(array.length()) { array.getString(it) },
            index = json.optInt("index"),
            positionMs = json.optLong("position"),
            shuffle = json.optBoolean("shuffle"),
            repeatMode = json.optInt("repeat"),
            linear = json.optJSONArray("linear")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        )
    }.getOrNull()
}
