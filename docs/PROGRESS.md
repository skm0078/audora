# Audora — Progress

One entry per merged ticket, newest first. Each entry: what changed, what was verified, what still needs a real-device check.

## P0-01 — Repo scaffold + CI (2026-10-14)
- Five modules (`:app`, `:playback`, `:speech`, `:pdf`, `:core`) wired `app → playback → (speech, pdf) → core`. `:core` is a pure JVM module; the others are Android modules.
- Convention plugins in `build-logic/`; every version in `gradle/libs.versions.toml` (AGP 9.4.0, Gradle 9.8.1, Kotlin 2.3.21, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00; Media3 1.11.1, Room, DataStore, WorkManager, PdfiumAndroidKt pinned for later tickets).
- compileSdk 37 (targetSdk stays 36): current AndroidX requires it, see ADR-010.
- Hilt + Compose app shell: `AudoraApplication`, `MainActivity` (edge-to-edge, predictive back), placeholder screen, dynamic color.
- Logging: `AudoraLog` facade in `:core`, logcat sink, rolling file `filesDir/logs/audora.log` (2 MB × 3), uncaught exceptions written to the file before the crash.
- Quality gates: ktlint 1.8.0 and detekt 1.23.8 run as CLIs from the root build (`ktlintCheck`, `ktlintFormat`, `detekt`).
- CI (`.github/workflows/android.yml`): ktlint, detekt, unit tests, debug APK, 16 KB alignment + arm64-only check, APK artifact.
- Branches: `dev` (default, stable code, ticket PRs land here) and `prod` (releases; tags from P0-02 are cut here).
- Docs moved into the repo: spec, ticket plan, decisions, v0.1 archive, research notes.

**Real-device checks:** install the CI debug APK on the OnePlus 12; it opens and shows the placeholder screen; the launcher icon renders (including themed icons); `adb logcat -s` shows an `Audora.AppLogging` start line.

**Known gaps:** 16 KB check passes trivially until native libraries arrive (S1/P1-02); its own unit tests prove it rejects bad APKs. Signed release builds are P0-02.
