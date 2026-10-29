package com.calmyjane.jaymusic.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.calmyjane.jaymusic.core.synology.Session
import com.calmyjane.jaymusic.core.synology.SynologyClient
import com.calmyjane.jaymusic.core.synology.SynologyException
import com.calmyjane.jaymusic.data.AppSettings
import com.calmyjane.jaymusic.data.SecretStore
import com.calmyjane.jaymusic.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * Keeps a File Station session alive in the background.
 *
 * Nothing in the app ever waits for this: playback of downloaded songs works in every state.
 * When the connection is [State.Online], songs can additionally be streamed and downloaded.
 */
class ServerConnection(
    context: Context,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    http: OkHttpClient,
    private val scope: CoroutineScope,
) {

    sealed interface State {
        data object NotConfigured : State
        data object Connecting : State
        data class Online(val address: String) : State
        data class Offline(val reason: String) : State
        /** The account uses 2-factor authentication; [connectWithOtp] must be called. */
        data object NeedsOtp : State
        data class LoginFailed(val reason: String) : State
    }

    private val client = SynologyClient(http)
    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state.asStateFlow()

    val isOnline: Boolean get() = _state.value is State.Online

    @Volatile
    private var session: Session? = null
    private var connectJob: Job? = null
    private var retryDelayMs = INITIAL_RETRY_MS

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun start() {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // A new network (e.g. Wi‑Fi at home) may make the local address reachable.
                retryDelayMs = INITIAL_RETRY_MS
                connect(force = true)
            }

            override fun onLost(network: Network) {
                if (!hasNetwork()) {
                    session = null
                    _state.value = State.Offline("No network")
                }
            }
        })
        // Reconnect whenever the server settings change.
        scope.launch {
            settings.flow
                .map { Triple(it.serverUrls, it.username, it.musicFolders) }
                .distinctUntilChanged()
                .drop(1)
                .collect { connect(force = true) }
        }
        connect()
    }

    /** Session for API calls, or null if not connected. Never blocks. */
    fun currentSession(): Session? = session

    /** Streaming URL for a song, or null when offline. Never blocks. */
    fun streamUrl(path: String): HttpUrl? = session?.let { client.downloadUrl(it, path) }

    fun client(): SynologyClient = client

    /** Waits up to [timeoutMs] for a session, e.g. in the background sync worker. */
    suspend fun awaitSession(timeoutMs: Long): Session? {
        session?.let { return it }
        connect()
        return withTimeoutOrNull(timeoutMs) {
            state.first { it !is State.Connecting }
            session
        }
    }

    /** Called when the server rejected the session (e.g. it expired). */
    fun invalidate(rejected: Session) {
        if (session?.sid == rejected.sid) {
            session = null
            connect(force = true)
        }
    }

    fun connect(force: Boolean = false) {
        if (!force && (session != null || connectJob?.isActive == true)) return
        connectJob?.cancel()
        connectJob = scope.launch { connectLoop(otpCode = null) }
    }

    fun connectWithOtp(code: String) {
        connectJob?.cancel()
        connectJob = scope.launch { connectLoop(otpCode = code) }
    }

    private suspend fun connectLoop(otpCode: String?) {
        var otp = otpCode
        while (true) {
            val config = settings.flow.first()
            val password = secrets.password
            if (!config.isConfigured || password.isNullOrEmpty()) {
                session = null
                _state.value = State.NotConfigured
                return
            }
            if (!hasNetwork()) {
                session = null
                _state.value = State.Offline("No network")
                return // The network callback restarts us.
            }
            if (session == null) _state.value = State.Connecting
            try {
                val (newSession, address) = login(config, password, otp)
                otp = null
                if (newSession.deviceId != null && newSession.deviceId != config.deviceId) {
                    settings.setDeviceId(newSession.deviceId)
                }
                session = newSession
                retryDelayMs = INITIAL_RETRY_MS
                _state.value = State.Online(address)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: SynologyException) {
                session = null
                when {
                    e.isOtpRequired -> { _state.value = State.NeedsOtp; return }
                    e.isOtpInvalid -> { _state.value = State.NeedsOtp; return }
                    e.code in 400..410 -> { _state.value = State.LoginFailed(e.message ?: "Login failed"); return }
                    else -> _state.value = State.Offline(e.message ?: "Server error")
                }
            } catch (e: Exception) {
                Log.i(TAG, "Connection failed: $e")
                session = null
                _state.value = State.Offline(e.message ?: e.javaClass.simpleName)
            }
            delay(retryDelayMs)
            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    /**
     * Logs in on all configured addresses in parallel. The local address wins if it works, so at
     * home songs come from the LAN even if the internet address is also reachable.
     */
    private suspend fun login(config: AppSettings, password: String, otp: String?): Pair<Session, String> =
        coroutineScope {
            val deviceName = "JayMusic (${Build.MODEL})"
            val attempts = config.serverUrls.map { url ->
                url to async {
                    runCatching {
                        client.login(url, config.username, password, deviceName, otp, config.deviceId)
                    }
                }
            }
            var firstError: Throwable? = null
            for ((url, attempt) in attempts) {
                val result = attempt.await()
                result.onSuccess { s ->
                    attempts.forEach { (_, other) -> other.cancel() }
                    return@coroutineScope s to url
                }
                val error = result.exceptionOrNull()
                // Auth errors are the most useful to show; network errors of the LAN address are expected away from home.
                if (firstError == null || (error is SynologyException && firstError !is SynologyException)) {
                    firstError = error
                }
            }
            throw firstError ?: IllegalStateException("No server address configured")
        }

    fun hasNetwork(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isUnmetered(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val TAG = "ServerConnection"
        const val INITIAL_RETRY_MS = 5_000L
        const val MAX_RETRY_MS = 5 * 60_000L
    }
}
