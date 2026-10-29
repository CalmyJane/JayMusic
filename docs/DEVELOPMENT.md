# Development

## Requirements

- JDK 17 or newer
- Android SDK with platform 35 (Android Studio Ladybug or newer installs it automatically)

## Build and test

```bash
./gradlew :core:test              # JVM unit tests of the core logic (no Android SDK needed for this module)
./gradlew :app:testDebugUnitTest  # app unit tests
./gradlew :app:lintDebug          # Android lint
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug       # install on a connected phone
```

The debug build has the application id `com.calmyjane.jaymusic.debug`, so it can be installed
next to a release build.

## Code layout

```
core/src/main/kotlin/com/calmyjane/jaymusic/core/
  synology/   SynologyClient, models and error codes
  library/    LibraryScanner, AudioFiles (formats, tag guessing), FolderRules
  cache/      StoragePolicy (eviction), CacheFiller (background fill)
app/src/main/java/com/calmyjane/jaymusic/
  JayMusicApp.kt   Application + AppContainer (dependency wiring)
  data/            Room entities/DAOs, LibraryRepository, settings, SecretStore
  remote/          ServerConnection (session lifecycle, connectivity)
  cache/           MediaCache, SynologyDataSource, TrackUri
  playback/        PlaybackService, PlayerConnection, queue persistence
  sync/            LibrarySync, SyncWorker
  ui/              MainActivity, MainViewModel, theme, screens, components
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces work together.

## Conventions

- Kotlin official code style, see `.editorconfig`.
- Logic that does not need Android goes into `core` and gets unit tests.
- Dependencies are declared in `gradle/libs.versions.toml`.
- Media3 APIs marked `@UnstableApi` are opted in per class with `@OptIn(UnstableApi::class)`.
- Commits: imperative subject line, explain *why* in the body. Update `CHANGELOG.md`.

## Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request:

1. unit tests (`core` and `app`),
2. Android lint,
3. debug APK build, uploaded as a workflow artifact (download it from the run's summary page to
   test a branch on the phone).

Test and lint reports are uploaded as artifacts as well.
