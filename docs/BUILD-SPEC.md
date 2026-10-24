# Audora — Build Spec v1.0 (LOCKED 2026-10-14)

Oct 14, 2026 · Owner: Swarnakeshar (skm0078) · Supersedes "PDF Voice Reader — Build Spec v0.1" (archived in `docs/archive/`).

> **Status: locked.** All decisions D1–D8 settled 2026-10-14 (`DECISIONS-OPEN.md`). Changes from here go through an ADR in `docs/adr/` and a version bump. Spike results (S1–S3) may revise defaults marked "decided by S1/S2/S3"; that is planned, not a change of scope. **Project rule: no cloud and no paid services; on-device only (local self-hosting at most).** Research behind every change is in `research/notes/` (01 TTS, 02 PDF, 03 playback/platform/distribution).

## 0. What changed from v0.1 and why

| # | Change | Why (evidence) |
|---|---|---|
| C1 | Primary PDF extractor: **PdfiumAndroidKt** (`io.legere:pdfiumandroid`) instead of PdfBox-Android | PdfBox-Android's last release is ~Jan 2023 (effectively unmaintained) and is the heaviest on memory. PdfiumAndroidKt is Apache-2.0, released 2026-07, native, 16 KB aligned, already exposes char boxes and font size. Needs a small JNI addition for font name/weight. PdfBox stays only as the S2 comparison. |
| C2 | New pipeline stages: **Indic visual-to-logical reorder** and **legacy-font converter** (Krutidev, Bijoy/SutonnyMJ) before OCR | Neither PDF engine reorders pre-base matras (ि ি ে ৈ, ো ৌ). Legacy fonts can be converted deterministically from the font name + mapping table, which is faster and more accurate than OCR when it works. |
| C3 | Bengali neural voice: **official Piper `bn_BD/google/medium`** (CC BY-SA 4.0) replaces the unofficial bn_BD candidate | Released upstream in rhasspy/piper-voices; 16 speakers, covers bn_IN. MMS-ben (non-commercial, 16 kHz) drops to fallback. |
| C4 | English/Hindi candidates added: **KittenTTS v0.8 nano** (Apache-2.0, ~32 MB) and **Supertonic 3** (Hindi, OpenRAIL-M) | Both are in sherpa-onnx 1.13.x. Kitten may beat Piper on size; Supertonic is the only non-espeak Hindi neural option. S1 decides. |
| C5 | Playback invariant: **the player must never reach ENDED while text remains** | Media3 releases its wake lock in ENDED/IDLE (checked in source). If synthesis falls behind, the CPU sleeps and reading stops with the screen off. This is the most likely way to fail the Phase 1 gate. See §6. |
| C6 | Speed via **ExoPlayer `setPlaybackSpeed` (Sonic)**, not re-synthesis | No cache invalidation on speed change; instant response. Engine-native rate only if quality at >2x is bad. |
| C7 | **Android 17 background-audio hardening**: playback starts only from a user action; FGS stays up while buffering | Android 17 silently mutes audio from apps without a visible activity or non-short FGS. |
| C8 | **Resume anchor** = page + character offset + sentence fingerprint + pipeline version, not just sentence index | Sentence indices shift every time the Phase 2 pipeline improves; a pure index would resume at the wrong sentence after an update. |
| C9 | Synthesis runs **only inside the playback service**, never in WorkManager | Android 16 counts jobs running alongside an FGS against JobScheduler quota. WorkManager is for model downloads only. |
| C10 | **One app, open-source OCR only; no ML Kit** (D2) | espeak-ng makes the APK GPL-3.0; ML Kit is proprietary. User chose one app. OCR = Tesseract + PaddleOCR ONNX models (Apache-2.0) run on the ONNX Runtime already shipped with sherpa-onnx. |
| C11 | Distribution: plan for **Android developer verification** (global 2027) | Free limited-distribution account covers 20 devices; register package + key before India enforcement. Never declare SMS / accessibility / notification-listener permissions (India Play Protect blocks those for sideloads). |
| C12 | Phase 0 adds **S0: zero-code listening test** with sherpa-onnx's official TTS-engine APKs | Lets you hear Piper hi, Supertonic hi and two Bengali voices on your own phone today, before any code. |
| C13 | Platform baseline pinned: targetSdk 36, minSdk 31 (D5), Media3 1.11.1, sherpa-onnx 1.13.8, 16 KB alignment check in CI | Current as of Oct 2026. |

## 1. Summary and feasibility

Audora is buildable as a free, offline-first, sideloaded Android APK. Every requirement (start from any page, choose a voice, play with screen off or in other apps, resume at the exact sentence) is supported by current Android APIs.

