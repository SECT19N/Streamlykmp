# Streamly

Movies, TV shows (TMDB) and live TV (HLS) for Android mobile and Android TV, built with
Kotlin Multiplatform and Compose Multiplatform. Architecture: MVI.

See [ROADMAP.md](./ROADMAP.md) for planned work and progress.

## Assumptions

- TMDB is used for browsing and metadata only; there is no VOD playback.
- Live playback uses the provided `channels.json`, bundled with the app.
- Channels have no logo, so a placeholder (initials tile) is shown.
- The stream hosts are plain `http://`, so cleartext traffic is allowed for them (approved).
- Offline: live channels need a network; favorites and cached images are available offline.
- TMDB content is requested in English.

## Structure

- `androidApp`: Android entry point (mobile + TV)
- `shared`: shared code and UI (`commonMain`), platform code (`androidMain`, `iosMain`)
- `iosApp`: Xcode project (optional target, requires a Mac)

## Requirements

- Android Studio with its bundled JBR (the system JDK may be too new for Gradle/AGP)
- minSdk 23 (Android 6.0)
- A free TMDB API key, added to `local.properties`:
  ```
  TMDB_API_KEY=your_key_here
  ```

## Build

```
./gradlew :androidApp:assembleDebug
```

## Tests

```
./gradlew :shared:testAndroidHostTest
```

---
This product uses the TMDB API but is not endorsed or certified by TMDB.
