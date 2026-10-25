# ADR-072: The phones run an optimised build; speed is measured, not guessed

**Status:** Accepted (2026-10-25). Changes what `dev-build/audora-dev.apk` is (ADR-031 publishing, unchanged URL).

## Context
The maintainer reports the app as "very slow" on the Moto Edge 50 Fusion, and v0.1.0 waits on it. The APK every
phone test has used so far is `assembleDebug`. A debug build is the slowest way to run this app:

- **Debuggable.** ART keeps a debuggable app ready for a debugger, so it never compiles the app ahead of time
  from profiles and gives up optimisations it would otherwise make. Google's guidance is to judge performance only on
  non-debuggable builds. Compose suffers most, because its runtime and our UI run as plain bytecode on every frame.
- **No R8.** Compose's own R8 rules remove its source-information and tracing calls, and R8 inlines and removes
  unused code across the app and its libraries. None of that happens in debug.
- **Native code at -O0.** AGP builds a debug variant's CMake code as `Debug`, so our page reader
  (`libaudora_pdfium_ext`) and the acted voice's runtime (`libaudora_acted`: sampling, tokens) ran unoptimised.
  Release builds them `RelWithDebInfo` (-O2).
- **StrictMode `detectAll`** records a stack trace on every main-thread disk read.

`release` itself had R8 off as well, so the first Play build would have shipped without it.

## Decision
- **R8 on for release**: `proguard-android-optimize.txt` + `app/proguard-rules.pro`. It uses `-dontobfuscate`, so log
  and crash lines keep their names. It keeps the classes native code reaches by name: PdfiumAndroidKt,
  Tesseract4Android, our two JNI bridges, and sherpa-onnx through `speech/consumer-rules.pro`.
- **New `dev` build type** = release (not debuggable, R8, -O2 native code), but:
  - signed with the dev key, with the debug build's application id, so it updates the installed "ListenLock Dev"
    in place and keeps its data;
  - every voice (`SIMPLE_VOICES` false) and Settings' debug tools (`BuildConfig.DEV_TOOLS`) stay on;
  - the main-thread stall watchdog stays on. StrictMode is debug-only.
- **CI publishes `dev` as `audora-dev.apk`.** The debug build is published beside it as `listenlock-debug.apk`, under a
  name Obtainium's `audora-dev` filter does not match. It is the same app and the same key, so installing it replaces
  the dev build if R8 ever breaks something on a phone.
- **Measured on every push.** The device-tests job builds a `benchmark` type (dev plus x86_64, never shipped) and
  runs `scripts/perf/time_builds.sh` against the debug build (`uiTest`) on the same emulator. It reports cold start
  (median and worst of 10), frames over swipes, and memory. The table goes to the run summary. Emulator numbers
  are not phone numbers, so read the ratio between the two builds. Phone measurements are in `docs/PERFORMANCE.md`.
- Two code fixes ride along. Saved library covers are decoded without waiting behind the one lock that draws PDF
  pages. The spoken-word highlight polls about 30 times a second instead of on every frame (144 Hz on the Moto).

## Alternatives considered
- **Keep debug, add a Baseline Profile.** A Baseline Profile does nothing for a debuggable app, which ART never
  compiles from profiles. It needs the optimised build first. It is the next step, and it needs two new
  dependencies (`androidx.profileinstaller` in the APK, the Macrobenchmark test stack in CI), so it waits for the
  maintainer's yes.
- **Turn obfuscation on too.** Obfuscation saves little next to the voice files. It makes shared logs unreadable
  without a mapping file, and it is the part of R8 most likely to break JNI lookups.
- **Test the R8 build on CI's emulator.** The instrumented tests run against `uiTest` (debug). Running them against a
  minified build means extra keep rules for test-only APIs, which costs CI rounds. The emulator timing run does
  start the minified app on every push, which catches a crash at start.

## Consequences
- Dev testing now runs what ships. A missing keep rule shows up as a crash or a `NoSuchMethodError` in the dev
  build. Share logs, and `listenlock-debug.apk` is the fallback.
- CI does two more R8 runs (dev; release already built). The build job's timeout goes from 30 to 40 minutes.
- No `:pdf` output change, so PIPELINE_VERSION stays 12. No change to the audio either, so STUDIO_AUDIO_VERSION is
  unchanged.
