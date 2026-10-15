# PDF Voice Reader — Build Spec v0.1

Oct 14, 2026 · @Swarnakeshar Mukherjee

## Summary and feasibility

The app is buildable as a free, offline-first Android APK. The agent builds it one ticket at a time, and GitHub Actions produces the signed APK. No Android rule blocks any requirement: picking a start page, choosing a voice, and playing with the screen off or while you use other apps are all supported.

Three real limits shape the design. They don't block it.

- **Voice speed vs. quality.** Kokoro sounds best, but it runs slower than real time on budget phones. Piper runs in real time on any phone. Voices are therefore tiered, and on slower phones Kokoro renders ahead of playback.
- **Phone makers killing background apps.** Xiaomi, Huawei, Oppo, Vivo and some low-end Samsung phones can kill even a foreground media service. Mitigation is a battery-exemption prompt plus brand-specific setup steps. A small residual risk stays.
- **PDF text quality.** "Reading like a human" depends on extraction, not the voice. Digital English PDFs can reach about 95% natural reading. Scanned pages need OCR. Many Hindi and Bengali PDFs use legacy non-Unicode fonts, and their extracted text comes out garbled, so they go to OCR too.

Bengali has the weakest offline voice options. Its default is Google's free system voice, not a model the app ships.

## Scope

The MVP is English PDFs read aloud reliably in the background. Hindi, Bengali, OCR and premium voices come after the MVP is stable on real phones.

**MVP (Phases 1–2)**

- Open a PDF from the phone's file picker; library of recent books
- Start reading from any page, or tap a sentence to start there
- Voice picker: one bundled Piper English voice plus Android system voices
- Playback with the screen off and while other apps are open; lock-screen and notification controls; headphone buttons
- Speed 0.5x–3x, skip to the next or previous sentence, sleep timer
- Resume from the exact sentence for each book
- "Human reading" pipeline for digital PDFs: reading order, removal of headers, footers and page numbers, rejoining hyphenated words, number and abbreviation expansion, natural pauses

**v1 (Phases 3–4)**

- Hindi and Bengali, with the voice chosen automatically for each sentence's script
- OCR for scanned pages and garbled legacy-font pages (Devanagari via ML Kit, Bengali via Tesseract)
- Kokoro premium English voices, a voice manager for downloads and deletion, and a render-ahead mode
- Current sentence highlighted on the page
- Optional cleanup of messy pages by a hosted AI model. Off by default; it is the only feature that sends text off the phone.

**Non-goals**: iOS, a Play Store listing, user accounts, analytics, EPUB, voice cloning, and replacing Android's accessibility screen reader (TalkBack). The app reads documents; it does not narrate the whole phone screen.

## Voice strategy: English, Hindi, Bengali

English gets an offline neural voice by default. Hindi and Bengali default to Google's free system voices, because the open offline models for them are weaker. All neural models run through one runtime, sherpa-onnx (Apache-2.0).

| Language | Voice | Engine | Role | Quality | Download size | License / caveat |
| --- | --- | --- | --- | --- | --- | --- |
| English | Piper en\_US (lessac / amy, medium) | sherpa-onnx VITS | Default, bundled in the APK | Good, real-time on any phone | \~60 MB each | Per voice model card |
| English | Kokoro-82M (af\_heart, bf\_emma and others) | sherpa-onnx Kokoro | Premium, downloaded in the app | Best | \~80–100 MB int8, \~330 MB fp32 (confirm in S1) | Apache-2.0 |
| English | Android system TTS | `TextToSpeech` | Fallback, zero size | Good | 0 MB | Device voice |
| Hindi | Android system TTS (hi-IN) | `TextToSpeech` | Default | Good | Offline voice pack via phone settings | Device voice |
| Hindi | Piper hi\_IN (rohan / pratham / priyamvada) | sherpa-onnx VITS | Offline neural option | Fair–good | \~60 MB | Training-data license unverified |
| Hindi | Kokoro hf\_alpha / hm\_omega | sherpa-onnx Kokoro | Not offered by default | Low; the model card grades the Hindi voice C | Shared with English Kokoro | Apache-2.0 |
| Bengali | Android system TTS (bn-IN) | `TextToSpeech` | Default | Good | Offline voice pack via phone settings | Device voice |
| Bengali | Meta MMS-TTS `ben` | sherpa-onnx VITS | Offline neural fallback | Fair; 16 kHz, single speaker | \~110–140 MB | CC-BY-NC 4.0: fine for free personal sharing |
| Bengali | Third-party Piper `bn_BD` | sherpa-onnx VITS | Candidate only | Unknown | \~73 MB | Unofficial; evaluate in S1 before shipping |

