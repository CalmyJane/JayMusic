package com.calmyjane.jaymusic.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calmyjane.jaymusic.ui.screens.ExclusionsScreen
import com.calmyjane.jaymusic.ui.screens.LibraryScreen
import com.calmyjane.jaymusic.ui.screens.LockScreenSettingsScreen
import com.calmyjane.jaymusic.ui.screens.MiniPlayer
import com.calmyjane.jaymusic.ui.screens.PlayerScreen
import com.calmyjane.jaymusic.ui.screens.PlaylistDetailScreen
import com.calmyjane.jaymusic.ui.screens.PlaylistsScreen
import com.calmyjane.jaymusic.ui.screens.ServerScreen
import com.calmyjane.jaymusic.ui.screens.SettingsScreen
import com.calmyjane.jaymusic.ui.theme.JayMusicTheme
import com.calmyjane.jaymusic.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.theme)
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.BLACK)
                } else {
                    SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.BLACK)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            JayMusicTheme(settings.theme) {
                AppRoot(vm)
            }
        }
    }
}

private enum class Tab(val label: String) { LIBRARY("Library"), PLAYLISTS("Playlists"), SETTINGS("Settings") }

/** Pages opened on top of a tab. */
private sealed interface Page {
    data class Playlist(val id: Long) : Page
    data object Exclusions : Page
    data object Server : Page
    data object LockScreen : Page
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var page by remember { mutableStateOf<Page?>(null) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = page != null && !playerOpen) { page = null }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(vm, onOpen = { playerOpen = true })
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t && page == null,
                                onClick = { tab = t; page = null },
                                icon = {
                                    Icon(
                                        when (t) {
                                            Tab.LIBRARY -> Icons.Filled.LibraryMusic
                                            Tab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                                            Tab.SETTINGS -> Icons.Filled.Settings
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.background,
                                    indicatorColor = MaterialTheme.colorScheme.onBackground,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            when (val p = page) {
                is Page.Playlist -> PlaylistDetailScreen(vm, p.id, padding, onBack = { page = null })
                Page.Exclusions -> ExclusionsScreen(vm, padding, onBack = { page = null })
                Page.Server -> ServerScreen(vm, padding, onBack = { page = null })
                Page.LockScreen -> LockScreenSettingsScreen(vm, padding, onBack = { page = null })
                null -> when (tab) {
                    Tab.LIBRARY -> LibraryScreen(vm, padding)
                    Tab.PLAYLISTS -> PlaylistsScreen(vm, padding, onOpen = { page = Page.Playlist(it) })
                    Tab.SETTINGS -> SettingsScreen(
                        vm,
                        padding,
                        onOpenServer = { page = Page.Server },
                        onOpenExclusions = { page = Page.Exclusions },
                        onOpenLockScreen = { page = Page.LockScreen },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            BackHandler { playerOpen = false }
            PlayerScreen(vm, onClose = { playerOpen = false })
        }
    }
}
