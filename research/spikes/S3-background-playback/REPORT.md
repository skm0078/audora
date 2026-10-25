# S3 — Background playback spike

Status: **harness built; phone results pending.** Ticket: `docs/TICKET-PLAN.md` S3. Code: `spikes/playback/` (debug APK `audora-s3-spike.apk` on the dev-build release).

## Question

Can a Media3 `MediaSessionService` fed by a blocking, synthesis-backed `TtsDataSource` play for 60 minutes with the screen off, with an engine slower than real time, without ever reaching ENDED while text remains (C5)? And what do OnePlus/OxygenOS battery management and Android 17 background-audio hardening (C7) do to it?

## What the spike app does

| Piece | File | Notes |
|---|---|---|
| Fake engine, RTF configurable | `engine/FakeSpeechEngine.kt` | Renders speech-like tone bursts and takes RTF × audio length. `SLEEP` mode stalls if the CPU suspends; `BUSY` mode spins a core like a neural voice. |
| Render-ahead queue | `queue/RenderAheadQueue.kt` | One worker renders the window `[playing, playing + 3]`. Readers block on `awaitPcm`, interruptibly. |
| Blocking `TtsDataSource` | `service/TtsDataSource.kt` | Blocks in `open()` (the WAV header needs the length) until PCM is ready, so the player sits in BUFFERING. |
| Playlist feeding | `queue/PlaylistWindow.kt` | Appends a page (20 units) whenever fewer than 20 are queued ahead, like the real app appending extracted pages. |
| Service | `service/SpikePlaybackService.kt`, `PlaybackRun.kt` | Media3 notification, lock-screen controls, audio focus, becoming-noisy. Playback starts only from a button tap (C7). Optional own `PARTIAL_WAKE_LOCK`. Optional `enablePerStreamMediaProgression`. |
| Metrics | `metrics/RunMetrics.kt` | Stalls, ENDED/IDLE with text left, clean inter-item gap (p50/p95/max), measured RTF, CPU deep sleep while playing (realtime − uptime between 30 s heartbeats), screen-off playing time. |
| Process-death evidence | `service/RunStore.kt`, `device/ExitReasons.kt` | Report persisted every 30 s with `commit()`. A fresh process finding an active run marks it killed and records Android's `ApplicationExitInfo` reason. |

Presets: **60-min gate** (RTF 1.2, sleep), **Gate, busy CPU** (RTF 1.2, busy), **Gap check** (RTF 0.3), **Gap, per-stream** (RTF 0.3 + per-stream progression).

Pass criteria (`metrics/RunSnapshot.kt`): ≥ 60 min wall time, ≥ 50 min of it screen-off while playing, zero ENDED/IDLE with text left, zero player errors, zero render failures, ≤ 5 s measured CPU deep sleep while playing, process not killed.

## Findings from the Media3 1.11.1 source (before device runs)

1. **Wake lock:** `WAKE_MODE_LOCAL` is the default, held in READY and BUFFERING while `playWhenReady`, released in ENDED and IDLE (`ExoPlayerImpl.updateWakeAndWifiLock`). A blocking source keeps the lock. Confirms C5.
2. **Foreground service:** `MediaNotificationManager.isAnySessionUserEngaged` treats BUFFERING with `playWhenReady` as engaged, so the FGS stays up while we wait for synthesis.
3. **New risk, stuck-player detection:** since 1.9 ExoPlayer fails playback with `StuckPlayerException` after 10 min in BUFFERING with no loading progress (`DEFAULT_STUCK_BUFFERING_DETECTION_TIMEOUT_MS`) and after 10 min "suppressed" (e.g. a permanent audio-focus loss). A real engine never takes 10 min per sentence, but a hung engine would turn into a player error, not a silent stall. P1-04 should surface that error and recover, not raise the timeout.
4. Setting any stuck timeout to `Integer.MAX_VALUE` silently switches the default wake mode to NONE unless `setWakeMode` is called. The spike sets `WAKE_MODE_LOCAL` explicitly; P1-04 must too.
5. `enablePerStreamMediaProgression` is `@ExperimentalApi` and documented as "will be renamed or removed".

## Results (fill in from the shared reports)

| Run | Phone | Battery settings | Verdict | Wall / screen-off | Stalls (count, longest) | ENDED w/ text | CPU sleep | Gap p50 / p95 | Exit reason if killed |
|---|---|---|---|---|---|---|---|---|---|
| 60-min gate | second test phone, Android 16 | default | | | | | | n/a | |
| 60-min gate | test phone | default | | | | | | n/a | |
| Gap check | second test phone | default | n/a | | | | | | |
| Gap, per-stream | second test phone | default | n/a | | | | | | |

Android 17 hardening (`adb shell cmd audio set-enable-hardening throw`): not applicable yet; both test phones run Android 16. Re-run the gate preset when either phone gets Android 17.

## Decisions this spike will settle

- Is our own `PARTIAL_WAKE_LOCK` needed (Build Spec §6 "belt and braces")? Expected: no, if the gate passes with the switch off.
- Is battery exemption required on OnePlus, or only advisable? Decides how hard P1-06 onboarding pushes it.
- Is per-stream progression worth the experimental API? Decides by gap p95 with and without it.
- Does sentence grouping per item (2–5 sentences) need to happen? Only if clean gap p95 is audible (> ~80 ms).
