package com.calmyjane.jaymusic.core.library

/**
 * Set of folders whose songs must never be downloaded to the phone.
 *
 * A folder excludes everything below it. Paths are compared case-sensitively, like DSM does.
 */
class FolderRules(excluded: Collection<String>) {

    private val folders: List<String> = excluded.map(::normalize).distinct().sorted()

    val excludedFolders: List<String> get() = folders

    fun isExcluded(path: String): Boolean {
        val p = normalize(path)
        return folders.any { f -> f == "/" || p == f || p.startsWith("$f/") }
    }

    /** The excluded folder that causes [path] to be excluded, or null. */
    fun excludedBy(path: String): String? {
        val p = normalize(path)
        return folders.firstOrNull { f -> f == "/" || p == f || p.startsWith("$f/") }
    }

    companion object {
        val NONE = FolderRules(emptyList())

        fun normalize(path: String): String {
            val trimmed = path.trim().trimEnd('/')
            if (trimmed.isEmpty()) return "/"
            return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        }
    }
}