Four real limits shape the design:

1. **Voice speed vs. quality.** Kokoro sounds best but is not real time on mid/low phones (RTF ~1.0–3.0). Piper/Kitten are 5–10x faster than real time. Voices are tiered; premium voices render ahead.
2. **Phone makers killing background apps.** Xiaomi/Oppo/Vivo/Realme can still kill a foreground media service. Mitigation: a correct mediaPlayback FGS, battery-exemption onboarding by brand, crash-safe resume.
3. **PDF text quality.** "Reading like a human" depends on extraction more than on the voice. Digital English PDFs are good. Indic PDFs need reordering, legacy-font conversion, or OCR.
4. **Bengali has the weakest offline voices.** The official Piper Bengali voice helps; S0/S1 decide whether it beats Google's system voice.

## 2. Scope

**MVP (Phases 1–2), English only**
- Open a PDF via the system file picker (SAF); library of recent books.
- Start from any page, or tap a sentence to start there.
- Voices: one bundled English neural voice (Piper or Kitten, per S1) + Android system voices.
- Playback with screen off and in other apps; lock-screen, notification and headset controls.
- Speed 0.5x–3x, skip sentence back/forward, sleep timer (15/30/60 min, end of page, end of chapter).
- Exact resume per book, surviving process death and app updates.
- "Human reading" pipeline for digital PDFs (reading order, header/footer removal, hyphen repair, normalization, pauses).

**v1 (Phases 3–4)**
- Hindi and Bengali with per-sentence voice selection by Unicode script.
- Indic reorder, legacy-font conversion, OCR fallback.
- Premium voices (Kokoro English, Supertonic Hindi), voice manager, render-ahead mode.
- Sentence highlight on the page.
- ~~Hosted-AI page cleanup~~: **dropped (D6, no-cloud rule).** A post-v1 on-device experiment only if the deterministic pipeline proves insufficient.

**Non-goals:** any cloud or paid service, iOS, Play Store listing, accounts, analytics/telemetry, EPUB, voice cloning, replacing TalkBack.

## 3. Voice strategy

All neural models run through **sherpa-onnx 1.13.8** (Apache-2.0 runtime). System voices go through `TextToSpeech`.