**Picking the voice by script.** Every sentence is tagged by its Unicode script: Latin = English, Devanagari (U+0900–097F) = Hindi, Bengali (U+0980–09FF) = Bengali. Each language has its own selected voice. Script detection is deterministic, instant and has no false positives within this three-language scope, so it beats an ML language detector here. In the MVP the voice switches per sentence. In v1, English runs of three or more words inside a Hindi or Bengali sentence are split out and read in the English voice.

**Bring-your-own voice.** Advanced users can import any sherpa-onnx model folder (`.onnx` + `tokens.txt`). This keeps the voice list open without code changes.

## Architecture

The app is native Kotlin with Jetpack Compose, split into five Gradle modules. Speech synthesis and playback both live inside one foreground media service, so reading never depends on the screen being on.

&#91;embedded content: data flow · 3 core modules, 1 foreground service\]

Each sentence leaves `:pdf` as a `ReadingUnit`, becomes a WAV clip in `:speech`, and is queued and played by `:playback`. The queue asks `:speech` for clips ahead of time, so rendering continues while the screen is off.

**Stack**: Kotlin 2.x, Jetpack Compose, Media3 (ExoPlayer + MediaSessionService), sherpa-onnx (Kotlin API, arm64-v8a), PdfBox-Android (text with glyph positions), Android `PdfRenderer` (page display), ICU `BreakIterator` (sentence splitting), Room (books, progress), DataStore (settings), WorkManager (model downloads), Hilt (dependency injection), kotlinx.coroutines. Phase 3 adds ML Kit Text Recognition and Tesseract4Android.

| Module | Owns | Depends on |
| --- | --- | --- |
| `:app` | Compose UI (library, reader, voices, settings), navigation, DI wiring | all modules |
| `:core` | Domain model (`Book`, `ReadingUnit`, `VoiceId`, `Lang`), `Result` and error types, logging facade | — |
| `:pdf` | Extraction, layout analysis, cleanup, OCR fallback → a list of `ReadingUnit` per page | `:core` |
| `:speech` | Text normalization, script routing, the `SpeechEngine` interface with sherpa and system implementations, voice and model manager | `:core` |
| `:playback` | `PlaybackService` (MediaSessionService), sentence render-ahead queue, progress persistence | `:core`, `:speech`, `:pdf` |

The module boundaries match what the agent needs to work on in isolation. `:pdf` and `:speech` are pure Kotlin/JVM-testable logic, plus thin Android adapters. That makes them easy to cover with fast unit and golden tests.

```kotlin
// File: speech/src/main/kotlin/speech/SpeechEngine.kt
// Purpose: Single contract every TTS backend implements; playback never knows which engine it is using.
interface SpeechEngine {
    val id: EngineId
    fun supports(lang: Lang): Boolean
    fun voices(): List<VoiceInfo>
    /** Renders one sentence to a WAV file in cache. Must be cancellable and must not block the main thread. */
    suspend fun render(text: String, voice: VoiceId, speed: Float, out: File): RenderResult
    fun release()
}

// File: core/src/main/kotlin/core/ReadingUnit.kt
// Purpose: One speakable sentence with enough position data to resume and highlight.
data class ReadingUnit(
    val page: Int,
    val index: Int,            // sentence index within the page
    val text: String,          // normalized, speakable text
    val lang: Lang,
    val pauseAfterMs: Int,     // paragraph/heading pauses
    val bounds: List<RectF>?,  // glyph boxes for highlighting; null for OCR'd or reflowed text
)
```

