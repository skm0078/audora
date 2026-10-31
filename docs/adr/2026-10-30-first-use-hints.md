# ADR 2026-10-30-first-use-hints: One small tip when a feature would help, once, and a switch to mute them

**Status:** Accepted (2026-10-30). Seventh of the findability PRs from the maintainer-approved audit ("First-use
hints, shown when they matter"). Builds on ADR 2026-10-30-player-line-actions (the reader's notice slot and its
gesture tips card) and ADR 2026-10-29-find-player-options (`SpeechRequest` opens a group of the Voice and speed sheet).

## Context

Most of ListenLock's best features (smart speed, Cast, Quiz me, studio mode, keeping a Drive book on the phone, adding
books from photos or the web) are only found by someone who goes looking. The audit asked for a hint at the moment
each one would help, never more than one at a time, and never in the way of reading.

## Decision

- **Seven hints, each shown once** (`core.hints.FirstUseHint`), in this priority when several apply:
  1. A book read through another app (Drive and other cloud providers): "Keep a copy" copies it to the phone.
  2. Speed above the voice's clear top speed (`SpeechStyleConfig.maxModelRate`) without smart speed: Show me opens
     Sound in the Voice and speed sheet.
  3. A story with no dialogue or character voices set: Show me opens Cast.
  4. The book has a highlight: "Highlights turn into quiz questions", Show me opens Quiz me in the library.
  5. The last page of a book: Show me opens Your listening (stats); the page's Share button is already in sight.
  6. A long book (`FirstUseHintsConfig.longBookPages`, 100 pages) a quarter to three quarters read while charging:
     "Start studio mode" prepares the rest now.
  7. The third app start that opens the library with one book: Show me opens Add book.
- **At most one per app start**, and a hint already showing stays until answered or no longer true
  (`pickHint`). Got it or the action marks it seen for good.
- **Where:** in the reader's notice slot, after the page-problem, voice-pack and gesture-tips cards, drawn above the
  play controls (never over Play), with the Voice and speed shortcuts hidden while it shows, as the tips do. The
  library hint sits under the library's banner. None in kids mode.
- **Mute:** Settings › Advanced › Help › "Tips as you go" (on by default; search finds it by "tips" or "hints").
- **Saved** in its own SharedPreferences file (`first_use_hints`): seen keys, muted, library visits. Read once per
  app start off the main thread; hints stay hidden until it is read, so a seen hint never flashes.
- **Tests run muted:** `AudoraTestRunner` mutes hints before the app starts, so no other test's screen changes.
  `FirstUseHintsUiTest` gives its own file. Guarded by `FirstUseHintsTest`, `ReaderHintsTest`, `ReaderNoticeTest`,
  `FirstUseHintsUiTest` and a release journey step.

## Differences from the audit's list (flagged to the maintainer)

- **Scanned PDF with no OCR pack:** not a new hint. The reader already shows its scanned-page card in the same slot,
  with the pack's download, every time it applies; a second card would repeat it.
- **Drive book with no internet:** the hint shows when a cloud book opens, online or not. Checking the network would
  need the `ACCESS_NETWORK_STATE` permission and a listener; the advice (keep a copy for offline) holds either way.
- **Charging** is read once when a book opens (the battery's sticky broadcast), not watched; plugging in mid-book
  shows the hint next time the book opens.
- **"Session"** means one app start.

## Consequences

- Cost: one small SharedPreferences read per app start, one sticky-broadcast read per book open, and a few
  comparisons when the reader redraws. No receiver, no background work, no new permission or dependency.
- A new hint is one enum entry, one fact in `ReaderHintFacts`, one string in three languages and one route in
  `actOnHint`.
