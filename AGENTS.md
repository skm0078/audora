# Audora — AGENTS.md

## What this is
Offline-first Android app that reads PDFs aloud like a human (English, Hindi, Bengali) with background playback. Personal use + friends, sideloaded APK.
Spec: docs/BUILD-SPEC.md · Tickets: docs/TICKET-PLAN.md · Progress: docs/progress/ (one file per change; docs/PROGRESS.md is frozen history) · Decisions: docs/DECISIONS-OPEN.md, docs/adr/ · Docs index: docs/README.md (add a line for every new doc; not for ADRs or progress entries)

## Stack (change only via an ADR)
Kotlin 2.x, Compose, Hilt, Media3 1.11.x (ExoPlayer + MediaSessionService), sherpa-onnx 1.13.x (arm64-v8a), PdfiumAndroidKt, ICU BreakIterator, Room, DataStore, WorkManager (downloads only). Phase 3: Tesseract4Android (PaddleOCR ONNX only if P3-05 proves it, ADR-011). No proprietary SDKs (GPL-3.0).
Versions live only in gradle/libs.versions.toml. Build setup lives in build-logic/ (convention plugins: audora.android.application, audora.android.library, audora.jvm.library, audora.hilt, audora.room).
Platform: compileSdk 37, targetSdk 36, minSdk 31, arm64-v8a only (ADR-010).
Toolchain: AGP 9 (built-in Kotlin, so no kotlin-android plugin), Gradle wrapper, JDK 21 to run Gradle, Java/Kotlin target 17.

## Modules and rules
Package root: io.github.skm0078.audora (D7). Module packages: io.github.skm0078.audora.{core,pdf,speech,playback}. (Code samples in BUILD-SPEC.md use an older `app.audora` path; the package above wins.)
- :core — domain model, Result/error types, config objects, logging facade (AudoraLog). Pure Kotlin JVM module: no Android imports at all.
- :pdf — extract → quality → recover → layout → cleanup → ReadingUnit. Pure logic in plain Kotlin; Android adapters thin.
- :speech — normalization, script routing, SpeechEngine implementations, model manager.
- :playback — PlaybackService, TtsDataSource, render queue, progress, the app's Room database (library, resume anchors) and the file-access adapter. Synthesis runs HERE only.
- :app — Compose UI only; no business logic in composables.
Dependencies: app → playback → (speech, pdf) → core. Never the reverse.
- :spikes:* — throwaway debug apps that answer one risky question each (S1–S3). They may depend on :core only; nothing depends on them.

## Invariants
- The player never reaches ENDED while text remains (TtsDataSource blocks instead).
- Playback starts only from a user action. No boot auto-start.
- Resume uses ResumeAnchor (page + charStart + fingerprint + pipelineVersion). Bump PIPELINE_VERSION when :pdf output can change.
- Studio audio is kept across app updates, keyed by STUDIO_AUDIO_VERSION (playback/studio/StudioAudioVersion.kt). Bump it when a change can make the same sentence, voice and settings sound different (engine or model upgrade, speech text shaping, clip format); ADR-064.
- Never declare SMS, accessibility, or notification-listener permissions.
- APK is arm64-v8a only and every native library is 16 KB aligned (CI enforces with scripts/check_16kb_alignment.py).

## Commands
- Fast check: ./gradlew ktlintCheck detekt test
- Lint, every module against minSdk (CI runs it): ./gradlew :app:lintDebug
- Device tests (:pdf, needs a device or emulator; CI runs them on an emulator): ./gradlew :pdf:connectedDebugAndroidTest
- Auto-fix style: ./gradlew ktlintFormat
- Golden quality (from P2-01): ./gradlew :pdf:goldenTest (writes docs/QUALITY.md)
- Listening score (RI-8a): ./gradlew :playback:listeningTest (writes docs/LISTENING.md; corpus in pdf/golden/listening/)
- Debug APK: ./gradlew assembleDebug (CI builds it on every push and uploads it)
- Signed release: Release workflow from prod (docs/RELEASING.md); version lives in gradle.properties (audora.versionName)
- 16 KB check: python3 scripts/check_16kb_alignment.py app/build/outputs/apk/debug/*.apk
The maintainer has no Android Studio: CI on GitHub Actions is the build of record.

## Branches
- `dev`: default branch; stable, CI-green code. Every ticket is a feature branch (`feat/P1-04-playback-service`) merged into `dev` by PR.
- `prod`: what people install. Updated only by a PR from `dev` when a release is ready; release tags `v*` are cut from `prod` (P0-02).
- Never push directly to `dev` or `prod`.

## Parallel PRs (ADR 2026-10-25-parallel-prs-merge-cleanly)
Independent PRs must merge in any order with no conflicts. Every change adds its own files; it never appends to a
shared list.
- One branch per feature, made from the latest `dev`. No stacked branches (a branch made from another feature
  branch): squash-merging the bottom one makes every branch above it conflict. If work depends on an unmerged
  branch, wait for it to merge, or put both in one branch.
- Merge the latest `dev` into the branch (no rebase or force-push once others may have it) and get CI green before
  asking for the merge.
- Files added per change, named `<yyyy-mm-dd>-<slug>` (date the work was done, lowercase words): progress entry
  `docs/progress/<name>.md`, ADR `docs/adr/<name>.md`, What's new `app/src/main/assets/whatsnew/<name>.txt`.
- Never edit: `docs/PROGRESS.md` (frozen), the ADR list in `docs/README.md`, the §11 table in `BUILD-SPEC.md`, another
  change's entry file. Never add numbered ADRs or a numbered What's new.
- CI's first step, `python3 scripts/check_hotspots.py`, fails a branch that breaks these rules and says what to do
  instead. Run it before pushing. Change its rules only with the maintainer's agreement.

## Logging
- Use AudoraLog.d/i/w/e(tag, message, throwable) from any module. The app installs logcat + a rolling file (filesDir/logs, 2 MB × 3).
- Log context (book id, page, engine, voice), never the text of the user's PDF.

## Conventions
- File header comment: path + one-line purpose.
- Errors: typed Result; never swallow; log with context (book, page, engine, voice).
- No magic numbers: tunables in config objects (LayoutConfig, QueueConfig, QualityConfig).
- Tests first for :pdf and :speech; table-driven tests for normalizers.
- TODOs must name a ticket: `TODO(P1-07): ...` (detekt enforces).
- Something a listener would notice: add `app/src/main/assets/whatsnew/<yyyy-mm-dd>-<slug>.txt` with an `en:`, `hi:` and `bn:` line (one file per item). Nothing to bump: the app shows entries not seen yet (ADR 2026-10-25-parallel-prs-merge-cleanly).
- New strings go into a `strings_<feature>.xml` (en/hi/bn) of their own, never appended to `strings.xml`.
- No new dependency without asking. No network except model downloads. No cloud or paid services, ever.
- Never commit keystores, secrets, model binaries, or PDFs we can't redistribute.

## Definition of done
1. Ticket's done-when met. 2. ktlintCheck detekt test green (CI green). 3. Golden score not lower (if :pdf touched). 4. A `docs/progress/` entry added; an ADR if a decision changed. 5. Real-device checks listed in the PR.