## PDF to human-like reading pipeline

Natural reading comes from a deterministic text pipeline, checked by golden tests. The voice model contributes far less. Each page runs through these stages lazily, two pages ahead of playback, and results are cached per book.

1. **Extract** glyphs with positions, font size and font name (PdfBox-Android `TextPosition`).
2. **Check text quality.** Score the page: the share of characters in the expected Unicode blocks, invalid Indic sequences (for example a vowel sign with no consonant before it), and the empty-text ratio. A failing page is routed to OCR. This catches scanned pages and the legacy Hindi/Bengali fonts (Krutidev-style, Bijoy/SutonnyMJ) that extract as garbage.
3. **OCR fallback.** ML Kit Text Recognition v2 for Latin and Devanagari. Tesseract4Android with `ben.traineddata` for Bengali, since ML Kit has no Bengali model.
4. **Rebuild layout.**
   1. Group glyphs into lines by baseline.
   2. Detect columns by clustering the x-positions of line starts.
   3. Order the text column by column, top to bottom.
   4. Merge lines into paragraphs using line gaps and indents.
   5. Mark headings by font size and weight relative to the page's typical text.
5. **Remove clutter.** Drop lines that repeat in the top or bottom 8% of the page across three or more pages (headers, footers), standalone page numbers, and figure or table interiors. In-text citation markers like `[12]` and `(Smith, 2020)` are skipped, with a toggle in settings.
6. **Repair text.**
   - Rejoin words hyphenated across line breaks, and keep real hyphens.
   - Expand ligatures (ﬁ → fi).
   - Apply Unicode NFC normalization, which matters for Bengali vowel signs.
   - Fold soft hyphens and zero-width characters.
7. **Split sentences** with ICU `BreakIterator` for each locale. Handle `।` (dari) as the Hindi/Bengali sentence end. Protect abbreviations (Dr., e.g., Fig.) with a per-language list.
8. **Normalize for speech.**
   - Numbers, dates, currency (₹), percentages and ranges, including Devanagari and Bengali digits.
   - URLs and emails are shortened to "link".
   - Roman numerals in headings are spoken as numbers.
   - Each rule is a small pure function with its own tests.
9. **Prosody hints.** A pause after each heading (\~600 ms), between paragraphs (\~400 ms) and around list items. Headings and list markers are spoken naturally ("Chapter three.").

**Optional AI cleanup (v1, off by default).** For pages that still score poorly, the page text can be sent to a hosted model through OpenRouter or Groq, with the prompt "fix reading order and remove non-prose; do not add or rewrite words". The output is checked: it must contain at least 95% of the original words, or it is discarded. The settings screen labels this as the one feature that sends text off the phone.

**Golden corpus.** Keep about 25 PDFs in `testdata/pdfs/`: two-column papers, textbooks with headers, a scanned page, Hindi and Bengali Unicode PDFs, and one legacy-font Bengali PDF. Each has a hand-checked expected reading script in `testdata/expected/*.txt`. Every pipeline change must keep or improve the similarity score against these. This is the agent's main way to verify its own work.

## Playback and background audio

Each sentence is rendered to a short WAV file and queued into a stock ExoPlayer playlist inside a Media3 `MediaSessionService`. This reuses Media3's tested handling of the hard parts instead of rebuilding them on a raw `AudioTrack`: audio focus, wake locks, the notification, headphone buttons, and pausing when headphones are unplugged.

**Render-ahead queue**

- A coroutine in the service renders sentences into `cacheDir/tts/<book>/` and appends them to the playlist. It keeps a window of N sentences ahead: 3 for Piper and system TTS, 12 for Kokoro.
- If playback catches up with rendering, the player shows buffering, and the reader shows "preparing voice…". There is no silent stall.
- When the user seeks, changes speed or changes voice, the queue is cancelled from the current point and rebuilt. Rendered clips are deleted as they finish, so cache stays under about 20 MB.
- System TTS uses `synthesizeToFile`, so all three engines feed one identical path.

