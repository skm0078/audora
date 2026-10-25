# ListenLock docs

Every document in the repository, in one list. Plans, trackers and guides that live outside the repository are
collected on the project's private docs hub; this file is its repository half.

When you add a document or an ADR, add a line here in the same change.

## Start here

| Document | What it is for |
| --- | --- |
| [../README.md](../README.md) | What the app does, privacy, voice credits, roadmap |
| [../AGENTS.md](../AGENTS.md) | Rules for every change: stack, module boundaries, definition of done |
| [PROGRESS.md](PROGRESS.md) | One entry per merged change, newest first, with the real-device checks each still needs |

## Product and plan

| Document | What it is for |
| --- | --- |
| [BUILD-SPEC.md](BUILD-SPEC.md) | The locked Build Spec v1.0: architecture, pipeline, voices, quality gates, ADR-001 to ADR-009 (§11) |
| [TICKET-PLAN.md](TICKET-PLAN.md) | Phases and tickets, with each ticket's done-when |
| [DECISIONS-OPEN.md](DECISIONS-OPEN.md) | Decisions D1 to D8 and how each was settled |
| [archive/Build-Spec-v0.1.md](archive/Build-Spec-v0.1.md) | The first draft of the spec, kept for history |

## Quality (generated, do not edit)

| Document | What it is for |
| --- | --- |
| [QUALITY.md](QUALITY.md) | Golden corpus score for the `:pdf` pipeline; CI fails if it drops |
| [LISTENING.md](LISTENING.md) | Listening score (what the voice is given vs what a careful reader says); CI fails if it drops |

## Release and public pages

| Document | What it is for |
| --- | --- |
| [PERFORMANCE.md](PERFORMANCE.md) | How speed is measured: CI's debug-vs-dev timing, Share logs render lines, System Tracing and adb on the phone |
| [RELEASING.md](RELEASING.md) | Signing keys, cutting a release from `prod`, Obtainium, Google Play, Data safety answers |
| [index.html](index.html) | Public home page, English, Hindi and Bengali (GitHub Pages, served from `prod` `/docs`); no trackers or external requests |
| [assets/](assets/) | The home page's stylesheet, Hindi and Bengali text (`i18n.js`), web-sized logo and icons |
| [privacy.html](privacy.html) | Public privacy policy (the URL Play links to) |
| [store/](store/) | Play icon and feature graphic (SVG sources and PNGs) |

## Research in the repository

| Document | What it is for |
| --- | --- |
| [../research/notes/](../research/notes/) | Early research: offline TTS, PDF extraction, playback and distribution, outside-view benchmark |
| [../research/spikes/S1-voice-benchmark/REPORT.md](../research/spikes/S1-voice-benchmark/REPORT.md) | S1: voice speed and quality on desktop and the test phone |
| [../research/spikes/S2-pdf-extraction/REPORT.md](../research/spikes/S2-pdf-extraction/REPORT.md) | S2: PDF text extraction and OCR across the test corpus |
| [../research/spikes/S3-background-playback/REPORT.md](../research/spikes/S3-background-playback/REPORT.md) | S3: screen-off background playback |
| [../research/spikes/indic-voices/README.md](../research/spikes/indic-voices/README.md) | Hindi and Bengali voice candidates and how they were exported |
| [../research/pronunciation/README.md](../research/pronunciation/README.md) | Pronunciation check corpus |
| [../research/voice-samples/README.md](../research/voice-samples/README.md) | Desktop voice samples |

## Decisions (ADRs)

How to write one: [adr/README.md](adr/README.md). ADR-001 to ADR-009 are in the Build Spec, §11.
ADR-013 and ADR-037 were planned (podcast mode, online AI) and have no file yet.

**Platform, build and release**

- [ADR-010](adr/010-compile-sdk-37.md) compileSdk 37, targetSdk stays 36
- [ADR-014](adr/014-bundled-voice-build.md) Bundled English voice fetched at build time, SHA-pinned
- [ADR-031](adr/031-stable-debug-signing-key.md) One signing key for dev builds
- [ADR-055](adr/055-battery-list-not-exemption-prompt.md) Battery onboarding opens Android's list, not the exemption prompt
- [ADR-056](adr/056-read-to-suit-the-book.md) Read each book to suit it, and a simpler Play build
- [ADR-060](adr/060-app-name-listenlock.md) The app's name is ListenLock
- [ADR-061](adr/061-privacy-and-app-lock.md) What leaves the phone, and an optional app lock
- [ADR-065](adr/065-listenlock-logo.md) The app icon is the hand-drawn ListenLock logo (adaptive PNG on white, themed copy; name and logo not under the GPL)
- [ADR-066](adr/066-more-book-formats-and-voice-choice.md) Word, ODT, RTF, web pages, Markdown and gallery pictures are books; Read aloud from a selection; the voice list shows the choice
- [ADR-067](adr/067-jobs-screen-and-pitch.md) One Jobs screen for every long job and download (with Cancel); pitch is a listening setting applied after the voice, in playback and saved files
- [ADR-068](adr/068-library-outputs-and-book-details.md) What the app made (audiobooks, studio files, copies) is listed per book with Play, Share and Delete and an in-app player; books carry author, language, format and a chosen cover, filled from the file and editable; pitch per book
- [ADR-069](adr/069-library-catalogue.md) Collections, filters by author, file type, language and collection, author search and sort; tuned for low-end phones (search keys folded once, debounced search, cover cache sized by memory, 16-bit covers on low-RAM phones)
- [ADR-070](adr/070-small-touches-testers-notice.md) Cover, author and chapter on the notification and lock screen, app icon shortcuts, What's new after an update, mark finished or not started, copy and share a sentence, a sample book, haptic ticks
- [ADR-071](adr/071-pitch-presets-and-option-rows.md) Pitch presets like speed, heard while dragging, ±15%; option rows never squeeze their titles
- [ADR-072](adr/072-optimised-dev-build.md) The phones run an optimised dev build (R8, not debuggable, -O2 native); R8 in release; speed measured on every push

