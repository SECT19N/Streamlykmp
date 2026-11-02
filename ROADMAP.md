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

## 4 Nov: TMDB Home

- [ ] Ktor client + DTOs
- [ ] Home rows (trending / popular movies and shows)
- [ ] TMDB attribution

## 5 Nov: Details + Search

- [ ] Details screen (TMDB metadata only, no playback)
- [ ] Search with debounce, all states

## 6 Nov: Polish

- [ ] Android TV focus and navigation on every screen
- [ ] Accessibility (content descriptions, readable text)
- [ ] Bonus, in order: pagination, session cache, favorites, search filters
- [ ] Smoke test on an API 23 phone emulator

## 7 Nov: Ship (buffer day, no new features)

- [ ] Debug/signed APKs for mobile and TV
- [ ] README final pass and documented assumptions
