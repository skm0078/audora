# ADR-034: Offline audiobook export, one AAC file per chapter

**Status:** Proposed (2026-10-16). The export engine and its tests are in; the Reader/Library entry point and the
foreground service that runs it are the next step, and the status moves to Accepted when they ship.

## Context
Paid PDF readers (Speechify, ElevenReader, Voice Dream) sell "download as audio" in their premium tiers. Audora
already renders every sentence offline for playback, so it can produce the same files at no cost, from the same
voices the maintainer picked, for listening in any audiobook app, in the car, or on a phone without Audora.

The idea came from looking at rea (github.com/morluto/rea, MIT), a PC-side reverse-engineering toolkit for AI
agents. rea runs on the developer's computer, not in the app, so it adds no dependency here. Within D8 it is useful
for our own APK and for adb captures during the background-audio runs; paid apps are only observed from outside,
which is how this feature was scoped. Nothing in this ADR was taken from another app's code or assets.

## Decision
1. **Same renderer as playback.** `AudiobookExporter` (`:playback`, `playback.export`) reads pages through
   `BookText` and sentences through `SentenceRenderer` (`EngineRenderer` in the app). Files sound exactly like
   in-app listening: per-language voices (ADR-026), normalizers (ADR-019), pauses (ADR-029). Synthesis stays in
   `:playback`, as AGENTS.md requires.
2. **One file per chapter.** `AudioChapters.plan` uses `BookText.chapterStarts()` (the outline, as the sleep timer
   does); pages before the first chapter become their own file, and a book without an outline is cut into parts of
   `AudiobookConfig.pagesPerPartWithoutChapters` pages. Files are named `NN Pages a-b.m4a`, zero-padded so every
   player sorts them; a folder of such files is how audiobook players model a book with chapters.
3. **Platform encoder.** AAC-LC mono through `MediaCodec` into `MediaMuxer` (.m4a), 64 kbit/s by default (about
   29 MB per hour). No new dependency, no native library of ours, nothing to 16 KB-align.
4. **Playback's failure rules.** A failing sentence is skipped; `renderRetryLimit` failures in a row stop the export
   (a broken voice must not write hours of silence). An unextractable page is skipped; a book that can no longer be
   opened stops it. Interrupting the worker thread cancels: the chapter in progress is deleted, finished chapters
   stay. A chapter with nothing to read writes no file.
5. **One sample rate per file**, set by its first sentence; later sentences are resampled to it with
   `PcmAudio.resampled` (linear). Mixed-rate chapters are rare (a second language's voice) and short.

## Alternatives considered
- **One .m4b with chapter marks:** the single-file audiobook format, but `MediaMuxer` cannot write chapter atoms;
  it would need FFmpeg or a hand-written MP4 box writer. A folder of files works in every player today. Revisit if
  friends ask for single files.
- **Opus in Ogg:** smaller at the same quality and available on minSdk 31, but car stereos and older audiobook
  players handle it less reliably than AAC in MP4, which plays everywhere, including Media3.
- **WAV:** no encoder needed, but about 150 MB per hour of 22 kHz speech. Rejected.
- **WorkManager job:** AGENTS.md keeps WorkManager for downloads; a long user-started render is a foreground
  service's job, like playback.

## Consequences
- Rendering a book takes about as long as the voice's render speed allows (Piper is several times faster than
  real time on the Moto; Kokoro is not, and should be warned about before a long export).
- Exporting while listening would share the engines with playback; the entry point will pause playback or refuse
  until that is measured.
- Open: the output folder (app-specific Music folder first, a user-picked SAF folder later), a progress
  notification, and chapter titles from the outline in file names and MP4 metadata.