**Reading the PDF (`:pdf`)**

- [ADR-011](adr/011-s2-extraction-and-ocr.md) S2 outcome: PDFium, Indic text cross-checked by OCR, Tesseract
- [ADR-017](adr/017-golden-corpus.md) The golden corpus gates CI
- [ADR-018](adr/018-layout-and-reading-text-offsets.md) Layout rebuilds reading order; `charStart` offsets
- [ADR-020](adr/020-clutter-removal-and-text-repair.md) Clutter removal and text repair
- [ADR-021](adr/021-indic-quality-routing.md) Page quality routing and Indic repair
- [ADR-023](adr/023-legacy-font-converter.md) Legacy-font converter
- [ADR-025](adr/025-tesseract-ocr.md) OCR with Tesseract4Android
- [ADR-032](adr/032-known-gaps-legacy-turns-and-ocr-pages.md) Known gaps after Phase 3
- [ADR-054](adr/054-never-block-on-a-scanned-page.md) A scanned page never blocks the listener

**Speech and voices (`:speech`)**

- [ADR-012](adr/012-s1-voice-benchmark.md) S1 outcome: sherpa-onnx, voice candidates, reference phone
- [ADR-019](adr/019-normalize-at-render-time.md) Normalization at render time
- [ADR-022](adr/022-voice-manager.md) Voice manager and downloads
- [ADR-026](adr/026-script-routing-at-render-time.md) Script routing at render time
- [ADR-027](adr/027-premium-voices-and-measured-render-ahead.md) Premium voices and measured render-ahead
- [ADR-028](adr/028-indic-sentences-nfc-and-numbers.md) Indic sentences, NFC and numbers
- [ADR-029](adr/029-prosody-pauses-and-normalizer-wiring.md) Pauses and normalizer wiring
- [ADR-030](adr/030-indic-voices-and-licences.md) Hindi and Bengali voices and the licences screen
- [ADR-033](adr/033-speech-speed-pace-and-app-basics.md) Speech speed, pace and app basics
- [ADR-035](adr/035-speakability-layer.md) Speakability layer
- [ADR-049](adr/049-smart-speed.md) Smart speed
- [ADR-052](adr/052-bundled-kitten-micro-male.md) Built-in English voice: KittenTTS micro
- [ADR-053](adr/053-bengali-studio-voice.md) Bengali default voice: SYSPIN
- [ADR-057](adr/057-bengali-emotion-voices.md) Bengali emotion voices (Rasa)
- [ADR-058](adr/058-act-the-quotes-keep-the-mood.md) Act the quoted words, keep the mood
- [ADR-059](adr/059-acted-hindi-studio-voice.md) Acted Hindi voice for studio mode (Chatterbox)

**Playback, resume and studio (`:playback`)**

- [ADR-015](adr/015-resume-anchor-details.md) Resume anchor details
- [ADR-016](adr/016-render-cache-in-memory.md) Render cache in memory
- [ADR-034](adr/034-offline-audiobook-export.md) Offline audiobook export
- [ADR-039](adr/039-studio-mode.md) Studio mode
- [ADR-051](adr/051-screen-off-voice-commands.md) Screen-off voice commands
- [ADR-063](adr/063-studio-progress-and-visible-audio.md) Studio progress and visible audio; new voice applies at once
- [ADR-064](adr/064-settings-apply-at-once.md) Studio audio survives updates; settings apply at once

**App features and screens (`:app`)**

- [ADR-024](adr/024-page-view-and-highlight.md) Page view and highlight
- [ADR-036](adr/036-ui-shell-theme-and-reading-views.md) App shell, theme and reading views
- [ADR-038](adr/038-clean-room-study-of-closed-apps.md) Clean-room study of closed listening apps
- [ADR-040](adr/040-accessible-copy-and-talking-book.md) Accessible copy and talking-book EPUB
- [ADR-041](adr/041-highlights-voice-notes-and-search.md) Highlights, voice notes and search
- [ADR-042](adr/042-epub-text-books-and-shared-text.md) EPUB, text books and shared text
- [ADR-043](adr/043-point-and-read.md) Point and read
- [ADR-044](adr/044-watched-folders.md) Watched folders
- [ADR-045](adr/045-offline-dictionary.md) Offline dictionary
- [ADR-046](adr/046-backup-and-restore.md) Backup and restore
- [ADR-047](adr/047-hindi-bengali-dictionaries.md) Hindi and Bengali dictionaries
- [ADR-048](adr/048-quiz-me.md) Quiz me
- [ADR-050](adr/050-reading-tutor.md) Reading tutor
- [ADR-062](adr/062-options-that-look-like-controls.md) Options that look like controls
