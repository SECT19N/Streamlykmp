# Streamly

Movies, TV shows (TMDB) and live TV (HLS) for Android mobile and Android TV, built with
Kotlin Multiplatform and Compose Multiplatform. Architecture: MVI.

See [ROADMAP.md](./ROADMAP.md) for planned work and progress.

## Assumptions

- TMDB is used for browsing and metadata only; there is no VOD playback.
- Live playback uses the provided `channels.json`, bundled with the app.
- Channels have no logo, so a placeholder (initials tile) is shown.
- The stream hosts are plain `http://`, so cleartext traffic is allowed for them (approved).
  `network_security_config.xml` permits it for the three stream IPs only; all other traffic (e.g.
  TMDB) stays HTTPS-only. A stream that redirects to a new host needs that host added.
- Offline: live channels need a network; favorites and cached images are available offline.
- TMDB content is requested in English.
- TMDB attribution (logo and the required notice) lives on the About tab, not on Home.

## Features

- **Home:** trending and popular movies and shows from TMDB, with loading, error and offline states.
  Rows load more pages as you near the end.
- **Live TV:** channel list and full-screen HLS playback (see the limitations below). When a stream
  carries more than one audio feed, an audio track switch appears in the player (currently ch_07
  and ch_14, shown as "English 1/2").
- **Search:** movies and shows together, debounced, with All / Movies / Shows filters and
  pagination.
- **Details:** TMDB metadata and cast, content rating, a facts section (status, language,
  seasons/episodes, budget/revenue) and a "Part of <collection>" row for movies in a franchise.
  No playback.
- **Favorites:** a heart on Details saves a title; the Favorites tab splits them into Movies and
  TV Shows.
- **About:** app description, TMDB logo and attribution.
- Works on phones (bottom bar) and Android TV / tablets (navigation rail, D-pad focus).

### Behaviour notes

- Favorites are saved on the device only (SharedPreferences on Android, UserDefaults on iOS), as the
  title and poster path, so the list still shows offline. They are lost on uninstall; there is no
  sync.
- TMDB results are cached in memory for 5 minutes. Retry always refetches, and if a refresh fails
  the last good result is shown instead. Nothing is persisted across app restarts.

## Known limitations and findings

### MP2 audio

**Problem.** 15 of the 16 working channels carry MPEG-1 Layer II ("MP2") audio at 64-320 kbps (only
ch_17 is AAC; ch_16 and ch_18 don't respond). Android isn't required to ship an MP2 decoder
and most phones don't, so ExoPlayer drops the audio track and plays the video silently, without
reporting an error. This only showed up on a physical phone after the player was built.

**Fix.** The app carries its own small MP2 decoder (`shared/src/androidMain/.../player/audio`).
`Mp2Decoder` is a Kotlin port of the audio decoder in
[PL_MPEG](https://github.com/phoboslab/pl_mpeg) (MIT, Dominic Szablewski, itself based on kjmp2),
plugged into ExoPlayer as an extra audio renderer that is only used when the device has no MP2
decoder of its own.

- Differences from PL_MPEG: requantisation follows ISO 11172-3 exactly (PL_MPEG under-scales coarse
  quantizers) and is done in floats.
- Tests compare the output with FFmpeg for 64, 96, 128, 192 and 320 kbps streams cut from the real
  channels (max error well under 16 LSB on 16-bit audio).

**Second cause (Rojava HD).** Its master playlist declares only `avc1` in `CODECS`, and ExoPlayer
trusts that to skip audio when it prepares without downloading a segment. The player now disables
chunkless preparation so the audio track is found in the stream.

**Ruled out.** The Jellyfin FFmpeg extension (no MP2 decoder compiled in, and it needs an older
Media3), and building Media3's own FFmpeg extension (needs the NDK and a committed binary).

**Not covered.**

- Joint-stereo streams, which share scale factors between channels in the intensity bands (none of
  the current channels use joint stereo)
- MPEG-2 (LSF) Layer II
- iOS, whose player is still a stub

## Structure

- `androidApp`: Android entry point (mobile + TV)
- `shared`: shared code and UI (`commonMain`), platform code (`androidMain`, `iosMain`)
- `iosApp`: Xcode project (optional target, requires a Mac)

## Requirements

- Android Studio with its bundled JBR (the system JDK may be too new for Gradle/AGP)
- minSdk 23 (Android 6.0)
- A free TMDB account. Add the **API Read Access Token** (the long v4 token) to `local.properties`:
  ```
  TmdbApiReadAccessToken=your_read_access_token
  ```
  Quotes around the value are optional. It is sent as a Bearer header and exposed to shared code
  through BuildKonfig. If it is missing the app still builds, but Home shows the error state.

## Build

```
./gradlew :androidApp:assembleDebug
```

A single APK (`androidApp/build/outputs/apk/debug/androidApp-debug.apk`) supports phones, tablets
and
Android TV: it declares both the standard and the Leanback launcher, and the UI adapts at runtime
(bottom bar on phones, navigation rail on TV and wide screens). There are no separate flavors.

## Tests

```
./gradlew :shared:testAndroidHostTest
```

---
This product uses the TMDB API but is not endorsed or certified by TMDB.
