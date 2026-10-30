# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Big full screen lock screen player (huge buttons, shuffle, rating), opened via a full screen
  notification when the screen is turned on during playback; setup page with permission
  checklist and preview. Android's own lock screen controls cannot be enlarged by apps.
- QuickConnect IDs can be entered as internet address.
- Tapping the connection card shows the error for every address tried.

### Changed
- Shuffle is a pure on/off switch: it no longer starts a new song, and tapping a song no longer
  turns it off. With shuffle on, *previous* returns to the songs actually played before.
- Previous/next are pinned to their places in the system media controls.
- Settings redesigned as a clean list of equally sized rows; server account on its own page.

### Fixed
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
