# Streamly

Movies, TV shows (TMDB) and live TV (HLS) for Android mobile and Android TV, built with
Kotlin Multiplatform and Compose Multiplatform. Architecture: MVI.

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

### MP2 audio and software video (FFmpeg)

**Problem.** 15 of the 16 working channels carry MPEG-1 Layer II ("MP2") audio at 64-320 kbps (only
ch_17 is AAC; ch_16 and ch_18 don't respond). Android isn't required to ship an MP2 decoder
and most phones don't, so ExoPlayer drops the audio track and plays the video silently, without
reporting an error. The same applies to MPEG-2 video, and to H.264/HEVC profiles a device can't
decode in hardware.

**Fix.** The `ffmpeg` module bundles a minimal FFmpeg (decoders only: MPEG-2 video, H.264, HEVC,
MP1/MP2/MP3, AAC, AC-3/E-AC-3) through the NDK and plugs it into ExoPlayer as extra renderers
(`StreamlyRenderersFactory`):

- `FfmpegAudioRenderer`: adapted from Media3's FFmpeg extension (Apache-2.0), with Layer I/II mapped to
  FFmpeg's `mp1`/`mp2` decoders (Media3 maps them to the Layer III decoder).
- `FfmpegVideoRenderer`: written for this app, since Media3 has no FFmpeg video renderer. Frames are
  decoded on up to 4 threads, normalised to 8-bit 4:2:0 and converted to RGBA straight into the
  surface (swscale).

Both are added **after** the MediaCodec renderers, so hardware/MediaCodec decoding is still used
whenever the device supports the format; FFmpeg only takes over when it doesn't. Software video is
CPU-heavy, so expect it to struggle on low-end devices with HD streams.

FFmpeg is built LGPL-only (no `--enable-gpl`/`--enable-nonfree`) and statically linked into
`libffmpegJNI.so`. See `ffmpeg/NOTICE.md`.

**Second cause (Rojava HD).** Its master playlist declares only `avc1` in `CODECS`, and ExoPlayer
trusts that to skip audio when it prepares without downloading a segment. The player now disables
chunkless preparation so the audio track is found in the stream.

**Not covered.**

- iOS, whose player is still a stub
- Playlist `CODECS` that don't list MPEG-2 video (`mp2v`) may still be filtered out by ExoPlayer's HLS
  track selection before a renderer is asked

## Structure

- `androidApp`: Android entry point (mobile + TV)
- `shared`: shared code and UI (`commonMain`), platform code (`androidMain`, `iosMain`)
- `ffmpeg`: Android library with the FFmpeg NDK build and ExoPlayer renderers
- `iosApp`: Xcode project (optional target, requires a Mac)

## Requirements

- Android Studio with its bundled JBR (the system JDK may be too new for Gradle/AGP)
- minSdk 23 (Android 6.0)
- Android NDK 30.0.16248370 and CMake (install both with the SDK Manager), `git` and `make`
- A free TMDB account. Add the **API Read Access Token** (the long v4 token) to `local.properties`:
  ```
  TmdbApiReadAccessToken=your_read_access_token
  ```
  Quotes around the value are optional. It is sent as a Bearer header and exposed to shared code
  through BuildKonfig. If it is missing the app still builds, but Home shows the error state.

## Build

FFmpeg is built once, outside Gradle (output is git-ignored, takes a few minutes per ABI):

```
ffmpeg/scripts/build_ffmpeg.sh            # arm64-v8a armeabi-v7a x86_64
ffmpeg/scripts/build_ffmpeg.sh arm64-v8a  # or just the ABIs you need
```

Without it the project still builds, but FFmpeg decoding is unavailable (the renderers report
every format as unsupported and playback falls back to MediaCodec only). The script checks out
FFmpeg `n7.1.5`; override with `FFMPEG_TAG`. If you build fewer ABIs, adjust `abiFilters` in
`ffmpeg/build.gradle.kts`.

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
./gradlew :ffmpeg:connectedDebugAndroidTest   # needs a device/emulator; MP2 vs reference PCM and H.264/HEVC/MPEG-2 playback
```

---
This product uses the TMDB API but is not endorsed or certified by TMDB.
