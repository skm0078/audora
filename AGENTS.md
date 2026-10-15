# Audora — AGENTS.md

## What this is
Offline-first Android app that reads PDFs aloud like a human (English, Hindi, Bengali) with background playback. Personal use + friends, sideloaded APK.
Spec: docs/BUILD-SPEC.md · Tickets: docs/TICKET-PLAN.md · Progress: docs/PROGRESS.md · Decisions: docs/DECISIONS-OPEN.md, docs/adr/

## Stack (change only via an ADR)
Kotlin 2.x, Compose, Hilt, Media3 1.11.x (ExoPlayer + MediaSessionService), sherpa-onnx 1.13.x (arm64-v8a), PdfiumAndroidKt, ICU BreakIterator, Room, DataStore, WorkManager (downloads only). Phase 3: Tesseract4Android (PaddleOCR ONNX only if P3-05 proves it, ADR-011). No proprietary SDKs (GPL-3.0).
Versions live only in gradle/libs.versions.toml. Build setup lives in build-logic/ (convention plugins: audora.android.application, audora.android.library, audora.jvm.library, audora.hilt).
Platform: compileSdk 37, targetSdk 36, minSdk 31, arm64-v8a only (ADR-010).
Toolchain: AGP 9 (built-in Kotlin, so no kotlin-android plugin), Gradle wrapper, JDK 21 to run Gradle, Java/Kotlin target 17.

## Modules and rules
Package root: io.github.skm0078.audora (D7). Module packages: io.github.skm0078.audora.{core,pdf,speech,playback}. (Code samples in BUILD-SPEC.md use an older `app.audora` path; the package above wins.)
- :core — domain model, Result/error types, config objects, logging facade (AudoraLog). Pure Kotlin JVM module: no Android imports at all.
- :pdf — extract → quality → recover → layout → cleanup → ReadingUnit. Pure logic in plain Kotlin; Android adapters thin.
- :speech — normalization, script routing, SpeechEngine implementations, model manager.
- :playback — PlaybackService, TtsDataSource, render queue, progress. Synthesis runs HERE only.
- :app — Compose UI only; no business logic in composables.
Dependencies: app → playback → (speech, pdf) → core. Never the reverse.
- :spikes:* — throwaway debug apps that answer one risky question each (S1–S3). They may depend on :core only; nothing depends on them.

## Invariants
- The player never reaches ENDED while text remains (TtsDataSource blocks instead).
- Playback starts only from a user action. No boot auto-start.
- Resume uses ResumeAnchor (page + charStart + fingerprint + pipelineVersion). Bump PIPELINE_VERSION when :pdf output can change.
- Never declare SMS, accessibility, or notification-listener permissions.
- APK is arm64-v8a only and every native library is 16 KB aligned (CI enforces with scripts/check_16kb_alignment.py).

## Commands
- Fast check: ./gradlew ktlintCheck detekt test
- Device tests (:pdf, needs a device or emulator; CI runs them on an emulator): ./gradlew :pdf:connectedDebugAndroidTest
- Auto-fix style: ./gradlew ktlintFormat
- Golden quality (from P2-01): ./gradlew :pdf:goldenTest (writes docs/QUALITY.md)
- Debug APK: ./gradlew assembleDebug (CI builds it on every push and uploads it)
- Signed release: Release workflow from prod (docs/RELEASING.md); version lives in gradle.properties (audora.versionName)
- 16 KB check: python3 scripts/check_16kb_alignment.py app/build/outputs/apk/debug/*.apk
The maintainer has no Android Studio: CI on GitHub Actions is the build of record.

## Branches
- `dev`: default branch; stable, CI-green code. Every ticket is a feature branch (`feat/P1-04-playback-service`) merged into `dev` by PR.
- `prod`: what people install. Updated only by a PR from `dev` when a release is ready; release tags `v*` are cut from `prod` (P0-02).
- Never push directly to `dev` or `prod`.

## Logging
- Use AudoraLog.d/i/w/e(tag, message, throwable) from any module. The app installs logcat + a rolling file (filesDir/logs, 2 MB × 3).
- Log context (book id, page, engine, voice), never the text of the user's PDF.

## Conventions
- File header comment: path + one-line purpose.
- Errors: typed Result; never swallow; log with context (book, page, engine, voice).
- No magic numbers: tunables in config objects (LayoutConfig, QueueConfig, QualityConfig).
- Tests first for :pdf and :speech; table-driven tests for normalizers.
- TODOs must name a ticket: `TODO(P1-07): ...` (detekt enforces).
- No new dependency without asking. No network except model downloads. No cloud or paid services, ever.
- Never commit keystores, secrets, model binaries, or PDFs we can't redistribute.

## Definition of done
1. Ticket's done-when met. 2. ktlintCheck detekt test green (CI green). 3. Golden score not lower (if :pdf touched). 4. PROGRESS.md updated; ADR if a decision changed. 5. Real-device checks listed in the PR.
