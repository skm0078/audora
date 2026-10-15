# Research: playback, Android platform rules, distribution, CI (2026-10-14)

Source: research pass for Build Spec v0.2. Items marked *unverified* need a device check.

## Media3
- Latest stable **1.11.1 (2026-09-10)**. 1.10: services extend LifecycleService. 1.11: session getters throw off the app thread; FGS-start fixes; experimental `enablePerStreamMediaProgression()` reduces inter-item gaps and fixes stuck very short items (relevant to sentence clips).
- Use `MediaSessionService` (not MediaLibraryService). Opt into playback resumption: `MediaButtonReceiver` + `onPlaybackResumption()` restoring from Room. Service leaves foreground after 10 min paused/stopped.
- **Wake lock (checked in source):** WAKE_MODE_LOCAL default since 1.9.0; held in READY/BUFFERING while playWhenReady, **released in ENDED/IDLE**. If the render-ahead window drains, the playlist ends, the CPU can sleep and synthesis stalls. Fix: always keep a pending next item backed by a DataSource that blocks until the audio is ready (player stays BUFFERING), or hold our own PARTIAL_WAKE_LOCK while the synth queue is non-empty.
- `addMediaItem` while playing is supported. Per-item overhead: consider grouping sentences per item; keep exact resume via sentence offset table.
- Speed: `setPlaybackSpeed` (Sonic) is standard; fine for speech to ~2x *(judgment)*.
- Alternative: custom `SimpleBasePlayer` over streaming AudioTrack (gapless, sample-accurate) at the cost of more code. Start with ExoPlayer.

## Android 14–17
- 14: FGS types + FOREGROUND_SERVICE_MEDIA_PLAYBACK.
- 15: BOOT_COMPLETED cannot start mediaPlayback FGS; audio focus needs top app or FGS; 6h/day limit is NOT for mediaPlayback; edge-to-edge enforced.
- 16: edge-to-edge opt-out removed; predictive back default; jobs running alongside an FGS count against JobScheduler quota (do synthesis in the service, not WorkManager); unaligned native libs run in 16 KB compat mode with a warning dialog.
- **17 (stable on Pixels since 2026-06-16): background audio hardening.** Audio without a visible activity or non-short FGS is silently muted. Target 37 also needs while-in-use capability (FGS started from a user action). Keep FGS up through buffering. Test: `adb shell cmd audio set-enable-hardening throw`.
- 16 KB pages: Play deadline 2027-02-01, but device behavior applies to sideloads. AGP >= 8.5.1, NDK r28+, verify every prebuilt .so.
- targetSdk 36 recommended (Play requires it since 2026-08-31); 37 only after testing while-in-use.

## OEM killing
- dontkillmyapp: Xiaomi 5, Samsung 5, Oppo 4, Vivo 3, Realme 3. HyperOS not covered. Samsung says One UI 6+ honors proper FGS. No authoritative 2025-26 data for ColorOS/FuntouchOS/HyperOS *(unverified)*.
- Mitigation: proper mediaPlayback FGS + MediaStyle notification, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (fine off-Play), per-OEM steps, crash-safe resume.

## Distribution
- **Developer verification:** enforced from 2026-09-30 in Brazil, Indonesia, Singapore, Thailand, currently for installs via participating stores; direct sideloading unaffected "yet"; global rollout planned 2027. **India not named; assume 2027.**
- Free **limited distribution account**: email only, up to 20 devices for testing/personal use (device-add process undocumented).
- **Advanced flow** (rolling out since Aug 2026): Developer options, reboot, 24 h wait, then allow unverified installs. ADB installs exempt.
- Play Protect India Enhanced Fraud Protection blocks internet-sideloaded apps declaring SMS, notification-listener or accessibility permissions. Never declare them.
- Obtainium v1.6.17 (2026-09-13), verifies signing certs.

## CI
- actions/setup-java@v6, gradle/actions/setup-gradle@v6 (enhanced caching closed-source, free for public repos; MIT basic option). Tag `v*` -> assembleRelease -> softprops/action-gh-release. Estimated 4-8 min cached with prebuilt .so *(unmeasured)*. abiFilters arm64-v8a.

## Links
- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/about/versions/15/behavior-changes-15
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/16kb-page-size
- https://dontkillmyapp.com/
- https://developer.android.com/developer-verification/guides/faq
- https://www.androidauthority.com/android-sideloading-developer-verification-first-wave-rollout-3717921/
- https://9to5google.com/2026/08/18/google-gradually-rolling-out-androids-advanced-sideloading-ahead-of-developer-verification/
- https://9to5google.com/2024/10/03/google-blocking-sideloaded-apps-india/
- https://github.com/ImranR98/Obtainium/releases
