# Changelog

All notable changes are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
