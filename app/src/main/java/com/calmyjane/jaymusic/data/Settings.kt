package com.calmyjane.jaymusic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    /** Address used at home, e.g. `http://192.168.1.10:5000`. Tried first. */
    val localUrl: String = "",
    /** Address used from the internet, e.g. `https://nas.example.com:5001`. */
    val remoteUrl: String = "",
    val username: String = "",
    /** Folders on the NAS that contain music, e.g. `/music`. */
    val musicFolders: List<String> = listOf("/music"),
    val storageLimitMb: Long = 8 * 1024,
    /** Background sync only on unmetered (Wi‑Fi) networks. */
    val syncOnWifiOnly: Boolean = true,
    /** Number of upcoming songs downloaded ahead of time. */
    val prefetchCount: Int = 3,
    val theme: ThemeMode = ThemeMode.DARK,
    /** Device token from a 2-factor login so later logins need no code. */
    val deviceId: String? = null,
    val lastScanAt: Long = 0,
) {
    val isConfigured: Boolean get() = (localUrl.isNotBlank() || remoteUrl.isNotBlank()) && username.isNotBlank()
    val serverUrls: List<String> get() = listOf(localUrl, remoteUrl).filter { it.isNotBlank() }
    val storageLimitBytes: Long get() = storageLimitMb * 1024 * 1024
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store = context.dataStore

    private object Keys {
        val localUrl = stringPreferencesKey("local_url")
        val remoteUrl = stringPreferencesKey("remote_url")
        val username = stringPreferencesKey("username")
        val musicFolders = stringPreferencesKey("music_folders")
        val storageLimitMb = longPreferencesKey("storage_limit_mb")
        val syncOnWifiOnly = booleanPreferencesKey("sync_wifi_only")
        val prefetchCount = intPreferencesKey("prefetch_count")
        val theme = stringPreferencesKey("theme")
        val deviceId = stringPreferencesKey("device_id")
        val lastScanAt = longPreferencesKey("last_scan_at")
    }

    val flow: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            localUrl = p[Keys.localUrl] ?: defaults.localUrl,
            remoteUrl = p[Keys.remoteUrl] ?: defaults.remoteUrl,
            username = p[Keys.username] ?: defaults.username,
            musicFolders = p[Keys.musicFolders]?.split('\n')?.filter { it.isNotBlank() } ?: defaults.musicFolders,
            storageLimitMb = p[Keys.storageLimitMb] ?: defaults.storageLimitMb,
            syncOnWifiOnly = p[Keys.syncOnWifiOnly] ?: defaults.syncOnWifiOnly,
            prefetchCount = p[Keys.prefetchCount] ?: defaults.prefetchCount,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.theme,
            deviceId = p[Keys.deviceId],
            lastScanAt = p[Keys.lastScanAt] ?: 0,
        )
    }

    /** Always-available snapshot; starts with defaults until DataStore has loaded (a few ms). */
    val state: StateFlow<AppSettings> = flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun updateServer(localUrl: String, remoteUrl: String, username: String, musicFolders: List<String>) {
        store.edit { p ->
            if (p[Keys.username] != username.trim()) p.remove(Keys.deviceId)
            p[Keys.localUrl] = localUrl.trim()
            p[Keys.remoteUrl] = remoteUrl.trim()
            p[Keys.username] = username.trim()
            p[Keys.musicFolders] = musicFolders.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
    }

    suspend fun setStorageLimitMb(value: Long) = store.edit { it[Keys.storageLimitMb] = value }
    suspend fun setSyncOnWifiOnly(value: Boolean) = store.edit { it[Keys.syncOnWifiOnly] = value }
    suspend fun setPrefetchCount(value: Int) = store.edit { it[Keys.prefetchCount] = value }
    suspend fun setTheme(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }
    suspend fun setLastScanAt(value: Long) = store.edit { it[Keys.lastScanAt] = value }

    suspend fun setDeviceId(value: String?) = store.edit {
        if (value == null) it.remove(Keys.deviceId) else it[Keys.deviceId] = value
    }
}
