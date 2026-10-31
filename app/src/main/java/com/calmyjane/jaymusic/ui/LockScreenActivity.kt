package com.calmyjane.jaymusic.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.calmyjane.jaymusic.appContainer
import com.calmyjane.jaymusic.data.ThemeMode
import com.calmyjane.jaymusic.playback.LockScreenLauncher
import com.calmyjane.jaymusic.playback.PlayerConnection
import com.calmyjane.jaymusic.ui.lockscreen.LockScreenPlayer
import com.calmyjane.jaymusic.ui.theme.JayMusicTheme

/**
 * Full screen player shown on top of the lock screen with very large buttons.
 *
 * Android's own lock screen media controls are drawn by the system and their size cannot be
 * changed by apps (some manufacturers make them tiny). This activity is started by the
 * [com.calmyjane.jaymusic.playback.PlaybackService] when the screen turns off during playback and
 * disappears as soon as the phone is unlocked.
 */
class LockScreenActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Opened automatically: get out of the way once the phone is unlocked. Opened from the
            // app's lock button: stay until the user swipes it away.
            if (intent.action == Intent.ACTION_USER_PRESENT && !stayAfterUnlock) finish()
        }
    }

    private val stayAfterUnlock: Boolean get() = intent.getBooleanExtra(EXTRA_STAY, false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastCreatedAt = android.os.SystemClock.elapsedRealtime()
        showOnLockScreen()
        LockScreenLauncher.dismissNotification(this)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.BLACK), SystemBarStyle.dark(android.graphics.Color.BLACK))

        val container = applicationContext.appContainer
        player = PlayerConnection(this, container.mediaCache, container.connection, lifecycleScope)
        player.connect()
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            JayMusicTheme(ThemeMode.DARK) {
                LockScreenPlayer(
                    container = container,
                    player = player,
                    scope = lifecycleScope,
                    onClose = ::finish,
                    onUnlock = ::unlock,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isShowing = true
    }

    override fun onStop() {
        isShowing = false
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        player.release()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    /** Swipe up: ask for the PIN / fingerprint like the normal lock screen does. */
    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
                override fun onDismissCancelled() = finish()
                override fun onDismissError() = finish()
            },
        )
    }

    companion object {
        /** True while the player is on screen, so it is not opened twice. */
        @Volatile
        var isShowing = false
            private set

        /** elapsedRealtime of the last onCreate, to tell whether a start request worked. */
        @Volatile
        var lastCreatedAt = 0L
            private set

        private const val EXTRA_STAY = "stay_after_unlock"

        /** @param stayAfterUnlock keep showing after the phone is unlocked (opened by the user). */
        fun intent(context: Context, stayAfterUnlock: Boolean = false): Intent =
            Intent(context, LockScreenActivity::class.java)
                .putExtra(EXTRA_STAY, stayAfterUnlock)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}
