# ADR-034: Offline audiobook export, one AAC file per chapter

**Status:** Accepted (2026-10-17). Engine 2026-10-16; entry point, service and notifications 2026-10-17; resume,
replace and passwords 2026-10-17 (section 9).

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
6. **Entry point and runtime.** "Save as audiobook" in a library book's menu starts `AudiobookExportService`, a
   foreground service (type mediaProcessing on Android 15+, dataSync before) with its own thread, its own voice
   engines, a partial wake lock capped at 6 hours, and a notification with progress and Cancel. One export at a time;
   never restarted by the system (C7). Android 15's daily 6-hour limit for these jobs cancels it like Cancel does.
7. **Where files go.** MediaStore `Audiobooks/<title>/` on shared storage, so other apps can play them, with no
   storage permission (API 29+ lets an app create its own media). Each file stays pending, hidden from other apps,
   until it is complete. The title is cleaned for file systems (unsafe characters, leading dots, 80 characters).
8. **Rate and pace.** Files use the listener's pace (ADR-033) at the voice's own rate: audiobook players have their
   own speed control, and a faster model rate costs clarity.

9. **Resume, replace and passwords** (added with studio mode and the copies, ADR-039 and ADR-040).
   - *A record per book* (`ExportRecords`, `filesDir/audiobook-exports/<hash of book id>.txt`) lists the chapter files
     written so far: number, duration, saved name and MediaStore URI, under a key hashed from the rendering signature
     (style at the voice's rate, voices, :pdf pipeline, punctuation model, app build; ADR-039's), the folder and the
     chapter plan. Each finished chapter is added at once, replacing the file whole (temp file, then rename). No
     text and no title is stored.
   - *Going on* (`ExportSession`): an export of the same book with the same key that did not finish (Cancel, Android's
     limit, a crash, a failure) renders only the chapters whose files are missing. Android's 6-hour limit now shows
     "paused" with **Continue**, which opens Audora (resetting the limit) and starts the export again; after Cancel,
     saving the book again goes on the same way.
   - *Replacing* (closes the re-saving gap): another key (new voice, style, title, chapters or app build), or saving
     a finished export again, deletes the earlier files first, so the folder holds one audiobook, not "(1)" copies.
     File names reported in the notification are the names shared storage actually gave (queried `DISPLAY_NAME`).
     Files an earlier install of Audora wrote belong to no app it can speak for; their deletion fails (logged) and
     they stay.
   - *Passwords* (closes the password gap): "Save as audiobook" in the reader's ⋮ menu passes the password the book
     was opened with through `PasswordHandoff`: in memory only, never in an Intent, on disk or in the log, taken
     once by the service and expiring after a minute (removed at once if the service cannot start). From the
     library, and after Continue, there is no password: a protected book is refused with "needs its password", and
     opening it in the reader and saving from there goes on from the saved chapters.
   - *Wake lock*: a 30-minute timeout renewed on every finished page (`AudiobookConfig.wakeLockMs`) instead of one
     6-hour hold, so a stuck export lets go within 30 minutes.

## Alternatives considered
- **One .m4b with chapter marks:** the single-file audiobook format, but `MediaMuxer` cannot write chapter atoms;
  it would need FFmpeg or a hand-written MP4 box writer. A folder of files works in every player today. Revisit if
  friends ask for single files.
- **Opus in Ogg:** smaller at the same quality and available on minSdk 31, but car stereos and older audiobook
  players handle it less reliably than AAC in MP4, which plays everywhere, including Media3.
- **WAV:** no encoder needed, but about 150 MB per hour of 22 kHz speech. Rejected.
- **WorkManager job:** AGENTS.md keeps WorkManager for downloads; a long user-started render is a foreground
  service's job, like playback.
- **Keeping the password for Continue** (encrypted in the keystore): would let Continue and the library open a
  protected book, but puts the password on disk, which nothing else in Audora does. Rejected; the reader route
  covers it.
- **Asking before replacing**: a dialog on every re-save, for a folder the listener asked to have again. Rejected;
  the notification names the files.

## Consequences
- Rendering a book takes about as long as the voice's render speed allows (Piper is several times faster than
  real time on the test phone; Kokoro is not, and should be warned about before a long export).
- Exporting while listening loads a second copy of the voice model (separate engines, no shared state); fine for
  Piper, to be watched with Kokoro on the test phone.
- Opening the book for export marks it opened in the library, like opening it in the reader.
- A resumed chapter sequence starts quote voices (`QuoteCarry`) fresh at the first chapter rendered; a quote
  running across a chapter boundary at that point is read in the narrator's voice.
- Open: chapter titles from the outline in file names and MP4 metadata, and a single .m4b.
