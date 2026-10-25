# Audora — Ticket Plan v1.0 (LOCKED 2026-10-14)

Companion to `BUILD-SPEC.md`. Each ticket = one agent session = one PR. A phase starts only when the previous phase's gate passes on a real phone. Size: S ≈ one short session, M ≈ one full session, L ≈ may split.

## Recommended order (the short answer to "which ticket first?")

1. **In parallel, today, no code:**
   - **S0 (you, ~1 hour):** listen to Hindi/Bengali voices on your phone using sherpa-onnx's ready-made TTS-engine APKs + Google's voice packs.
   - **S2 (agent):** PDF extraction spike. It runs on a desktop with pypdfium2 (the same PDFium engine as PdfiumAndroidKt), so it needs no phone, only ~10 sample PDFs from you in `D:\AUDORA\test-pdfs\`.
2. **First code ticket: P0-01 repo scaffold + CI**, now unblocked (D1, D3, D7 settled). Small, and it lets every later spike ship as a CI-built APK you can install.
3. **Then S3 (background playback spike)**: the riskiest part of the Phase 1 gate (wake-lock/ENDED gap, Android 17 muting, OEM killing). Proving it early means the rest of Phase 1 is mostly plumbing.
4. Then S1 (voice benchmark), P0-02, and Phase 1.

Why not start with features: the Phase 1 gate fails on background survival, not on UI. A cheap spike that runs for 60 minutes with a deliberately slow fake engine tells us more than any amount of feature code.

## Phase 0 — Foundation and spikes

| ID | Ticket | Depends on | Size | Done when |
|---|---|---|---|---|
| S0 | **Listening test (you).** Install sherpa-onnx TTS-engine APKs for Piper hi (rohan/priyamvada), Supertonic-3 hi, Bengali (coqui/mimic3); install Google hi-IN + bn-IN offline voice packs. Read the same 3 paragraphs with each. | — | S | `research/notes/S0-listening.md` with a 1–5 rating per voice and your phone model |
| S2 | **Extraction spike.** pypdfium2 vs PdfBox on ~10 PDFs (en 1-col, en 2-col, hi Unicode, bn Unicode, Krutidev, Bijoy, scanned). Measure: char error vs hand-checked excerpt, OCR char error for Tesseract vs PaddleOCR (incl. whether PP-OCR supports Bengali), matra-order errors, font name/size availability, legacy-font detection, ms/page. | sample PDFs | M | `research/spikes/S2-pdf-extraction/REPORT.md`; ADR-008 confirmed or revised |
| P0-01 | **Repo scaffold**: 5 modules, Hilt, Compose, version catalog (pins from spec §4), ktlint, detekt, logging facade + rolling log file, AGENTS.md, docs moved into repo, CI debug build + **16 KB alignment check** | D1, D3, D7 | M | `./gradlew ktlintCheck detekt test assembleDebug` green locally and in CI; debug APK artifact downloadable |
| S3 | **Playback spike** (`:spikes:playback` debug app): MediaSessionService + blocking `TtsDataSource` + fake engine with configurable RTF (e.g. 1.2 = slower than real time) + notification controls. | P0-01 | M | Plays 60 min screen-off on your phone with the slow fake engine, no stop; player never hits ENDED mid-book; inter-item gap measured; Android 17 hardening test passes (if your phone is on 17); report in `research/spikes/S3-…/REPORT.md` |
| S1 | **Voice benchmark** (`:spikes:voices` debug app): Piper en (ljspeech, cori), Kitten v0.8 nano, Kokoro v1.0 int8 (ADR-012), Supertonic-3 en, Piper hi, Supertonic-3 hi, Piper bn (converted), MMS ben via sherpa-onnx 1.13.8. | P0-01 | M | RTF + first-sentence latency on the test phone in a table (friend's phone optional, ADR-012); WAV samples in `research/voice-samples/`; 16 KB check on the sherpa `.so`s; English default voice chosen |
| P0-02 | **Signed release on tag** + keystore runbook | P0-01 | S | Tag `v0.0.1` publishes `audora-v0.0.1-arm64.apk` to GitHub Releases; Obtainium installs it on your phone |

**Phase 0 gate:** S1–S3 reports exist, default English voice chosen, ADR-003/008 confirmed or revised, spec bumped to v0.3.

## Phase 1 — MVP playback (English)

| ID | Ticket | Depends on | Size | Done when |
|---|---|---|---|---|
| P1-01 | Library: SAF file picker, persisted URI permission, recent books (Room) | P0-01 | M | Reopening the app lists books; a deleted/moved file shows a clear error |
| P1-02 | Basic extraction with PdfiumAndroidKt → `ReadingUnit` list with `charStart/charEnd` (no layout heuristics yet) + JNI library over PdfiumAndroidKt's `libpdfium.so` (font info, generated flag, map errors, `/ActualText`, render mode; ADR-011) + password prompt | S2 | M | Unit tests on 3 simple PDFs; page-at-a-time memory bound tested on a 500-page PDF with reopen every 100 pages |
| P1-03 | `SpeechEngine` + bundled English voice (from S1) + system TTS (`synthesizeToFile`, chunked by `getMaxSpeechInputLength`) | S1 | M | Both engines render a sentence to PCM; cancellation tested; missing voice pack reported clearly |
| P1-04 | `PlaybackService` + `TtsDataSource` + render queue + notification/lock-screen/headset controls (productionized from S3) | S3, P1-02, P1-03 | L | Plays a real PDF screen-off for 15 min; seek and voice change rebuild the queue |
| P1-05 | Reader screen: page picker, tap-to-start sentence, play/pause, skip, speed (Sonic), sleep timer | P1-04 | M | Start from page N works; speed change is immediate |
| P1-06 | Exact resume (`ResumeAnchor` + re-anchoring) + playback resumption + brand battery onboarding | P1-04 | M | Force-stop mid-sentence → reopen resumes at that sentence; changing `PIPELINE_VERSION` still resumes at the same sentence (test) |
| P1-07 | Diagnostics: Settings → Share logs; unfinished-session detection ("Reading stopped unexpectedly — resume?") | P0-01 | S | A friend sends you a log in two taps; process death after a kill is logged |

**Phase 1 gate (project goal):** a 50-page English PDF plays 60 min screen-off on your phone with no stop; lock-screen controls work; resume after force-stop is exact. Then a `v0.1.0` tag for friends.

Moved earlier vs v0.1: diagnostics (was P5-01). The gate is tested on friends' phones, and without logs a failure there is undiagnosable.

## Phase 2 — Human-like reading (English)

| ID | Ticket | Depends on | Size | Done when |
|---|---|---|---|---|
| P2-01 | Golden corpus (redistributable PDFs only) + similarity harness + `docs/QUALITY.md`. **Outside-view benchmark:** listen to Adobe Acrobat's Read Aloud (and one other reader app) on 5 golden PDFs and note what they skip, how they pause, where they misread. Black-box observation only; no decompiling (D8). | P1-02 | M | Baseline scores recorded; behavior notes in `research/notes/` |
| P2-02 | Lines, columns (XY-cut), reading order, paragraphs, headings | P2-01 | L | Two-column golden PDFs read in correct order; score up |
| P2-03 | Header/footer/page-number removal, de-hyphenation, ligatures, zero-width folding | P2-01 | M | Score up; no regressions |
| P2-04 | English normalizer (numbers, dates, ₹, abbreviations, URLs, Roman numerals) + prosody pauses | P1-02 | M | 100+ table-driven cases pass |

## Phase 3 — Hindi and Bengali

| ID | Ticket | Depends on | Size | Done when |
|---|---|---|---|---|
| P3-01 | Script routing per sentence + per-language voice settings | P1-03 | M | Mixed en/hi PDF switches voices correctly |
| P3-02 | Indic sentence splitting (`।`), Devanagari/Bengali digits, NFC | P3-01 | M | Indic unit tests pass |
| P3-03 | Quality scorer + `/ActualText` rebuild + **Indic reorder pass** + per-document OCR-sample cross-check (ADR-011) | S2, P2-01 | M | Indic Unicode golden PDFs pass; mis-mapped real PDFs route to OCR |
| P3-04 | **Legacy-font converter** (Krutidev, Bijoy/SutonnyMJ), font-name triggered | P3-03 | L | Legacy golden PDFs read intelligibly; unknown-glyph rate routes to OCR |
| P3-05 | OCR: Tesseract4Android with `tessdata_best` eng/hin/ben (downloaded models) + "OCR" page badge; optional PaddleOCR Devanagari experiment on device (ADR-011) | P3-03 | M | Scanned golden PDFs read intelligibly; per-page time logged on both test phones |
| P3-06 | Hindi/Bengali neural voices chosen in S0/S1 + "Voices & licenses" screen | S1 | S | Attributions shown for CC BY-SA / CC-BY-NC voices |

## Phase 4 — Premium

| ID | Ticket | Depends on | Size | Done when |
|---|---|---|---|---|
| P4-01 | Voice manager: `models.json`, WorkManager download, SHA-256, delete, import custom model | P1-03 | M | Interrupted download resumes; corrupt file rejected |
| P4-02 | Premium voices (Kokoro en, Supertonic hi) + render-ahead window from measured RTF | P4-01 | M | No audible stalls at 1.0x on your phone |
| P4-03 | Sentence highlight on the rendered page (char boxes) | P1-05 | M | Highlight tracks the spoken sentence |

## Phase 5 — Release

| ID | Ticket | Depends on | Size | Done when |
|---|---|---|---|---|
| P5-01 | README for friends, Obtainium setup, Play Protect note | P0-02 | S | A friend installs and updates without help |
| P5-02 | Developer-verification readiness: limited-distribution account, package + key registered | P0-02 | S | Registration done before India enforcement |
| P5-03 | `v1.0.0` release + device checklist on 3 phones | all | S | Checklist passed on your phone + 2 friends' |

## Prompt template per ticket

```markdown
Ticket: <ID> — <title>
Read AGENTS.md, docs/BUILD-SPEC.md, docs/PROGRESS.md and docs/adr/ first.
Goal: <one line>
Done when: <done-when line>
Constraints: touch only <modules>; no new dependencies without asking; keep :core public APIs unchanged unless the ticket says so.
Process: plan mode → I approve → tests first → implement → ./gradlew ktlintCheck detekt test → update PROGRESS.md (and QUALITY.md if :pdf) → list real-device checks and risks.
```
