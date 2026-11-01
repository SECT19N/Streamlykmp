# Roadmap (1–7 Nov 2026)

Status: `[x]` done, `[ ]` todo. Bonus items are cut first if time runs short; the four
states (loading / error / empty / offline) and TV navigation are never cut.

## 1 Nov: Setup

- [x] Read spec and `channels.json`, list questions for the interviewers
- [x] KMP project (Android + iOS, shared Compose UI), minSdk 23
- [x] Dependencies: Ktor, serialization, Koin, Coil 3, Navigation, Media3
- [ ] Package-by-feature skeleton (`core`, `home`, `details`, `search`, `livetv`, `player`)
- [ ] MVI base (`State` / `Intent` / `Effect` + base ViewModel)
- [ ] TMDB key via `local.properties`
- [ ] Android TV manifest (leanback launcher), navigation shell

## 2 Nov: Live TV

- [ ] Channel model + repository (bundled JSON, swappable for a URL)
- [ ] Live TV list: logo (with fallback), name, category
- [ ] Loading / error / empty / offline states

## 3 Nov: Player

- [ ] Media3 HLS behind `expect/actual`
- [ ] Full-screen playback, basic controls, retry on error
- [ ] Cleartext HTTP config for the stream hosts
- [ ] D-pad handling

## 4 Nov: TMDB Home

- [ ] Ktor client + DTOs
- [ ] Home rows (trending / popular movies and shows)
- [ ] TMDB attribution

## 5 Nov: Details + Search

- [ ] Details screen
- [ ] Search with debounce, all states

## 6 Nov: Polish

- [ ] Android TV focus and navigation on every screen
- [ ] Accessibility (content descriptions, readable text)
- [ ] Bonus, in order: pagination, session cache, favorites, search filters
- [ ] Smoke test on an API 23 phone emulator

## 7 Nov: Ship (buffer day, no new features)

- [ ] Debug/signed APKs for mobile and TV
- [ ] README final pass, assumptions, commit history check

## Open questions for the interviewers

1. `channels.json` has no `logo` field: placeholder / initials, or will a new file follow?
2. All streams are `http://` (some raw IPs): is allowing cleartext traffic for them OK?
3. Channel list: fetch from a URL, or bundle the JSON?
4. Will the streams stay live during the review period?
5. Offline: friendly error + retry, or persistent caching expected?
6. Android TV: separate leanback entry point sharing the Compose UI acceptable?
7. TMDB language/region: English?
8. iOS: structured for it, but not buildable without a Mac. OK?
9. Live TV: category filtering wanted?
