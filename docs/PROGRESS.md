# Audora — Progress

One entry per merged ticket, newest first. Each entry: what changed, what was verified, what still needs a real-device check.

## P0-02 — Signed releases (2026-10-15)
- `.github/workflows/release.yml`: run from `prod` (Actions → Release → Run workflow) or push a `v*` tag on `prod`. Builds and signs `:app:assembleRelease`, checks 16 KB alignment and the signer certificate, publishes `audora-vX.Y.Z-arm64.apk` + `SHA256SUMS.txt` to GitHub Releases. Refuses commits not on `prod` and tags that differ from the version.
- Version: `audora.versionName` in `gradle.properties` is the single source; `versionCode` is derived (`MAJOR*1000000 + MINOR*1000 + PATCH`).
- Signing secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, spec §7) live in a GitHub environment `release` limited to `prod` and `v*`, so a workflow edited on a feature branch cannot read them. Publishing runs in a separate job without the secrets, using the `gh` CLI like the dev-build job instead of `softprops/action-gh-release` (one fewer third-party action with write access).
- Key runbook and Windows key script (no Android Studio): `docs/RELEASING.md`, `scripts/release/new-release-key.ps1` (PKCS12, RSA 4096, ~27 years, random 32-char password, never overwrites an existing key).
- Android CI now also builds the unsigned release APK on every push, and ignores tags.
- Verified: key script end to end under PowerShell 7 with the JDK `keytool` (key created, secrets copied, base64 round-trip, re-run keeps the key); actionlint + shellcheck on both workflows; ktlint. The release build itself is verified only in CI.

**Pending (user):** one-time key setup and backups (docs/RELEASING.md steps 1–3), dev → prod PR, run the Release workflow; then pin `EXPECTED_CERT_SHA256` from the v0.0.1 release notes.

**Real-device checks:** Obtainium installs `audora-v0.0.1-arm64.apk` on the Moto Edge 50 Fusion next to the dev build; the app opens.

## S3 — Background playback spike, harness (2026-10-14)
- New debug app `:spikes:playback` (`spikes/playback/`): Media3 `MediaSessionService`, blocking `TtsDataSource` over a render-ahead queue, fake engine with configurable RTF (sleep or busy-CPU), run metrics and a shareable report. CI builds it and the dev-build release carries it as `audora-s3-spike.apk`.
- Report and protocol: `research/spikes/S3-background-playback/REPORT.md`. Media3 source review found a 10-minute stuck-buffering timeout P1-04 must handle.
- Verified: unit tests for the queue, WAV encoder, fake engine, playlist window and metrics; ktlint and detekt.

**Real-device checks (pending, this closes S3):** 60-min gate preset screen-off on the OnePlus 12 and the Moto Edge 50 Fusion; gap check with and without per-stream progression.

## S2 — PDF extraction spike (2026-10-14)
- Report: `research/spikes/S2-pdf-extraction/REPORT.md`; decision: ADR-011 (ADR-008 confirmed).
- Corpus: 40 real-world PDFs (2-column papers, 400–1,323 page books, Unicode/legacy Hindi and Bengali, scans, encrypted, rotated) + 12 generated ground-truth PDFs. Kept in the project's shared `audora/test-pdfs/` with a licence manifest; only the generator scripts are committed.
- PDFium beats PdfBox on Indic (0–5 % vs 18–32 % CER, spacing ignored) and on open time and memory. No page cap needed: 0.7–4.6 ms/page; memory bounded by reopening every 100 pages.
- New work for P1-02/P3-03: JNI library over PdfiumAndroidKt's `libpdfium.so`, `/ActualText` rebuild (Indic CER 9–22 % → 2–3 %), OCR cross-check for Indic text layers.
- OCR: Tesseract `best` for eng/hin/ben; PP-OCRv5 has no Bengali model; PaddleOCR Hindi stays an optional experiment.

**Real-device checks:** none yet (desktop spike). OCR and extraction timings on the OnePlus 12 and Moto Edge 50 Fusion come in P1-02 and P3-05.

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