**Why files instead of streaming raw audio.** Clips of 100–300 KB cost little disk I/O. They make every engine look the same to the player and give free seeking between sentences. The cost is roughly 100–300 ms of first-sentence latency, which is acceptable for a reader.

**Staying alive with the screen off**

- Manifest: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions; the service declared with `foregroundServiceType="mediaPlayback"`; Media3 `MediaButtonReceiver`.
- `player.setWakeMode(C.WAKE_MODE_LOCAL)` keeps the CPU awake while playing or buffering, so rendering continues with the screen off. Verify this on a real device in S3.
- Request `POST_NOTIFICATIONS` (Android 13+) on first play, with a short explanation.
- On first launch, detect the brand (`Build.MANUFACTURER`). Show one screen with that brand's battery steps and a button that opens battery-optimization settings.

**State.** After each sentence starts, write (book, page, sentence index, voice) to Room. Resume is exact, survives the process being killed, and costs one small write every few seconds. Sleep timer options: 15, 30 and 60 minutes, or end of chapter or page.

## Build, signing and distribution

GitHub Actions is the build source of truth, so your own machine never has to run a full Android build. The agent writes code and runs fast unit tests; CI produces the APK.

- **Where the agent runs.** Either works: in the cloud against the GitHub repo, or locally. Unit and golden tests run on the JVM (`./gradlew :pdf:test :speech:test`) and are light. A full `assembleRelease` with native libraries is heavier, so leave it to CI if your machine struggles.
- **CI workflow** (`.github/workflows/android.yml`):
  - Every push and PR runs `./gradlew ktlintCheck detekt test assembleDebug` and uploads the debug APK as an artifact.
  - A pushed tag `v*` runs `assembleRelease` and attaches the signed APK to a GitHub Release.
