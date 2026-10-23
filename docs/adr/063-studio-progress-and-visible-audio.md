# ADR-063: Studio mode shows its speed, time left and audio; a new voice applies at once

**Status:** Accepted (2026-10-23). Amends ADR-039 (studio mode) and P4-01 (voice choice).

## Context
On the Moto, studio mode on an English book showed "0 h 00 min of 6 h 00 min ready" with a bar at 0 % for a long
time, and the listener took it for stuck. Root causes, from the code:
- Progress moved only when a whole page was saved, in whole minutes, against a fixed 6 h target: 1 % is 3.6 minutes of
  speech, so the first page or two showed nothing at all. The "running" notice appeared only after the first page.
- No estimate of the time left, and no reason for the speed: an English book reads with the listener's own voice
  (the 2.9 GB acted voice is Hindi only), which on this phone may be faster or slower than speech.
- The audio sits in `noBackupFilesDir/studio`, inside the app. Nothing in the app said so, how it plays (by itself,
  when the book plays with the same voice and speed), how big it is, or how to remove or keep it.
- Separately: a voice picked in Settings → Voices took effect only on sentences not yet rendered. The render-ahead
  window could hold minutes of the old voice, so it seemed not to switch until some other setting restarted reading.
  The reader's own picker kept its choice only for the playback service's life and then outranked Settings.

## Decision
1. `StudioRun` reports `StudioProgress` after every sentence and page: speech ready (the current page included), the
   page, and the speech it rendered against the time the voice took (charger and audio waits left out).
2. The time left is `speech still to make × measured time per second of speech`. Speech still to make is up to the
   target, or to the book's end when that is nearer, judged from speech per finished page. Nothing is promised until
   the voice has rendered for `StudioConfig.measureMs` (20 s; model loading would skew it). The notification and the
   library card show it with the page and the reason: "1 min of speech takes 45 s to make on this phone with this
   voice" and whether the voice keeps up live (then studio mode mainly saves battery) or not (then it is what lets the
   voice play without pauses). Updates are throttled to `progressEveryMs` (2 s), plus every page and wait change.
3. A per-book Studio mode dialog (book menu, or Details on the studio card) replaces the bare "Prepare" action: what it
   does, live progress, what is ready (length, pages, size; `StudioStore.bookAudio` reads only clip headers), where it
   is kept and what deletes it, and Listen, Save as audio file, Delete (asked once more, since it took hours), Start or
   Prepare more, Stop, Start now.
4. Save as audio file writes the ready pages as one `.m4a` in `Audiobooks/<title>/` with the audiobook export's encoder
   and folder (`StudioAudioFile`), resampling clips to the first clip's rate. Nothing is rendered.
5. The playback service follows `ReadingVoices.choices`: a change (Settings → Voices, the reader's picker, a language
   pack's first voice) restarts the sentence playing, so the rendered-ahead sentences are redone, playing or paused.
   The reader's picker for a book without its own voices now saves to Settings → Voices; the session-only choice is gone.

## Alternatives considered
- **A smaller target (1–2 h).** Shows "done" sooner but renders less per night; the honest time left solves the
  confusion without cutting what a night on the charger produces.
- **ETA from pages.** Pages vary from a heading to a dense column; speech per page is used only to find the book's end.
- **Make studio audio a public folder.** It is lossless (60–80 MB an hour) and keyed to settings; a deliberate save to
  `.m4a` is smaller and plays anywhere.
- **Undo instead of a delete confirmation.** Undo would mean keeping the files until a timeout; the second tap is kept
  because the loss is hours of rendering.

## Consequences
- The progress line is longer (status, page, speed); the library card shows the same text.
- `VoiceChoice` lost its session level; `SpeechVoices.select` became `choose`, which persists.
- Not addressed here: the citation-marker setting still applies when a book is next opened (offsets change, and the
  reader and playback must reopen together). Listed in the options audit.