| Language | Role | Candidate(s) | Engine | Size | License | Decided by |
|---|---|---|---|---|---|---|
| English | Default (bundled) | Piper medium **ljspeech or cori** (public-domain data) **or** KittenTTS v0.8 nano | sherpa VITS / Kitten | ~64 MB / ~28 MB + 19 MB espeak-ng-data | MIT + public domain / Apache-2.0 | S1 rule, ADR-012 (blind rating; Kitten only if RTF ≤ 0.5 on the Moto) |
| English | Premium (download) | Kokoro **v1.0** int8 (af_heart, bf_emma…); Supertonic 3 en | sherpa Kokoro / Supertonic | 132 MB / 145 MB | Apache-2.0 / OpenRAIL-M | S1; offered only if measured RTF < 0.8 |
| English | Fallback | Android system TTS | `TextToSpeech` | 0 | device | — |
| Hindi | Default | Google hi-IN system voice until a voice is downloaded; recommended download Piper hi_IN **pratham** (priyamvada offered) | system / sherpa VITS | 0 / 64 MB | device / CC BY-NC-SA 4.0 | S0 + S1; provisional by S1 desktop (ADR-030) |
| Hindi | Premium | Supertonic-3 hi | sherpa Supertonic | 145 MB (shared with en) | OpenRAIL-M | S1 |
| Hindi | Studio mode (download) | Chatterbox Multilingual, acted (Resemble AI), Turbo decoder | native ONNX Runtime | see ADR-059 | MIT | ADR-059 |
| Bengali | Default | Google bn-IN system voice until a voice is downloaded; recommended download **SYSPIN Bengali male** (IISc studio VITS, converted, 4 threads) | system / sherpa VITS | 0 / 114 MB | device / MIT | Indic voice spike; ADR-053 (replaces ADR-030's Piper google) |
| Bengali | Alternatives | SYSPIN Bengali female (MIT); Piper bn_BD google medium (CC BY-SA 4.0, fastest); MMS-TTS ben (CC-BY-NC 4.0) | sherpa VITS | 114 / 78 / 114 MB | as listed | offered as downloads |
| Any | Advanced | Import any sherpa-onnx model folder (`.onnx` + `tokens.txt` [+ espeak data]) | sherpa | — | user's | — |

**Script routing (ADR-005, unchanged).** Each sentence is tagged by Unicode script: Latin → English, Devanagari U+0900–097F → Hindi, Bengali U+0980–09FF → Bengali. Deterministic, instant. v1 splits English runs of ≥3 words inside Indic sentences; the split happens at render time, one clip per sentence (ADR-026).

**Never shipped (ADR-012):** Piper voices trained on or fine-tuned from Lessac/Blizzard 2013 data (lessac, amy, hfc_*, hi_IN rohan): research-only, no redistribution.

**Attribution.** CC BY-SA, CC BY-NC-SA and CC-BY-NC voices require an in-app "Voices & licenses" screen. *(P3-06, ADR-030: Settings → Voices & licences lists every voice with licence, credit and source links, from credits kept in the catalog; the first voice downloaded for Hindi or Bengali becomes that language's reader.)*

## 4. Architecture

Native **Kotlin 2.x + Jetpack Compose**, five Gradle modules. Synthesis and playback live inside one foreground media service.

```
:app (Compose UI, nav, DI)
  └─ :playback (PlaybackService, TtsDataSource, render queue, progress)
        ├─ :speech (normalizer, script router, SpeechEngine impls, model manager)
        ├─ :pdf    (extract → quality → reorder/convert/OCR → layout → cleanup → ReadingUnit)
        └─ :core   (domain model, Result/errors, logging facade, config objects)
```

Dependencies flow `app → playback → (speech, pdf) → core`, never the reverse. `:pdf` and `:speech` keep their logic in plain Kotlin classes behind thin Android adapters, so most behavior is JVM-testable.

**Stack (pinned in `libs.versions.toml`):** Kotlin 2.x, Compose (BOM), Media3 **1.11.1** (ExoPlayer + MediaSessionService), sherpa-onnx **1.13.8** AAR (arm64-v8a), **PdfiumAndroidKt 2.0.x** (text + render; no `PdfRenderer` fallback, ADR-024), ICU `BreakIterator` (android.icu), Room, DataStore, WorkManager (downloads only), Hilt (KSP), kotlinx.coroutines. Phase 3: Tesseract4Android 4.9.x with `tessdata_best` eng/hin/ben (ADR-011); PaddleOCR (PP-OCR) ONNX on the existing ONNX Runtime only if the optional P3-05 experiment beats Tesseract on Hindi. No ML Kit (D2).

**Platform baseline:** compileSdk 36, targetSdk 36 (37 only after testing the while-in-use rule), minSdk 31 (Android 12, D5), AGP ≥ 8.5.1, NDK r28+, `abiFilters arm64-v8a`. Reference device for development: Moto Edge 50 Fusion (Snapdragon 7s Gen 2); OnePlus 12 (SD 8 Gen 3, Android 16) is a pre-release check (ADR-012). Edge-to-edge + predictive back supported from day one.

```kotlin
// File: core/src/main/kotlin/app/audora/core/ReadingUnit.kt
// Purpose: One speakable sentence with enough position data to resume, highlight and re-anchor.
data class ReadingUnit(
    val page: Int,
    val index: Int,              // sentence index within the page (display only, NOT the resume key)
    val charStart: Int,          // offset into the page's reading-order text (ADR-018)
    val charEnd: Int,
    val text: String,            // normalized, speakable text
    val lang: Lang,
    val pauseAfterMs: Int,
    val bounds: List<RectF>?,    // glyph boxes for highlighting; null for OCR'd/converted text
)

// File: core/src/main/kotlin/app/audora/core/ResumeAnchor.kt
// Purpose: Survives pipeline changes: re-anchor by offset first, then by fingerprint.
data class ResumeAnchor(
    val bookId: BookId,
    val page: Int,
    val charStart: Int,
    val fingerprint: Long,       // hash of the first ~48 normalized chars of the sentence
    val pipelineVersion: Int,
    val voice: VoiceId,
)

// File: speech/src/main/kotlin/app/audora/speech/SpeechEngine.kt
// Purpose: Single contract every TTS backend implements; playback never knows which engine it uses.
interface SpeechEngine {
    val id: EngineId
    fun supports(lang: Lang): Boolean
    fun voices(): List<VoiceInfo>
    /** Renders one sentence to 16-bit PCM. Cancellable; never on the main thread. Speed is applied by the player. */
    suspend fun render(text: String, voice: VoiceId): RenderResult   // RenderResult = Pcm(sampleRate, samples) | Failure(reason)
    fun release()
}
```

## 5. PDF → human-like reading pipeline

Runs lazily per page, two pages ahead of playback, cached per book (a few analysed pages and small page profiles, ADR-020). Each stage is a pure function with tests; `PIPELINE_VERSION` bumps whenever output can change.

1. **Extract** chars with boxes, font size, font name (PdfiumAndroidKt + our JNI library over its `libpdfium.so`: font info, generated flag, Unicode-map errors, `/ActualText` marks, render mode; ADR-011). Rebuild `/ActualText` spans so PDFium's fake spaces do not split Indic words. Reopen the document every 100 pages. Font weight is unreliable and unused.
2. **Score quality** per page: Unicode-map errors, invisible text over a page image, empty/garbage text, legacy font names per glyph run, invalid Indic sequences. Route: legacy run → 3a; map errors / hidden OCR layer / garbage / empty → 3c; **Indic documents: OCR one sample page and compare with the (rebuilt, reordered) text layer; CER ≤ 5 % → text layer, else 3c for every page** (re-check 1 page in 50; CER line by line so block order does not count). English: text layer. (ADR-011, ADR-021)
3. **Recover text**
   - a. **Legacy-font converter**: table-driven Kruti Dev / DevLys / Chanakya → Unicode and Bijoy/SutonnyMJ → Unicode, chosen by font name, with reph and pre-base matra reordering; runs wherever legacy text is read. Unknown glyph rate above threshold (or an unsupported layout such as Shusha) → OCR (ADR-023).
   - b. **Indic reorder**: on pages detected as visual order, move pre-base matras (ि ি ে ৈ) after their consonant cluster, compose two-part vowels (ে+া → ো) and move rephs before their syllable; drop placeholder glyphs inside clusters on every page. A repair tried before the OCR cross-check; S2 found it fixes visual order only, not mis-mapped glyphs (ADR-021).
   - c. **OCR**: Tesseract with `tessdata_best` for `eng`/`hin`/`ben` (PP-OCRv5 has no Bengali model; on Hindi it was not consistently better, ADR-011). Runs in the render-ahead window. Page shows a "Scanned / OCR" badge.
4. **Rebuild layout**: lines by baseline → columns by recursive XY-cut, columns first (OpenDataLoader-PDF's XY-Cut++ as a reference) → reading order → paragraphs by gap/indent, list items by bullet or label (ADR-029) → headings by relative font size and a bold font name (weight is unreliable, ADR-011). Unit offsets index the resulting reading-order text (ADR-018).
5. **Remove clutter**: lines repeating in the top/bottom 8% of the text area across ≥3 pages of a 7-page window, header lines whose page number runs with the page, standalone page numbers, table interiors; citation markers skipped (setting) (ADR-020).
6. **Repair**: de-hyphenate across line breaks (keep real hyphens), expand ligatures, fold soft hyphens / zero-width and control chars (ZWJ/ZWNJ kept), drop line-start bullets (ADR-020); NFC first, and "|" typed for a danda after Indic text becomes `।` (ADR-028).
7. **Split sentences**: ICU `BreakIterator` per locale, then the Indic rules: `।`/`॥` as sentence end, Hindi/Bengali abbreviations and initials, verse numbers kept with their verse (ADR-028).
8. **Normalize for speech**: numbers, dates, ₹, %, ranges (incl. Devanagari/Bengali digits), URLs/emails → "link", Roman numerals in headings. Table-driven tests. Runs in `:speech` at render time, not on `ReadingUnit.text` (ADR-019).
9. **Prosody**: pause after headings (~600 ms), paragraphs (~400 ms), list items (~300 ms; items found by bullet or label at layout), played as silence at the end of each sentence's clip (ADR-029).

**Golden corpus.** Redistributable PDFs (public-domain books, CC BY papers, US-government Hindi/Bengali documents, self-generated Hindi/Bengali PDFs) in `pdf/golden/`, scored on the JVM from PDFium page dumps (ADR-017). Copyrighted samples stay in the shared `audora/test-pdfs/` folder and are only used locally. Each golden PDF has an expected text (`pdf/golden/expected/*.txt`, hand-checked). Pipeline changes must not lower the corpus score; CI enforces it. This is the agent's main self-check.

**AI cleanup: dropped (D6).** Nothing leaves the phone except model downloads.

## 6. Playback and background audio

**Design: a synthesis-backed DataSource inside a Media3 `MediaSessionService`.**

- Each playlist item is a `MediaItem` with a URI like `audora://book/<id>/page/<p>/unit/<i>` (optionally grouping 2–5 sentences per item; S3 measures whether grouping is needed).
- A custom **`TtsDataSource`** resolves the URI by asking the render queue for that unit's PCM and serves it as WAV bytes. **If the audio is not ready, `read()` blocks**, so the player sits in **BUFFERING**, not ENDED. Media3 keeps the wake lock and the FGS in BUFFERING, so synthesis continues with the screen off. *(C5)*
- A **render-ahead queue** (coroutine in the service) prefetches N units ahead (3 for fast voices, measured-RTF-based for Kokoro) into a bounded cache (~20 MB, LRU, `cacheDir/tts/`). *(P4-02, ADR-027: the window is seconds of audio from each voice's measured RTF, with a short start hold; the cache is in memory, ADR-016.)*
- The playlist always holds at least the next page of items, appended as pages are extracted; the last item of a book is the only legitimate ENDED.
- Seek / voice change: cancel the queue from the current point and rebuild. Speed change: `player.setPlaybackParameters` only *(C6)*.
- Try Media3's experimental `enablePerStreamMediaProgression()` to remove inter-item gaps on short clips (S3).
- Belt and braces: hold our own `PARTIAL_WAKE_LOCK` while the render queue is non-empty and playWhenReady is true. S3 decides whether this is needed.

**Staying alive with the screen off**
- Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, service `foregroundServiceType="mediaPlayback"`, `MediaButtonReceiver`, `<queries>` for `TTS_SERVICE`. **Never** SMS, accessibility, or notification-listener permissions.
- Playback starts only from a user action (UI, notification, media button) so Android 17 doesn't mute it *(C7)*. No auto-start on boot.
- `POST_NOTIFICATIONS` requested on first play (Android 13+).
- First-launch onboarding by `Build.MANUFACTURER`: battery-exemption deep link (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) + brand steps, **OnePlus/OxygenOS first** (OnePlus: battery Don't optimize + allow background activity; Xiaomi autostart + No restrictions; Samsung Never sleeping apps; Oppo/Realme/Vivo allow background activity).
- Playback resumption opt-in: `onPlaybackResumption()` rebuilds the playlist from the last `ResumeAnchor`.

**State.** On each unit start, persist `ResumeAnchor` to Room (debounced, ≤1 write per 2 s, plus on pause/stop). On reopen: same `pipelineVersion` → exact; different → find the unit containing `charStart`, verify fingerprint, else nearest fingerprint match on that page *(C8)*.

## 7. Build, signing and distribution

- **Repo (D1)**: public GitHub `skm0078/audora`, GPL-3.0. `D:\AUDORA\code` is its working copy. `docs/` moves into the repo at P0-01.
- **CI** (`.github/workflows/android.yml`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, Temurin 21): every push/PR runs `ktlintCheck detekt test assembleDebug` + a **16 KB alignment check** on all `.so` files, uploads the debug APK. Tag `v*` → signed `assembleRelease` → GitHub Release (`softprops/action-gh-release`), APK named `audora-vX.Y.Z-arm64.apk` for Obtainium.
- **Signing**: keystore generated once, kept in `D:\AUDORA\signing\` + two offline backups; CI gets it via secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- **Size**: arm64-v8a + one bundled English voice ≈ 50–80 MB. Other voices download from GitHub Releases / Hugging Face, pinned by SHA-256 in `models.json`.
- **Friends**: GitHub Releases + Obtainium. Expect a Play Protect prompt. Before India enforcement of developer verification (assume 2027), register the package name + signing key under a free **limited distribution** account (≤20 devices).
- **License**: GPL-3.0 (espeak-ng). Every bundled component must be GPL-compatible open source; no proprietary SDKs (D2).

## 8. Execution plan

See `TICKET-PLAN.md` for the phased tickets, dependencies and the recommended first ticket.

**Session rules (unchanged from v0.1, tightened):**
1. Plan mode first; paste the ticket ID + done-when; approve before edits.
2. Tests first for `:pdf` and `:speech`.
3. Finish with `./gradlew ktlintCheck detekt test`; CI green before merge.
4. One branch per ticket (`feat/P1-04-playback-service`), small PRs, reviewer subagent pass.
5. Update `docs/PROGRESS.md`; add/modify an ADR in `docs/adr/` if a decision changed.
6. Every PR lists what needs a real-device check.

**Phase 1 gate:** a 50-page English PDF plays for 60 minutes with the screen off on your phone with no stop; lock-screen controls work; resume after force-stop is exact; Android 17 hardening test (`adb shell cmd audio set-enable-hardening throw`) passes if your phone runs 17.

## 9. Risks and mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Render queue drains → player ENDED → wake lock released → silent stop | **High without C5** | Fails Phase 1 gate | Blocking `TtsDataSource`; own wake lock as backup; S3 proves it |
| OEM kills the FGS | Medium (Xiaomi, Oppo, Vivo) | Reading stops | Proper FGS, brand onboarding, unfinished-session detection, log process deaths |
| Android 17 mutes background audio | Medium | Silent playback | Start only from user action; FGS through buffering; hardening test in gate |
| Indic text in visual order / legacy fonts | High for older Hindi/Bengali PDFs | Garbled audio | Reorder pass, legacy converter, OCR fallback, "OCR" badge |
| Kokoro too slow | High on mid/low phones | Stalls | Offer only when measured RTF < 0.8; Piper/Kitten default |
| Open-source OCR weaker than ML Kit on Hindi | Medium | Some scanned pages read poorly | Tesseract `best` measured 3–4 % CER on Hindi scans in S2; optional PaddleOCR experiment in P3-05; legacy-font converter cuts OCR use |
| PdfiumAndroidKt lacks font-name API | Certain | No heading/legacy detection | Own JNI library over PdfiumAndroidKt's `libpdfium.so`, which exports every needed function (S2, ADR-011) |
| Developer verification reaches India | Likely 2027 | Friends can't install unverified APK easily | Limited distribution account; keep keystore safe |
| System voice pack missing (hi-IN/bn-IN) | Medium | Wrong/robotic voice | `isLanguageAvailable` + `KEY_FEATURE_NOT_INSTALLED` check; deep link to TTS settings |
| Model host changes / corrupt download | Low | Voice fails | SHA-256 pin, bundled fallback, mirror to own Release |
| Large PDFs (500+ pages) | Medium | OOM | Page-at-a-time extraction around the playhead, reopen the document every 100 pages, bounded caches, no page cap (S2: 1,323 pages at +18–45 MB) |
| Keystore lost | Low | Friends must reinstall; verification registration lost | Two offline backups |

## 10. Testing, diagnostics, maintainability

| Layer | What | Where |
|---|---|---|
| Unit | Normalizers, splitter, script router, reorder, legacy converter, header/footer detector, quality scorer, queue logic, resume re-anchoring | Agent + CI |
| Golden | Full `:pdf` pipeline vs expected scripts; score must not drop | Agent + CI |
| Integration | Room migrations, model manager (fake downloads), `TtsDataSource` with a fake engine (blocking/ready/failed) | CI |
| Instrumented | Service starts, notification appears, media buttons | CI emulator (optional) |
| Manual device | 60-min screen-off run, app switching, phone call, Bluetooth headset, force-stop resume, Android 17 hardening | You + 1–2 friends' phones per release |

**Diagnostics without telemetry:** rolling log (2 MB × 3), Settings → Share logs. Every failure is visible and specific ("Page 14 looks scanned — using OCR") plus a log line with book, page, engine, voice. No `!!` outside tests. ktlint + detekt in CI. Dependencies pinned; reviewed monthly.

## 11. Decision log (ADRs)

| ADR | Decision | Status |
|---|---|---|
| 001 | Native Kotlin + Compose | Accepted (v0.1) |
| 002 | sherpa-onnx as the single neural runtime | Accepted |
| 003 | **Revised:** ExoPlayer playlist fed by a blocking synthesis-backed `TtsDataSource` (was: WAV files appended) | Accepted (v1.0); S3 verifies |
| 004 | On-device only; no cloud, no paid services; AI cleanup dropped | Accepted (D6) |
| 005 | Script-based language routing | Accepted |
| 006 | GitHub Releases + Obtainium; limited-distribution verification before 2027 | Accepted (v1.0) |
| 007 | GPL-3.0 public repo; one APK; only GPL-compatible open-source components (no ML Kit) | Accepted (D1, D2) |
| 008 | PdfiumAndroidKt as primary extractor (+ font-info JNI) | Accepted; confirmed by S2 (ADR-011) |
| 009 | Resume by char-offset anchor + fingerprint + pipeline version | Accepted (v1.0) |
| 010 | compileSdk 37, targetSdk 36 | Accepted (P0-01) |
| 011 | S2: PDFium confirmed + JNI scope, `/ActualText` rebuild, OCR cross-check for Indic, Tesseract for all scripts, reopen every 100 pages | Accepted (S2) |
| 012 | S1: sherpa-onnx AAR vendored from its GitHub release (SHA-pinned), models mirrored to our own release, Kokoro v1.0, no Lessac-derived voices, English default rule, Moto Edge 50 Fusion as dev reference phone | Accepted (S1); English default pending phone run |
| 014 | Bundled English voice chosen in `speech/bundled-voices.json`, fetched at build time from sherpa-onnx's release (SHA-pinned), espeak-ng-data trimmed to en/hi/bn; Piper ljspeech provisional | Accepted (P1-03) |
| 015 | Resume anchor details: head + tail fingerprints (FNV-1a 64, normalized), no voice in the anchor, `resume_anchors` table (DB v2), brand battery steps as text with public intents only | Accepted (P1-06) |
| 016 | Render-ahead cache is an 8 MiB in-memory LRU (`QueueConfig.recentAudioBytes`) instead of a 20 MB disk LRU in `cacheDir/tts/` (§6) | Accepted (P1-04) |
| 017 | Golden corpus in `pdf/golden/`, scored on the JVM from PDFium page dumps; corpus score gated against `dev` in CI | Accepted (P2-01) |
| 018 | Layout: column-first XY-cut, paragraphs, headings by size + bold font name; `charStart` indexes the page's reading-order text; `PIPELINE_VERSION` 2 | Accepted (P2-02) |
| 019 | Speech normalization lives in `:speech` and runs at render time; `ReadingUnit.text` stays the extracted text (display, fingerprints) | Accepted (P2-04) |
| 020 | Clutter removal against neighbouring pages (window cached in `PdfBook`), margin bands on the text area, tables by aligned cell runs; repair keeps offsets mapped and ZWJ/ZWNJ; `PIPELINE_VERSION` 3 | Accepted (P2-03) |
| 021 | Page quality scorer and routes (`QualityConfig`); Indic repair and visual-order reorder per paragraph before text repair, decided per page; OCR sample compared by line-wise containment CER; `PdfBook.readPage` returns the route; `PIPELINE_VERSION` 4 | Accepted (P3-03) |
| 022 | Voice manager: catalog voices mirrored as reproducible zips on this repo's `voices` release, `models.json` packed into APK assets at build time, resumable WorkManager download with a final SHA-256 check, folder import of Piper/MMS/Kitten voices | Accepted (P4-01) |
| 023 | Legacy-font converter: Kruti Dev, DevLys, Chanakya and Bijoy tables chosen by font name, converted wherever read before the Indic repair; unknown-code rate checked first and routed to OCR; generated golden documents; `PIPELINE_VERSION` 5 | Accepted (P3-04) |
| 024 | Page view: pages drawn by a second, display-only PDFium document; glyph boxes mapped through PDFium's own page matrix; tap the nearest line to read; no `PdfRenderer` fallback | Accepted (P4-03) |
| 025 | OCR: Tesseract4Android (JitPack, AAR SHA-256 pinned), `tessdata_best` en/hi/bn pinned at an upstream commit and downloaded as one pack, disk cache per page, language probe, OCR text through the normal repair; PaddleOCR experiment not built; `PIPELINE_VERSION` 6 | Accepted (P3-05) |
| 026 | Script routing at render time: each script run of a sentence is read by its language's voice (≥3 English words to leave an Indic voice), parts joined into one clip; `ReadingUnit` and `:pdf` output unchanged | Accepted (P3-01) |
| 027 | Premium voices (Kokoro v1.0 en, Supertonic 3 hi) as catalog downloads; each voice's speed measured from the sentences it reads; render-ahead window in seconds from measured RTF, plus a start hold; voices measured at RTF ≥ 0.8 marked too slow | Accepted (P4-02) |
| 028 | Indic sentence rules after `BreakIterator` (danda, abbreviations, initials, closing quotes, verse numbers); NFC as the first text repair with offsets kept; "|" as danda; Hindi/Bengali numbers read as words at render time; `PIPELINE_VERSION` 7 | Accepted (P3-02) |
| 029 | Pauses (heading 600, paragraph 400, list item 300 ms) appended as silence to each sentence's clip; list items detected by layout; `ReadingUnit.kind`; English normalizer wired next to the Indic ones, headings read as headings; render speed measured without pauses; `PIPELINE_VERSION` 8 | Accepted (P2-04) |
| 030 | Hindi/Bengali catalog voices (pratham, priyamvada, Piper bn google, MMS ben); provisional defaults pratham and Piper google as a one-line `recommended` map; first download of a language becomes its reader; credits in the catalog; "Voices & licences" screen | Accepted (P3-06); defaults pending S0 + phone run |
| 031 | Debug APKs signed with one dev key restored from the repository secret `AUDORA_DEBUG_KEYSTORE_B64` (password `android`), APK certificate checked against it in CI; without the secret CI is unchanged | Accepted (CI) |
| 032 | Known gaps: Chanakya supplement glyphs by context; turned pages laid out in their reading frame; OCR via hOCR with word boxes mapped through the page transform (highlight), orientation by confidence, running headers on scans against neighbours' margin strips; citation switch; `PIPELINE_VERSION` 9 | Accepted |
| 035 | Speakability layer: `:pdf` tags tables, footnotes, captions, equations and asides (`UnitKind`) and scores each unit (`speakScore`, `SpeakabilityConfig`) instead of dropping them; `:playback` decides by setting, `:speech` voices; golden filter leaves out table rows; listening score gated in CI | Accepted (RI-1, RI-8a) |
| 053 | Bengali default becomes SYSPIN male (studio VITS, clearest by ASR round trip); SYSPIN female added; optional per-voice `threads` (4 for these) | Accepted (2026-10-23) |
| 055 | Battery onboarding opens Android's optimization list; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dropped for Google Play policy (amends ADR-015) | Accepted (2026-10-23) |
| 056 | Read each book to suit it (content intent, sentence delivery, page-cut sentences); one language pack per language and a simpler Play build | Accepted (2026-10-23) |
| 057 | Bengali emotion voices: AI4Bharat Rasa read in a style per sentence feeling (book, happy, sad, angry, scared, surprised, conversational), male speaker for characters | Accepted (2026-10-23) |
| 058 | Act only the quoted words, carry a sad or scared mood one sentence, pronunciation check and per-voice respellings | Accepted (2026-10-23) |
| 059 | Acted Hindi voice for studio mode only: Chatterbox Multilingual (MIT) with Turbo's one-step decoder, a native engine on sherpa-onnx's ONNX Runtime, downloaded on Wi-Fi, offered on phones with 8 GB of memory | Accepted (2026-10-23) |
| 060 | The app's name is ListenLock (label, strings, voice commands, export folder, public pages); package and repository keep "audora" | Accepted (2026-10-23) |
| 065 | The app icon is the designer's hand-drawn ListenLock logo (adaptive PNG on white, inside the safe zone, themed copy); debug uses it too; name and logo are not under the GPL | Accepted (2026-10-24) |
| 066 | Word, OpenDocument, RTF, web pages and Markdown are read as books (sniffed by content, pure Kotlin parsers); gallery pictures make a book through the OCR path; Read aloud from a text selection or the clipboard; the voice list is a radio group | Accepted (2026-10-24) |

## 12. AGENTS.md (to place at repo root in P0-01)

```markdown
# Audora — AGENTS.md

## What this is
Offline-first Android app that reads PDFs aloud like a human (English, Hindi, Bengali) with background playback. Personal use + friends, sideloaded APK.
Spec: docs/BUILD-SPEC.md · Tickets: docs/TICKET-PLAN.md · Progress: docs/PROGRESS.md · Decisions: docs/adr/

## Stack (change only via an ADR)
Kotlin 2.x, Compose, Hilt, Media3 1.11.x (ExoPlayer + MediaSessionService), sherpa-onnx 1.13.x (arm64-v8a), PdfiumAndroidKt, ICU BreakIterator, Room, DataStore, WorkManager (downloads only). Phase 3: Tesseract4Android (PaddleOCR ONNX only if P3-05 proves it). No proprietary SDKs (GPL-3.0).

## Modules and rules
- :core — domain model, Result/error types, config objects. No Android UI imports.
- :pdf — extract → quality → recover → layout → cleanup → ReadingUnit. Pure logic in plain Kotlin; Android adapters thin.
- :speech — normalization, script routing, SpeechEngine implementations, model manager.
- :playback — PlaybackService, TtsDataSource, render queue, progress. Synthesis runs HERE only.
- :app — Compose UI only; no business logic in composables.
Dependencies: app → playback → (speech, pdf) → core. Never the reverse.

## Invariants
- The player never reaches ENDED while text remains (TtsDataSource blocks instead).
- Playback starts only from a user action. No boot auto-start.
- Resume uses ResumeAnchor (page + charStart + fingerprint + pipelineVersion). Bump PIPELINE_VERSION when :pdf output can change.
- Never declare SMS, accessibility, or notification-listener permissions.

## Commands
- Fast check: ./gradlew ktlintCheck detekt test
- Golden quality: ./gradlew :pdf:goldenTest (writes docs/QUALITY.md)
- Debug APK: ./gradlew assembleDebug (CI builds signed release on tag v*)

## Conventions
- File header comment: path + one-line purpose.
- Errors: typed Result; never swallow; log with context (book, page, engine, voice).
- No magic numbers: tunables in config objects (LayoutConfig, QueueConfig, QualityConfig).
- Tests first for :pdf and :speech; table-driven tests for normalizers.
- No new dependency without asking. No network except model downloads. No cloud or paid services, ever.
- Never commit keystores, secrets, model binaries, or PDFs we can't redistribute.

## Definition of done
1. Ticket's done-when met. 2. ktlintCheck detekt test green. 3. Golden score not lower (if :pdf touched). 4. PROGRESS.md updated; ADR if a decision changed. 5. Real-device checks listed in the PR.
```

## 13. Sources
See `research/notes/01-offline-tts.md`, `02-pdf-extraction.md`, `03-playback-platform-distribution.md` (each with links), plus the v0.1 sources.
