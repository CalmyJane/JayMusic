# Architecture

JayMusic is a single-activity Android app written in Kotlin, built from two Gradle modules:

| Module | Type | Contents |
| --- | --- | --- |
| `core` | Pure Kotlin/JVM library | Synology API client, library scanner, folder rules, storage policy, cache filler. No Android dependencies, so it is fully unit tested on the JVM. |
| `app` | Android application | Playback, cache, database, sync, UI. |

## Overview

```
 UI (Compose) ── MainViewModel ── PlayerConnection ──(MediaController)──┐
      │                │                                                 │
      │                ├── LibraryRepository (Room)                      ▼
      │                ├── SettingsRepository (DataStore)          PlaybackService
      │                └── LibrarySync ── SyncWorker (WorkManager)  (MediaSession + ExoPlayer)
      │                                      │                            │
      └────────────── MediaCache (SimpleCache) ◄──────────────────────────┘
                              │
                     SynologyDataSource ── ServerConnection ── SynologyClient (core)
                                                                     │
                                                              DSM File Station API
```

`AppContainer` (in `JayMusicApp.kt`) creates one instance of each component per process
(manual dependency injection, no framework).

## Key design decisions

### Songs are identified by their NAS path
`/music/Artist/Album/01.flac` is the primary key in the database, the media id in the player
and the cache key. Media items use the stable URI `jaymusic://track?p=<path>`
(`TrackUri`); `SynologyDataSource` turns it into a File Station download URL with the *current*
session id only when data is actually needed. Cached songs therefore never depend on a session.

### One cache for streaming, prefetching and sync
`MediaCache` wraps Media3's `SimpleCache` with a `NoOpCacheEvictor`: the cache never deletes on
its own. Everything goes through `CacheDataSource`:

- the player streams a song and the bytes are stored as they are played,
- the prefetcher and the background sync use `CacheWriter` to store songs completely.

A song counts as *on the phone* once all bytes are stored (`CacheInfo.complete`). Songs in
excluded folders are routed around the cache (`RoutingDataSource`).

### Eviction policy
`core/cache/StoragePolicy` decides what to delete. Order: lowest rating score first (unrated =
2.5), partial downloads before complete ones, least recently played first, larger files first.
Songs playing now and queued next are protected. The background sync (`CacheFiller`) may only
evict songs that score *strictly lower* than the song it wants to add, which prevents churn.

The budget is `min(user limit, cache size + free space − 500 MB)`.

### Never wait for the network
- `ServerConnection` logs in in the background (local and internet address in parallel; local
  wins) and exposes a `State`. No UI or playback path blocks on it.
- `SynologyDataSource` fails immediately when there is no session.
- `PlayerConnection.play` filters out songs that are not on the phone when offline.
- `PlaybackService` skips unavailable songs, and a watchdog skips to the next downloaded song if
  buffering takes longer than 8 seconds.
- DSM answers expired sessions with HTTP 200 and a JSON body; `SynologyDataSource` checks the
  content type so such answers never get cached as audio, and renews the session.

### Playback service
`PlaybackService` is a Media3 `MediaSessionService`. The system builds the notification and the
lock screen player from the session; custom `CommandButton`s add shuffle and repeat. It also

- prefetches the next *n* items in play order (`Timeline.getNextWindowIndex` respects shuffle),
- reads the real tags (`Format.metadata`) of the playing song into the database,
- counts plays after 30 s (or half the song),
- persists the queue to `queue.json` and restores it on start and for playback resumption
  (headset button / lock screen after the app was killed).

### Library sync
`LibrarySync.run` (executed by `SyncWorker`) scans the music folders with `LibraryScanner`
(breadth-first, 4 folders in parallel), merges the result into Room keeping ratings and
statistics, drops cached copies of changed or deleted files and then runs `CacheFiller`. A worker
stops after 8 minutes and enqueues a continuation, staying within WorkManager's limits.

### Credentials
The password is encrypted with an AES-GCM key held in the Android Keystore (`SecretStore`).
Backups are disabled. The 2-factor device token is stored in DataStore.

### UI
Jetpack Compose with Material 3, themed strictly black and white (`ui/theme/Theme.kt`). One
`MainViewModel` exposes state as `StateFlow`s; list filtering runs on `Dispatchers.Default`.
Navigation is a small state machine in `MainActivity` (tabs + pages + player overlay).

## Data model (Room)

| Table | Purpose |
| --- | --- |
| `tracks` | One row per song: path, folder, tags, size, modification time, duration, rating, play count, last played. |
| `playlists` | Playlist names. |
| `playlist_entries` | Ordered songs of a playlist (by path, so entries survive rescans). |
| `excluded_folders` | Folders that are never downloaded. |

## Synology API usage

| API | Method | Used for |
| --- | --- | --- |
| `SYNO.API.Info` | `query` | Discover paths and versions of the APIs below. |
| `SYNO.API.Auth` | `login` (POST), `logout` | Session (`session=FileStation`, `format=sid`), 2-factor and device token. |
| `SYNO.FileStation.List` | `list`, `list_share` | Library scan (paginated, 1000 entries per page). |
| `SYNO.FileStation.Download` | `download` | Streaming and downloading (supports HTTP range requests). |

### QuickConnect

`core/synology/QuickConnect` resolves a QuickConnect ID with the (undocumented) service used by
Synology's own apps: `POST https://global.quickconnect.to/Serv.php` with the commands
`get_server_info` (for the `dsm_https` and `dsm` services) and, if no relay is known,
`request_tunnel` at the returned control host. Candidates are LAN addresses (including Synology's
`*.direct.quickconnect.to` names), direct internet addresses and the relay. `ServerConnection`
logs in on all candidates in parallel and uses the most preferred one that works.
