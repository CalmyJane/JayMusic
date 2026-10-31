package com.calmyjane.jaymusic

import android.app.Application
import android.content.Context
import com.calmyjane.jaymusic.cache.MediaCache
import com.calmyjane.jaymusic.data.AppDatabase
import com.calmyjane.jaymusic.data.LibraryRepository
import com.calmyjane.jaymusic.data.SecretStore
import com.calmyjane.jaymusic.data.SettingsRepository
import com.calmyjane.jaymusic.playback.QueueStore
import com.calmyjane.jaymusic.remote.ServerConnection
import com.calmyjane.jaymusic.sync.LibrarySync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class JayMusicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        container.connection.start()
        container.sync.schedulePeriodic()
    }
}

/** Manual dependency injection: one instance of everything for the whole process. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val database = AppDatabase.create(context)
    val settings = SettingsRepository(context, scope)
    val secrets = SecretStore(context)
    val library = LibraryRepository(database, scope)
    val connection = ServerConnection(context, settings, secrets, http, scope)
    val mediaCache = MediaCache(context, http, connection, library, settings, scope)
    val queueStore = QueueStore(context)
    val sync = LibrarySync(context, this)
}

val Context.appContainer: AppContainer get() = (applicationContext as JayMusicApp).container
