package com.calmyjane.jaymusic

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.DateFormat
import java.util.Date

/**
 * Keeps the last crash and recent non-fatal errors in a file so they can be shown in Settings and
 * copied into a bug report. There is no remote crash reporting.
 */
object CrashLog {

    private const val TAG = "CrashLog"
    private const val MAX_BYTES = 64 * 1024
    private lateinit var file: File

    fun install(context: Context) {
        file = File(context.filesDir, "errors.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write("CRASH in ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Records an error that was caught and handled. */
    fun nonFatal(what: String, error: Throwable) {
        Log.w(TAG, what, error)
        write("ERROR: $what", error)
    }

    fun read(): String = runCatching { if (::file.isInitialized && file.exists()) file.readText() else "" }.getOrDefault("")

    fun clear() {
        if (::file.isInitialized) file.delete()
    }

    @Synchronized
    private fun write(title: String, error: Throwable) {
        if (!::file.isInitialized) return
        runCatching {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            val entry = buildString {
                append("=== ").append(DateFormat.getDateTimeInstance().format(Date())).append(" · ")
                append(title).append('\n')
                append("JayMusic ").append(BuildConfig.VERSION_NAME)
                append(" · Android ").append(Build.VERSION.RELEASE).append(" · ").append(Build.MODEL).append('\n')
                append(trace).append('\n')
            }
            // Newest first, bounded size.
            val text = (entry + read()).take(MAX_BYTES)
            file.writeText(text)
        }
    }
}
