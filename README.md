# JayMusic

A fast, offline-first music player for Android that plays your music from a **Synology NAS**.

- **Always plays immediately.** No “connecting…” screen. Downloaded songs play even with no
  network; when the NAS is reachable, every song on it can be streamed too.
- **Big lock screen player** with previous / play-pause / next plus **shuffle** and **repeat**
  buttons (Android's media controls on the lock screen and in the notification shade).
- **Ratings** (1–5 stars) and **playlists**.
- **Smart storage:** songs you play and the best rated songs of your library are kept on the phone
  up to a limit you choose. When the limit is reached, the **worst rated songs are deleted first**.
- **Folders that are never downloaded** (e.g. audiobooks); they can still be streamed.
- **Instant skipping:** the next songs in the queue (in shuffle order) are downloaded ahead.
  If a song takes too long to start, the next downloaded song plays instead.
- Black and white, high contrast design. Dark by default, light mode available.

## Install

1. Open the [latest release](../../releases/latest) on your phone.
2. Download `JayMusic-x.y.z.apk` and open it. Android asks to allow installing apps from your
   browser once.
3. Updates: install the newer APK the same way; your library, ratings and downloads are kept.

Every push also publishes a **test build** (pre-release `x.y.z-test.N`) on the
[Releases page](../../releases); only the newest few are kept. See
[docs/RELEASING.md](docs/RELEASING.md).

Requires Android 8.0 or newer.

## Set up your Synology

See **[docs/SETUP.md](docs/SETUP.md)** for details. In short:

1. In the app, open **Settings**.
2. Enter the **home address** (e.g. `http://192.168.1.10:5000`) and/or the **internet address**
   (e.g. `https://nas.example.com:5001`), your DSM **user name** and **password**, and the
   **music folders** (e.g. `/music`).
3. Tap **Save and connect**, then **Sync now**. The library appears within seconds to minutes
   depending on its size; downloads continue in the background.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/SETUP.md](docs/SETUP.md) | NAS preparation, addresses, HTTPS certificates, 2-factor login, troubleshooting |
| [docs/USAGE.md](docs/USAGE.md) | Everyday use: playing, rating, playlists, storage, offline behaviour |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the app works internally |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Building, testing, code layout, CI |
| [docs/RELEASING.md](docs/RELEASING.md) | Publishing a release, signing keys |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
