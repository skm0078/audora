# ADR-067: One Jobs screen for every long job and download; pitch is a listening setting

**Status:** Accepted (2026-10-24).

## Context
Phone testing found two gaps. Long jobs (audiobook export, studio mode, accessible and talking-book copies) showed
their progress only in a notification and on the library's job cards, and downloads only on the screen that started
them, so there was no one place to see what was running, how far it was and how long it would take, or to stop it.
Tapping a job's notification opened whichever screen the app was last on. Separately, listeners could change speed
and pace but not pitch, which matters for voices that sound too high or too deep to some ears, and for children's
books.

## Decision
- **Jobs screen** (library top bar → Jobs, with a badge counting what is running; and every long job's
  notification). One list: long jobs first (`LongJobs`, the same cards as the library, with time left and Stop),
  then downloads. Downloads are read from WorkManager by their worker's class name, which WorkManager tags every
  request with, so downloads queued by an older version show too (`DownloadJobs.observe`). Voice, tutor and
  dictionary downloads also carry an item tag (`audora.download.item=<id or BCP 47 tag>`) so the row can say which
  voice or language it is; a download without one says "A voice". Cancel stops the work and keeps what was received,
  as each feature's own Cancel does, so downloading again resumes. A notification opens the screen through
  `OpenJobs` (its own PendingIntent request code, so it never rewrites the app's plain launch intent).
- **Pitch** (`SpeechStyle.pitch`, default 1.0): 0.8 to 1.25 in steps of 0.05 (`SpeechStyleConfig`), saved with the
  other speech settings, and shown in the speech sheet under speed as a slider with − and +, its value in words
  ("10% higher", "15% deeper") and a one-tap "Back to the voice's own".
  - **Applied after the voice, never inside it.** Playback sets `PlaybackParameters(speed, pitch)` on the player
    (Media3's Sonic). Files have no player of ours, so audiobook export, clips, the studio audio file and talking-book
    copies pass the voice's PCM through the same Sonic before the AAC encoder (`PitchedChapterWriter`). One stream per
    chapter, so sentence joins are shifted like the rest and nothing clicks; `finish` drains Sonic's tail so files
    keep their length.
  - **A pitch change restarts nothing.** `SpeechStyleConfig.asRendered` is the style without pitch; the service
    restarts the sentence only when that changes. Rendered sentences, the studio cache and `STUDIO_AUDIO_VERSION`
    are untouched by pitch.
  - **Resumable jobs** (export, talking-book staging) add the pitch to their key only when it is not 1.0, so a job
    left half done keeps its key and a resume at another pitch starts again instead of mixing two pitches in one book.
  - Pitch is one setting for all books in this change. A book's own pitch comes with the library schema change
    (the plan's second step), next to its own voice and speed.
- **No `:pdf` change:** PIPELINE_VERSION stays 12; corpus and listening scores are untouched.

## Alternatives considered
- **Render the voice at another pitch** (a model parameter): only some engines have one, it would invalidate every
  rendered sentence and the studio cache on each change, and it would not reach the system TTS voice.
- **A separate pitch library (SoundTouch, Rubber Band):** native code and size for what Media3's Sonic already does;
  the same processor in playback and files means a file sounds like listening.
- **Shift pitch with `setPlaybackSpeed` and resampling:** changes speed with pitch; listeners asked for one without the
  other.
- **Jobs from a database of our own:** WorkManager already persists every download with its state and progress;
  a copy would drift. Finished outputs (what was made, where it is) are stored in the library schema change.
- **Paging or a foreground service for the Jobs list:** the list is at most a few rows; a flow is enough.

## Consequences
- Pitch beyond about ±20% starts to sound processed, so the range stops there; widen it in `SpeechStyleConfig` if
  listeners ask.
- A saved file at another pitch costs some CPU per chapter (Sonic is light; it already runs during playback).
- The Jobs screen lists finished long jobs until dismissed, as the cards do; finished downloads leave the list.
- New strings in `strings_jobs.xml` and `strings_pitch.xml` (en, hi, bn).
- Tests: `SpeechStyleConfigTest` (pitch range, `asRendered`), `SpeechStyleStoreTest` (saved, old files read 1.0),
  `SpeechStyleChangeTest`, `ReaderModelTest`, `PitchKeyTest`, `DownloadJobsTest`, `AppDestinationTest`; on a device
  `PitchedChapterWriterDeviceTest` (length kept, frequency moved) and `JobsAndPitchScreensTest` (shows, applies at
  once, screenshots in the `player-screens` artifact).
