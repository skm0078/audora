# ADR 2026-10-30-baseline-profile: ship a Baseline Profile of start, first run, opening a PDF and turning pages

**Status:** Accepted (2026-10-30). The second PR of the on-phone speed audit (item 2 of the ranked list). The
maintainer approved the new build tool (androidx.benchmark's Macrobenchmark, test-only). Nothing ships from it but a
text file; no feature or voice changes.

## Context

On a fresh install Android runs an app's Kotlin code through the interpreter and its just-in-time compiler until the
phone's idle-time compiler has seen enough use, which takes days. Until then the first launches, the first book
opened and the first page turns are the slowest the listener will ever see, on exactly the screens that make a first
impression. A Baseline Profile lists the classes and methods those paths run; Play and `profileinstaller` hand it to
Android at install, which compiles that code ahead of time.

The app already carries the profiles Compose, Media3 and other libraries ship (5 KB in `assets/dexopt`, through
`profileinstaller`, which Compose brings), but none of its own code is in them: Hilt's graph, `MainActivity`, the
library, the reader, PDF page drawing and the curl.

## Decision

1. **A test-only `:baselineprofile` module** (`com.android.test`, through the `audora.android.test` convention)
   drives `:app`'s `benchmark` build (the dev build, optimised like release, for x86_64) on an emulator with
   Macrobenchmark's `BaselineProfileRule`. Each round starts the app cold, finishes first run with Skip when it shows,
   opens a small English PDF through "Open with" as a Files app would, waits for the reader, turns three pages and
   leaves the reader with Back. It finds buttons by the app's own strings, read by name from the installed app, so a
   renamed string fails by name.
2. **The profile is source.** `scripts/ci/baseline_profile.sh` writes the result to `app/src/main/baseline-prof.txt`,
   which the build compiles into every APK (`assets/dexopt/baseline.prof`); R8 rewrites it for the shrunk code. The
   file is committed and reviewed like code; no normal build records anything.
3. **Recorded on CI by hand.** `baseline-profile.yml` records the profile on an Android 14 emulator (Android 13+
   lets the shell read a profile without root) and uploads it as the `baseline-prof` artifact. It runs on pushes that
   change the generator and by hand after big changes to start-up, the library or the reader.
4. **No Baseline Profile Gradle plugin.** The plugin would add generated build types next to our own `benchmark`,
   `dev` and `uiTest` types, and record from arm64-only builds the x86_64 emulator cannot install. One library
   (`benchmark-macro-junit4`, test-only) and a script do the same with less to learn and less to break.
5. **The `benchmark` build only is profileable by the shell** (`app/src/benchmark/AndroidManifest.xml`); dev and
   release are unchanged.
6. **One emulator script.** Booting CI's emulator moved from three copies in `android.yml`, `ui-tour.yml` and the
   new workflow into `scripts/ci/start_emulator.sh`.

## Consequences

- **Speed:** the first launches, first book opened and first page turns after install or update run compiled code
  instead of interpreted code. Google cites about 30 percent faster code from the first launch; the test phone is the
  judge (phone checks in the PR).
- **Cost:** the first recording is 14,370 rules (3,844 of them the app's own), a 1.45 MB text file in the repo (re-recorded
  after the speed quick wins: 14,646 rules, 3,967 the app's own, 1.49 MB); the
  compiled profile in the dev APK grows from 5,487 bytes (the libraries' alone) to 12,475, and CI fails a dev APK
  whose profile drops under 10,000. Install takes a
  little longer while Android compiles the listed code. No run-time cost; no new code in the APK.
- **Drift:** the profile describes the code when it was recorded. Renamed or removed methods are ignored, new code is
  left out until the next recording. Re-record after big changes to start-up, the library or the reader.
- **Accepted limits:** recorded from the R8 build (names kept, `-dontobfuscate`), so code R8 inlines differently in
  release is matched loosely; recorded on an x86_64 emulator, which runs the same Kotlin code a phone does. No
  startup profile (DEX layout) yet: that needs the Gradle plugin and is a later call.
- **Failures fail:** the recording step runs with `pipefail`, so a failed generator fails the run instead of passing
  through `tee` and reprinting the committed profile. The generator looks strings up under the installed application
  id, where the resource table carries them; looking them up under the code's namespace finds none.

## Alternatives considered

- **The Baseline Profile Gradle plugin:** Google's default, with automatic generation on Gradle managed devices.
  Rejected for now (decision 4); revisit if the plugin learns custom build types and ABIs.
- **Cloud profiles from Play only:** Play builds profiles from early installers, but only after enough installs of
  each version and never for the dev APK or sideloads. Ours helps from the first install.