- **Signing.** Generate a release keystore once, by hand. Store it as base64 in GitHub Secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Never commit it. If it is lost, friends must uninstall and reinstall.
- **APK size.** Ship `arm64-v8a` only, plus one bundled Piper English voice: about 70–80 MB. Every other voice downloads in the app from GitHub Releases or Hugging Face, checked against a pinned SHA-256 in `models.json`.
- **Friends installing.** Share the GitHub Releases link. Friends install [Obtainium](https://github.com/ImranR98/Obtainium) and point it at the repo for automatic updates. Expect a Play Protect warning on first install; they tap "Install anyway".
- **Repo visibility.** sherpa-onnx's Piper and Kokoro path uses espeak-ng data, which is GPL-3.0. Sharing the APK with friends then means making the source available, so the simplest choice is a public GPL-3.0 repo. A private repo is fine while it's only on your phone (see ADR-007).

## Agent execution plan

Work in five phases. Each ticket is one agent session and ends in one commit. A phase starts only after the previous phase's gate passes on your real phone. The three Phase 0 spikes are throwaway experiments that decide defaults before any real code exists.

**Session rules**

1. Start each ticket in plan mode. Paste the ticket ID and its "done when" line, and approve the plan before any edits.
2. Write the tests first, wherever the logic is pure: the `:pdf` and `:speech` modules.
3. Finish with `./gradlew ktlintCheck detekt test`. CI must be green before you merge.
4. Use one branch per ticket (`feat/P1-04-playback-service`) and keep PRs small. Review the diff yourself or with a reviewer subagent.
5. After every ticket, the agent updates `docs/PROGRESS.md` and, if a decision changed, `docs/adr/`.

| ID | Phase | Ticket | Done when |
| --- | --- | --- | --- |
| S1 | 0 Spikes | Voice benchmark app: Piper en/hi, Kokoro int8 en, MMS ben, candidate Piper bn via sherpa-onnx | Real-time factor and first-sentence latency logged on your phone and one friend's; voice samples saved for listening |
| S2 | 0 Spikes | Extraction quality script on 10 sample PDFs, including Hindi, Bengali and legacy-font files | A report per PDF: text quality score, column detection, garbage detection working |
| S3 | 0 Spikes | Minimal Media3 service plays a WAV playlist with the screen off for 30 minutes | Survives screen off and app switching on stock Android and on one Xiaomi or Samsung phone |
| P0-01 | 1 Foundation | Repo scaffold: 5 modules, Hilt, Compose, version catalog, ktlint, detekt | `./gradlew test assembleDebug` green locally and in CI |
| P0-02 | 1 Foundation | CI workflow + signed release on tag | Tag `v0.0.1` produces a signed APK on GitHub Releases |
| P1-01 | 1 MVP | Library screen: open via file picker (SAF), persist URI permission, recent books | Reopening the app lists books; a deleted file shows a clear error |
| P1-02 | 1 MVP | Basic extraction → `ReadingUnit` list (no layout heuristics yet) | Unit tests on 3 simple PDFs pass |
| P1-03 | 1 MVP | `SpeechEngine` + Piper (bundled) + system TTS implementations | Renders a sentence to WAV in both engines; cancellation tested |
| P1-04 | 1 MVP | `PlaybackService` + render-ahead queue + notification controls | Phase gate (below) |
| P1-05 | 1 MVP | Reader screen: page picker, play/pause, skip, speed, sleep timer | Start from page N works; speed change applies at the next sentence |
| P1-06 | 1 MVP | Exact resume in Room + brand-specific battery onboarding | Kill the app mid-sentence; reopening resumes at that sentence |
| P2-01 | 2 Reading | Golden corpus + similarity test harness | Baseline scores recorded in `docs/QUALITY.md` |
| P2-02 | 2 Reading | Lines, columns, reading order, headings | Two-column golden PDFs read in the correct order |
| P2-03 | 2 Reading | Header/footer/page-number removal, hyphen repair, ligatures | Golden score improves; no regressions |
| P2-04 | 2 Reading | English normalizer (numbers, dates, ₹, abbreviations, URLs) + prosody pauses | Table-driven tests of 100+ cases pass |
| P3-01 | 3 Indic | Script routing per sentence + per-language voice settings | A mixed English/Hindi PDF switches voices correctly |
| P3-02 | 3 Indic | Hindi and Bengali sentence splitting (dari), digits, NFC normalization | Indic golden PDFs pass |
| P3-03 | 3 Indic | Text quality scorer + OCR fallback (ML Kit Devanagari/Latin, Tesseract Bengali) | Scanned and legacy-font golden PDFs read intelligibly |
| P4-01 | 4 Premium | Voice manager: `models.json`, WorkManager download, SHA-256 check, delete, import custom model | An interrupted download resumes; a corrupt file is rejected |
| P4-02 | 4 Premium | Kokoro engine + render-ahead mode based on measured speed | No audible stalls on your phone at 1.0x |
| P4-03 | 4 Premium | Sentence highlight on the rendered page (glyph boxes) | Highlight tracks the audio within the current sentence |
| P4-04 | 4 Premium | Optional AI page cleanup (off by default) with the word-preservation check | A rewrite that drops words is rejected; the toggle is clearly labelled |
| P5-01 | 5 Release | In-app diagnostics: rolling log file + "share logs" button; no telemetry | A friend can send you a log file in two taps |
| P5-02 | 5 Release | README for friends, Obtainium setup, `v1.0.0` tag | A friend installs and updates without your help |

**Phase 1 gate.** A 50-page English PDF plays for 60 minutes with the screen off on your phone, with no stop. Lock-screen controls work. Resume after a force-stop is exact.

**Prompt template for each ticket**

```markdown
Ticket: P2-02 — Lines, columns, reading order, headings
Read AGENTS.md, docs/PROGRESS.md and docs/adr/ first.
Goal: <one line from the ticket table>
Done when: <done-when line>
Constraints: touch only :pdf (and its tests); no new dependencies without asking; keep public APIs in :core unchanged.
Process: plan mode → I approve → tests first → implement → ./gradlew ktlintCheck detekt :pdf:test → update docs/QUALITY.md with golden scores before/after → summarize risks.
```

## Risks and mitigations

The two risks most likely to hurt the experience are phone makers killing the app and garbled Indic text. Both get a dedicated spike before real code is written.

| Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- |
| Phone maker kills playback despite the foreground service | Medium (Xiaomi, Oppo, Vivo, low-end Samsung) | Reading stops silently | Battery-exemption onboarding by brand; on next launch, detect an unfinished session and offer "resume"; log process deaths |
| Hindi/Bengali PDFs use legacy fonts → garbage text | High for older Bengali documents | Unintelligible audio | Text quality scorer routes the page to OCR; reader shows "Scanned/OCR" for that page |
| Kokoro too slow on mid-range phones | Medium | Stalls or long waits | Measured speed decides the render-ahead window; Piper stays the default |
| System TTS voice pack not installed for hi-IN/bn-IN | Medium | Wrong or robotic voice | Check `isLanguageAvailable`; deep link to the TTS settings to install the pack |
| Reading-order mistakes on complex layouts | Medium | Confusing reading | Golden corpus regression tests; "skip paragraph" control; optional AI cleanup |
| Model download corrupted or the host changes | Low | Voice fails to load | SHA-256 pin; fallback to the bundled voice; you mirror the models to your own GitHub Release |
| Release keystore lost | Low | Friends can't update | Two offline backups of the keystore and passwords |
| PdfBox memory use on large PDFs | Medium | Crash on 500+ page books | Extract one page at a time; never load the whole document's text; cap the cache |
| Licenses: espeak-ng GPL-3.0, MMS CC-BY-NC | Certain | Constrains how it's shared | Public GPL-3.0 repo, free distribution only (ADR-007) |

## Testing, diagnostics and maintainability

Most correctness is verified on the JVM, where the agent can run tests itself. Only playback and battery behavior need your real phone, and that check is a short manual list per release.

| Layer | What | Runs where |
| --- | --- | --- |
| Unit | Normalizers, sentence splitter, script router, header/footer detector, quality scorer, queue logic | Agent + CI |
| Golden | Full `:pdf` pipeline on the corpus vs. expected scripts; score must not drop | Agent + CI |
| Integration | Room migrations, model manager (fake downloads), engine rendering via the Robolectric-safe interface | CI |
| Instrumented | Service starts, the notification appears, media buttons work | CI emulator (optional) |
| Manual device | 60-minute screen-off run, app switching, phone call interruption, Bluetooth headset, force-stop resume | You and 1–2 friends' phones per release |

**Diagnostics without telemetry.** A rolling log file (2 MB, last 3 files) records engine, voice, page, process start reason and errors with context. Settings → "Share logs" sends it through Android's share sheet. Errors are never swallowed: every engine or extraction failure becomes a visible, specific message ("Page 14 looks scanned — using OCR") plus a log line.

**Code hygiene.** ktlint + detekt in CI. No `!!` outside tests. Public functions in `:core` are documented. Every PR description says what was tested on a device, if anything. Dependencies are pinned in `libs.versions.toml`, and updates are reviewed monthly.

## AGENTS.md, decisions and open questions

Copy this into the repo root as `AGENTS.md` before the first session. It is the standing context the agent reads every time.

```markdown
# PDF Voice Reader — AGENTS.md

## What this is
Offline-first Android app that reads PDFs aloud like a human, in English, Hindi and Bengali, with background playback. Personal use + friends, sideloaded APK. Spec: docs/SPEC.md. Progress: docs/PROGRESS.md. Decisions: docs/adr/.

## Stack (do not change without an ADR)
Kotlin 2.x, Jetpack Compose, Hilt, Media3 (ExoPlayer + MediaSessionService), sherpa-onnx (arm64-v8a), PdfBox-Android, Android PdfRenderer, ICU BreakIterator, Room, DataStore, WorkManager. Phase 3: ML Kit Text Recognition v2, Tesseract4Android.

## Modules and rules
- :core — domain model + error types. No Android UI imports.
- :pdf — extraction → layout → cleanup → ReadingUnit. Pure logic in plain Kotlin classes; Android adapters thin.
- :speech — normalization, script routing, SpeechEngine implementations, model manager.
- :playback — PlaybackService, render-ahead queue, progress persistence. Synthesis runs HERE, never in an Activity/ViewModel.
- :app — Compose UI only; no business logic in composables.
Dependencies flow app → playback → (speech, pdf) → core. Never the reverse.

## Commands
- Fast check: ./gradlew ktlintCheck detekt test
- Golden quality: ./gradlew :pdf:goldenTest (writes docs/QUALITY.md)
- Debug APK: ./gradlew assembleDebug (CI builds release on tag v*)

## Conventions
- File header comment: path + one-line purpose.
- Errors: return typed Result/failures; never swallow; log with context (book, page, engine, voice).
- No magic numbers: tunables live in config objects (e.g., LayoutConfig, QueueConfig).
- Tests first for :pdf and :speech. Table-driven tests for normalizers.
- No new dependency without asking. No network calls except model downloads and the opt-in AI cleanup.
- Never commit keystores, .jks, secrets, or model binaries (models come from models.json downloads).

## Definition of done (every ticket)
1. Done-when criterion from the ticket met. 2. ./gradlew ktlintCheck detekt test green. 3. Golden score not lower (if :pdf touched). 4. docs/PROGRESS.md updated; ADR added if a decision changed. 5. Note anything that needs a real-device check.
```

**Decision log**

| ADR | Decision | Why | Alternative rejected |
| --- | --- | --- | --- |
| 001 | Native Kotlin + Compose | Every hard part (Media3 service, native TTS, PDF glyph positions) is native; fewer bridges to debug | Flutter: extra plugin layer exactly where reliability matters |
| 002 | sherpa-onnx as the single neural TTS runtime | One API for Piper, Kokoro and MMS; Kotlin bindings; Android TTS engine examples to learn from | Separate runtimes per model |
| 003 | Sentence WAV clips in an ExoPlayer playlist | Reuses Media3's focus, wake lock and notification handling; one path for all engines | Custom `AudioTrack` streaming player: lower latency, much more code to get right |
| 004 | Offline-first; AI cleanup opt-in only | Privacy, zero cost, works without internet | Cloud TTS or LLM by default |
| 005 | Script-based language routing | Deterministic and instant for three distinct scripts | ML language identification |
| 006 | GitHub Releases + Obtainium distribution | Free, automatic updates, no store review | Play Store: listing overhead, not needed for friends |
| 007 | Public GPL-3.0 repo if shared beyond you | espeak-ng data is GPL-3.0; MMS Bengali is non-commercial | Private repo + sharing the APK: license conflict |

**Open questions**

- [ ] Which phones do you and your friends use (brand, rough tier)? This sets Kokoro's default role and which battery guides ship first.
- [ ] Public repo now, or private until you share it?
- [ ] Is the AI cleanup feature wanted at all in v1, given it is the only online feature?

## Sources

- [sherpa-onnx TTS documentation](https://k2-fsa.github.io/sherpa/onnx/tts)
- [VoxSherpa TTS](https://github.com/CodeBySonu95/VoxSherpa-TTS) — reference for running Kokoro and Piper on Android; reports slow Kokoro on budget phones
- [Media3 background playback](https://developer.android.com/media/media3/session/background-playback)
- [Don't kill my app](https://dontkillmyapp.com/) — phone-maker background restrictions
- [Meta MMS-TTS Bengali](https://huggingface.co/facebook/mms-tts-ben) — CC-BY-NC 4.0
- [sherpa-onnx MMS conversion](https://k2-fsa.github.io/sherpa/onnx/tts/mms.html)
- [Kokoro-82M voice grades](https://replicate.com/alphanumericuser/kokoro-82m/readme) — Hindi voice graded C
- [Piper Hindi voice in a sherpa-onnx bundle](https://huggingface.co/Kg1729/voiceai-android-models) — notes the unverified training-data license
