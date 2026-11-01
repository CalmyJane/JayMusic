# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- CalmyJane logo in the app bar, on the lock screen player, as app icon and status bar icon.
- "Show" filter in the sort menu: all, on this phone, synced (downloads allowed) or never
  downloaded; applies to the song list and the folder view.
- Cloud toggle on every song and folder: crossed out = never downloaded, with check = on the
  phone, outline = may be downloaded; tap to switch. The ⋮ buttons are gone (long-press opens
  the menu).
- Folder menu (long-press in the folder view): play, shuffle play, play next, add to queue,
  add all to playlist (or a new playlist named after the folder), download to phone now, never
  download / allow download. Songs get "download to phone now" as well.
- Error report in Settings (last crash or handled error, with copy button) for bug reports.
- "Never download" per folder (cloud icon in the folder view) and per song (song menu); existing
  copies are deleted, and it can be switched back at any time.
- "Lock player" button in the bottom navigation opens the lock screen player.
- Big full screen lock screen player, opened via a full screen notification when the screen is
  turned on during playback; setup page with permission checklist, preview and a log of recent
  attempts. Android's own lock screen controls cannot be enlarged by apps.
- QuickConnect IDs can be entered as internet address.
- Tapping the connection card shows the error for every address tried.

### Changed
- Shuffle reorders the queue itself: the upcoming songs stay fixed while skipping (before, they
  could change after every skip). Turning shuffle off restores the original order; turning it on
  again gives a new random order.
- Lock screen queue: swipe a song to the left to remove it. The long-press menu now has the
  rating and "play next", and a long press no longer also starts the song.
- Player bar at the bottom: shuffle, previous, play, next and repeat, a seek slider (also while
  paused) and a lock button that opens the big lock screen player (it then stays on the lock
  screen after pressing the power button).
- Lock screen queue: long song names scroll; hold and drag to reorder (auto-scrolls at the
  edges, works with shuffle); hold without moving for a menu (play now, add to playlist, never
  download, remove from queue). The progress bar is now a seek slider.
- Lock screen player: no title header (it is in the queue), previous/next moved to the top,
  more room for the queue.
- Library: slim icon bar (songs/folders, sort and filter menu, search) instead of search
  field, chips and play/shuffle buttons; denser song list.
- Lock screen player redesigned after car-mode players: huge previous/next at the top, queue
  ("up next") in the middle, a wide play/pause bar with small shuffle/repeat toggles, quick
  search (play now, play next, add to playlist), add current song to playlist and rating.
  Swipe up to unlock, tap an empty area to close.
- "Play next" now really plays next when shuffle is on.
- Shuffle is a pure on/off switch: it no longer starts a new song, and tapping a song no longer
  turns it off. With shuffle on, *previous* returns to the songs actually played before.
- Previous/next are pinned to their places in the system media controls.
- Settings redesigned as a clean list of equally sized rows; server account on its own page.

### Fixed
- Swiping the paused player notification away removes it for good (it came back once before):
  the playback service now shuts down when stopped; the queue is restored on next start.
- Shuffle/repeat on the lock screen player have a larger touch area, so a slightly-off tap no
  longer counts as "tap empty area" and closes the player. Reordering the queue for shuffle can
  no longer crash the app.
- Layout no longer jumps: the mini player, the connection card, the sync status and the rating
  row in the player always keep the same size.

## [0.1.0]

### Added
- Synology File Station connection with home/internet addresses, 2-factor login and device token.
- Library scan of configurable music folders; tags read from files during playback.
- Playback service with lock screen / notification controls including shuffle and repeat.
- On-device song cache with storage limit, worst-rated-first eviction and folder exclusions.
- Prefetching of upcoming songs, offline fallback and stall watchdog.
- Ratings, playlists, search, sorting and folder browsing.
- Background sync via WorkManager.
- Black and white high contrast theme (dark default, light, system).
- CI pipeline: unit tests, lint, APK builds; test pre-release for every push (old ones pruned),
  final release for `v*` tags.
