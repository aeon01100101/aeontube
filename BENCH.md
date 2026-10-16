# AeonTube bench evidence — 2026-10-16

Zero-tolerance ship gate: no APK ships on "it builds" alone.

## Host tests
- `host-test/run_tests.sh` — ParseUtil (channel-id/handle/URL parsing,
  video-id extraction, download filename sanitizer, views labels): **25/25**.

## Build
- Gradle assembleRelease (AGP 8.5.2, Media3 1.4.1): **BUILD SUCCESSFUL**,
  lint vital passed.
- APK: 3,817,212 bytes, sha256 `db9b60037307313e6563f88316a8e925d6cab7b00ac82199fe80e09c009b9b09`
- apksigner verify: permanent cert SHA-256 `a0f88fb694ec03b39561afb3a8b448fc75041d81a9a11403f3c5b6e9040e10f6`
  (keystore in `signing/` outside the source tree — updates install over the top forever).
- aapt: package `com.aeontube.app`, versionName 1.0.0, minSdk 24, targetSdk 34;
  activities Main/Player/Channel/Settings all present; permissions INTERNET,
  ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE (≤28 only).

## Live backend bench (mirrors the app's API calls + parsers)
Bench 1 (endpoints): Piped trending 15 videos ✓, Piped search 20 + nextpage ✓,
Piped channel @YouTube 30 videos + page 2 30 more ✓, Invidious trending ✓,
Invidious resolveurl `@YouTube` → `UCBR8-60-B28hp2BmDPdntcQ` ✓,
Invidious channel videos 60 ✓, SponsorBlock reachable (404 = no segments) ✓.

Bench 2 (streams/downloads, 7/7):
- Rick Astley `dQw4w9WgXcQ`: Piped streams OK; Invidious muxed=1, audio=4,
  videoOnly=14, DASH present. Download URLs (video + audio) byte-range fetched:
  real MP4 (`ftyp` magic), not HTML.
- Recent @YouTube upload `71ya_JC_IbM`: **Piped bot-blocked** (NewPipe
  SignInConfirmNotBot) — **Invidious carried it**: muxed=1, audio=36,
  videoOnly=12, DASH present; download URLs real MP4 bytes. Failover proven
  end-to-end on a real video.
- Invidious DASH manifest for the catalog video: 6 AdaptationSets.

## Known dead ends found by the bench (and designed around)
- Piped instances intermittently bot-blocked per-video (LOGIN_REQUIRED).
  Mitigation: multi-backend failover with last-good stickiness.
- Invidious `latest_version?local=true` on f5.si is behind an Anubis
  bot-check page — NOT used by the app.
- Invidious adaptive googlevideo URLs are IP-bound to the API caller —
  consistent in-app (same phone IP fetches metadata and media).
- One trending item was a live/premiere (empty DASH Period, no formats on
  any backend) — live streams are a stated limit, not a silent failure:
  the player surfaces a clear error.

## Not proven here
- On-device playback/install (no Android device in the sandbox) —
  Isaiah's phone is the victory lap, per the ship gate.
