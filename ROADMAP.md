# Roadmap (1–7 Nov 2026)

Status: `[x]` done, `[ ]` todo. Bonus items are cut first if time runs short; the four
states (loading / error / empty / offline) and TV navigation are never cut.

Scope: TMDB is used for browsing and metadata only, with no VOD
playback. Playback is for live channels from `channels.json`. Channels have no logo, so
placeholders are used. Cleartext HTTP is approved for the stream hosts. Offline support means
local favorites plus the image cache.

## 1 Nov: Setup

- [x] Review requirements and the channel list format
- [x] KMP project (Android + iOS, shared Compose UI), minSdk 23
- [x] Dependencies: Ktor, serialization, Koin, Coil 3, Navigation, Media3
- [x] Package-by-feature skeleton (`core`, `home`, `details`, `search`, `livetv`, `player`)
- [x] MVI base (`State` / `Intent` / `Effect` + base ViewModel)
- [x] TMDB key via `local.properties`
- [x] Android TV manifest (leanback launcher), navigation shell

## 2 Nov: Live TV

- [x] Navigation shell (bottom bar on phones, rail on TV/tablets)
- [x] Channel model + repository (bundled JSON, swappable for a URL)
- [x] Live TV list: logo (with fallback), name, category
- [x] Loading / error / empty / offline states

## 3 Nov: Player

- [x] Media3 HLS behind `expect/actual`
- [x] Full-screen playback, basic controls, retry on error
- [x] Cleartext HTTP config for the stream hosts
- [x] D-pad handling
- [x] Audio fix (unexpected finding on a real phone: 15 channels use MP2 audio, which Android
  can't decode on most devices): in-app MP2 decoder as an ExoPlayer fallback renderer, tested
  against FFmpeg (see README, "Known limitations")
- [x] Rojava HD stayed silent: its playlist's CODECS lists only video, so ExoPlayer skipped the
  audio track; chunkless HLS preparation is now off so tracks come from the stream itself
- [x] Confirm audio on a physical phone (Huawei Y9a) and an API 23 device

## 4 Nov: TMDB Home

- [x] Ktor client + DTOs
- [x] Home rows (trending / popular movies and shows)
- [x] TMDB attribution

## 5 Nov: Details + Search

- [x] Details screen (TMDB metadata only, no playback)
- [x] Search with debounce, all states

## 6 Nov: Polish

- [x] Android TV focus and navigation on every screen (theme-coloured focus ring, initial focus,
  focus restoration, TV rail, overscan padding, larger TV text; D-pad tested on a TV emulator)
- [x] Accessibility (merged card semantics, headings, live regions; not tested with TalkBack,
  out of scope)
- [x] Bonus: pagination (Home rows and Search)
- [x] Bonus: session cache (5 min, stale fallback when a refresh fails)
- [x] Bonus: favorites (device-local, heart on Details, Favorites tab)
- [x] Bonus: search filters (All / Movies / Shows)
- [x] Smoke test on an API 23 phone emulator

## 7 Nov: Ship (buffer day, no new features)

- [x] Debug/signed APKs for mobile and TV
- [x] README final pass and documented assumptions

## Extras (after Day 6)

- [x] Favorites split into Movies / TV Shows (segmented buttons)
- [x] Details: content rating (US first, else any country) and a facts section (status, language,
  seasons/episodes, budget/revenue)
- [x] Details: "Part of <collection>" row for movies in a franchise (a failed collection request
  doesn't fail the page)
- [x] Audio track switch in the player (shown only when a stream has more than one feed; only ch_07
  and ch_14 do, both labelled "eng", so they appear as "English 1/2"). Needs a check on a device.
