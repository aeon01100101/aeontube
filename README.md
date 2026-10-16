# AeonTube v1.0.0

An Android YouTube client with **no ads and no tracking**, plus downloads —
built for Isaiah. Package `com.aeontube.app`, pure Java + ExoPlayer (Media3),
no Google account, no sign-in, no third-party SDKs.

## How the ad blocking works

AeonTube never loads the official YouTube player — the component that serves
pre-roll, mid-roll and banner ads. Instead it fetches plain video streams
through open-source **Piped** and **Invidious** frontends and plays them
directly in ExoPlayer (HLS / DASH / MP4). The streams contain no ad breaks.
On top of that, **SponsorBlock** auto-skips sponsor/intro/self-promo segments
baked into videos themselves (toggle in Settings).

## Servers & failover

Public Piped/Invidious instances come and go and YouTube periodically
bot-blocks their IPs per-video. AeonTube ships an ordered server list
(Piped ducks.party, Piped private.coffee, Invidious f5.si, then more),
remembers the last one that worked, and hops automatically when one fails.
Settings → Servers lets Isaiah pin custom Piped/Invidious URLs.

Verified live 2026-10-16 (see BENCH.md): trending, search + paging, channel
pages + paging, @handle → channel-id resolution, streams on two real videos
(incl. one where Piped was bot-blocked and Invidious carried it), byte-range
downloads of the exact video/audio URLs the app enqueues (real MP4 `ftyp`
bytes), DASH manifest with 6 adaptation sets, SponsorBlock reachability.

## My Channel downloads

Settings → paste `@handle` / channel URL / UC… id once → resolved and saved.
The **My Channel** tab then lists his uploads; every row (here and everywhere)
has **⬇ Video** and **♪ Audio** buttons. Any video's player screen also has
both. Downloads go through the system DownloadManager into
`Downloads/AeonTube/`. YouTube serves some videos split-only (no combined
MP4): the app downloads the HD video file and says plainly it has no sound
track, with the audio offered alongside.

## Other features

- Trending home, search with infinite scroll, channel pages with paging
- Quality picker (Auto = HLS/DASH adaptive, or a fixed MP4 quality)
- Opens `youtube.com/watch` and `youtu.be` links (Android share/open-with)
- Dark UI, big simple buttons

## Build

Permanent signing keystore lives OUTSIDE this tree at
`../signing/aeontube.keystore` (cert SHA-256 `a0f88fb6…`), wired via
`../signing/keystore.properties` in `app/build.gradle`.

    bash ~/workspace/aeonradio/tools/gradle-build.sh assembleRelease

Host tests (input parsing / filenames):

    host-test/run_tests.sh   # 25/25

## Honest limits

- Public server availability is outside our control; failover + custom
  server settings are the mitigation. A self-hosted instance is the endgame.
- Not device-tested yet — first install on Isaiah's phone is the verdict.
- Live streams and age-restricted videos may fail on public instances.
