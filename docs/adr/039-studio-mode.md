# ADR-039: Studio mode, rendering a book ahead while the phone charges

**Status:** Accepted (2026-10-17). Plan item 6.

## Context
The premium voices (Kokoro English, about 150 MB) are estimated at 2.5–4× slower than real time on the Moto Edge 50
Fusion, so live playback with them can buffer, and every listen starts with the first sentence's render. Paid apps
avoid this by streaming from servers, which Audora will not do (zero spend, offline first). The plan's answer is to
render ahead while the phone is on the charger, when CPU time and battery cost nothing the listener notices.

C9 (Build Spec) keeps synthesis out of WorkManager: Android 16 counts jobs that run alongside a foreground service
against JobScheduler quota. ADR-034 already allows synthesis in a second, user-started foreground service (the
audiobook export). Android 12+ also refuses to start a foreground service from the background, so nothing can start
rendering on its own when the charger is plugged in.

## Decision
1. **A user-started foreground service.** "Prepare in studio mode" in a book's menu starts `StudioService`
   (`:playback`, `playback.studio`; mediaProcessing on Android 15+, dataSync before, like the export). It renders
   from the book's resume page until `StudioConfig.hoursAhead` (6 h) of audio is ready or the book ends, then stops.
   Its notification shows progress and offers **Stop**.
2. **Charging only, unless the listener says otherwise.** Before each sentence `ChargingGate` checks the charger:
   plugged in by the sticky battery status (`EXTRA_PLUGGED`), not `BatteryManager.isCharging`, which Android 14+
   turns true only after the level has risen for a while and never under a charge limit. Unplugged, it releases the
   wake lock and waits (the phone sleeps; the wait costs no battery) until a `POWER_CONNECTED` broadcast, which takes
   the lock again, or the next poll. The notification then offers **Start now**, which renders on
   battery for the rest of the run. While any audio plays (Audora or another app) it waits with the wake lock held,
   so studio mode never competes with listening for the CPU and goes on once playback stops, even screen off.
3. **Exactly the live renderer.** The service builds the same `EngineRenderer` as playback: the listener's style with
   the book's own speed, pace and voices on top, the book's pronunciation fixes, the reading modes and chapter cues,
   and the same `UnitReading.plays` selection of units. Audio is stored under a `StudioSignature`: pipeline version,
   app install time, style (rate, pace, clause pauses, reading modes, shaping), the explicitly chosen voice per
   language and the fixes. Any change gives another signature, so playback renders live until studio mode runs again.
   The listener's fix texts are hashed into the folder name and never logged.
4. **Playback looks first, then renders live.** `StudioSentenceRenderer` wraps playback's `EngineRenderer`. For each
   sentence it reads the page's studio file (once per page, again if the file is rewritten) and plays the clip when
   the unit's text key matches; anything else (another signature, a changed sentence, a damaged file) goes to the
   live voice. Studio clips skip the render-speed tracker, so the render-ahead window still sizes for the live voice.
5. **Lossless storage, pure Kotlin.** `LosslessPcm` codes 16-bit PCM in 4,096-sample blocks: constant blocks for the
   exact-zero silences, FLAC's fixed predictors of order 0–3 with Rice-coded residuals, or verbatim when prediction
   does not pay. Studio audio is therefore bit-identical to live audio, needs no codec or library, behaves the same
   on every phone and is unit-tested on the JVM. One file per page (`p<N>.studio`) with a CRC-32, written whole by
   temp file and rename, under `noBackupFilesDir/studio/<book hash>/<signature hash>/`.
6. **Bounded.** Pages more than `keepBehindPages` behind the listener and folders of old signatures are deleted when
   a run starts; all books' studio audio stays under `StudioConfig.maxBytes` (2 GiB), dropping the books written
   longest ago first; when the app opens, books removed from the library lose their audio and so does every folder
   written before the installed build (each signature carries the build's install time, so that audio can never play
   again); a run stops when free storage falls below
   `minFreeBytes` (512 MiB). `renderRetryLimit` failures in a row stop the run; one failed sentence is left to live
   rendering.

## Alternatives considered
- **WorkManager with a charging constraint.** The natural Android tool, and it could start on its own at plug-in, but
  C9 forbids synthesis there and a long-running worker needs a foreground service anyway, which Android 12+ will not
  start from the background. Rejected.
- **AAC (MediaCodec) storage.** About 29 MB per hour instead of roughly 60–80 MB, but lossy (a second generation of
  coding on top of the voice), device-dependent, untestable without a phone, and every sentence would need a decoder
  start on the render thread. Rejected for now; the codec sits behind `StudioClip`, so it can change with a format
  version bump.
- **IMA ADPCM.** About 40 MB per hour and simple, but audibly noisy on the quiet tails of premium voices. Rejected.
- **Rendering on battery by default.** Kokoro at 3× real time would cost a large share of the Moto's battery per
  listening hour. Kept as the explicit "Start now" choice.

## Consequences
- An hour of Kokoro audio needs about three hours on the charger (estimate until the final Moto session measures it);
  a night fills two to three hours and the next charge carries on from the new resume page.
- Storage: about 60–80 MB per ready listening hour at 22–24 kHz; at most 2 GiB in all.
- Every app update discards studio audio, because the signature keys on the build's install time. Keying it on a
  renderer version instead would keep hours of rendering across routine updates, but every change to the normalizer,
  the voices or sherpa-onnx would then need a manual bump, and a missed bump plays stale audio; the build time is
  kept until a renderer version is worth that risk.
- RAM: while studio mode runs next to playback, a second copy of the voice model is loaded (about 150 MB for Kokoro);
  rendering itself pauses while audio plays.
- Studio mode does not restart itself after the listener moves on; the listener starts it again (one tap), because
  Android allows no background start (C7 forbids auto-start in any case).
- A new system voice installed between studio mode and listening is the one change the signature does not see (the
  default voice is not resolved, to avoid binding the phone's TTS on the render thread).
