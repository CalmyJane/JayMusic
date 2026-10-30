package com.calmyjane.jaymusic.playback

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.calmyjane.jaymusic.R
import com.calmyjane.jaymusic.ui.LockScreenActivity

/**
 * Brings up [LockScreenActivity] on the lock screen.
 *
 * Android does not let apps enlarge the system's lock screen media controls, and it blocks apps
 * from opening screens while in the background. Two mechanisms are used:
 *
 * 1. A **full-screen notification** (what alarm clocks use). When the screen is turned on while
 *    the phone is locked and music plays, the system opens the player directly. Needs the
 *    notification permission and "full screen notifications" (allowed by default for apps that
 *    are not installed from the Play Store).
 * 2. A direct activity start when the screen turns off, which Android allows for apps that may
 *    "display over other apps".
 */
object LockScreenLauncher {

    private const val TAG = "LockScreenLauncher"
    private const val CHANNEL_ID = "lock_screen_player"
    private const val NOTIFICATION_ID = 4711

    /** What the lock screen player needs and what is missing; shown in Settings. */
    data class Requirements(
        val notifications: Boolean,
        val fullScreen: Boolean,
        val overlay: Boolean,
        val battery: Boolean,
    ) {
        val ready: Boolean get() = notifications && fullScreen && overlay && battery
    }

    private val _events = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())

    /** The last attempts to show the player, newest first; shown on the setup page. */
    val events: kotlinx.coroutines.flow.StateFlow<List<String>> = _events

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun log(message: String) {
        Log.i(TAG, message)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date())
        _events.value = (listOf("$time  $message") + _events.value).take(12)
    }

    /** Checks a moment later whether the player actually appeared. */
    private fun verifyShown(what: String) {
        val requestedAt = android.os.SystemClock.elapsedRealtime()
        handler.postDelayed({
            val started = LockScreenActivity.isShowing || LockScreenActivity.lastCreatedAt >= requestedAt
            log(if (started) "$what: opened" else "$what: not opened (blocked by Android)")
        }, 2_500)
    }

    fun requirements(context: Context): Requirements {
        val nm = context.getSystemService(NotificationManager::class.java)
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm.canUseFullScreenIntent()
        val battery = context.getSystemService(android.os.PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        return Requirements(notifications && nm.areNotificationsEnabled(), fullScreen, Settings.canDrawOverlays(context), battery)
    }

    private fun isLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** Screen turned off during playback: try to have the player ready when it turns on again. */
    fun onScreenOff(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            log("Screen off: no permission to display over other apps")
            return
        }
        try {
            context.startActivity(LockScreenActivity.intent(context))
            verifyShown("Screen off")
        } catch (e: Exception) {
            log("Screen off: start failed (${e.javaClass.simpleName})")
        }
    }

    /** Screen turned on during playback while locked: show the player via full-screen intent. */
    @SuppressLint("MissingPermission") // Checked in requirements().
    fun onScreenOn(context: Context) {
        if (LockScreenActivity.isShowing) return
        if (!isLocked(context)) {
            log("Screen on: phone not locked")
            return
        }
        val req = requirements(context)
        if (!req.notifications || !req.fullScreen) {
            log("Screen on: notification permissions missing")
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lock screen player", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Opens the big player on the lock screen"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
        val intent = PendingIntent.getActivity(
            context,
            0,
            LockScreenActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("JayMusic")
            .setContentText("Lock screen player")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setFullScreenIntent(intent, true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
            verifyShown("Screen on")
        } catch (e: SecurityException) {
            log("Screen on: notification not allowed")
        }
    }

    /** Removes the helper notification once the player is visible. */
    fun dismissNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}
