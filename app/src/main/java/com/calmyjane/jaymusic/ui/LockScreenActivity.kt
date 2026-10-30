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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.calmyjane.jaymusic.appContainer
import com.calmyjane.jaymusic.data.ThemeMode
import com.calmyjane.jaymusic.playback.PlayerConnection
import com.calmyjane.jaymusic.playback.PlayerUiState
import com.calmyjane.jaymusic.ui.components.RatingBar
import com.calmyjane.jaymusic.ui.theme.JayMusicTheme
import kotlinx.coroutines.launch

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
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOnLockScreen()
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
                val state by player.state.collectAsStateWithLifecycle()
                val index by container.library.trackIndex.collectAsStateWithLifecycle()
                val rating = state.currentPath?.let { index[it]?.rating }
                LockScreenPlayer(
                    state = state,
                    rating = rating,
                    onRate = { value ->
                        state.currentPath?.let { path ->
                            lifecycleScope.launch { container.library.setRating(path, value) }
                        }
                    },
                    player = player,
                    onUnlock = ::unlock,
                )
            }
        }
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

    private fun unlock() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            },
        )
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
}

@Composable
private fun LockScreenPlayer(
    state: PlayerUiState,
    rating: Int?,
    onRate: (Int) -> Unit,
    player: PlayerConnection,
    onUnlock: () -> Unit,
) {
    Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Song info: fixed height.
            Column(Modifier.fillMaxWidth().height(112.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    state.title.ifEmpty { "Nothing playing" },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFBDBDBD),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                if (rating != null) RatingBar(rating, onRate, starSize = 44.dp)
            }
            Spacer(Modifier.height(16.dp))

            // Previous / next: two huge halves of the screen.
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(Icons.Filled.SkipPrevious, "Previous", Modifier.weight(1f), onClick = player::previous)
                BigButton(Icons.Filled.SkipNext, "Next", Modifier.weight(1f), onClick = player::next)
            }
            Spacer(Modifier.height(12.dp))
            BigButton(
                icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.fillMaxWidth().weight(0.8f),
                filled = true,
                onClick = player::togglePlayPause,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(
                    icon = Icons.Filled.Shuffle,
                    label = if (state.shuffle) "Shuffle on" else "Shuffle off",
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    filled = state.shuffle,
                    iconSize = 40.dp,
                    showLabel = true,
                    onClick = { player.setShuffle(!state.shuffle) },
                )
                BigButton(
                    icon = Icons.Filled.LockOpen,
                    label = "Unlock",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    iconSize = 36.dp,
                    onClick = onUnlock,
                )
            }
        }
    }
}

@Composable
private fun BigButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    filled: Boolean = false,
    iconSize: Dp = 96.dp,
    showLabel: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val background = if (filled) Color.White else Color.Black
    val foreground = if (filled) Color.Black else Color.White
    Box(
        modifier
            .border(3.dp, Color.White, shape)
            .background(background, shape)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(iconSize))
            if (showLabel) {
                Spacer(Modifier.width(12.dp))
                Text(label.uppercase(), color = foreground, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
