# ADR 2026-10-29-find-player-options: Search and You open the player's Voice and speed sheet

**Status:** Accepted (2026-10-29). Sixth of six findability PRs from the maintainer-approved audit (layout rule:
sidebar and drawer for places and major features, sheets for quick changes, top bar for search, filter and ⋮).
Builds on ADR 2026-10-28-action-search (the library search's "Actions and settings") and ADR
2026-10-29-voice-and-speed-one-level (the sheet's places, `SpeechSpot`).

## Context

After the Voice and speed sheet moved to one level, its options were findable from the player, but only from the
player. The library search answered "dark" or "audiobook", yet "speed", "pitch" or "focus" found nothing, because
those options belong to a book's player and not to You. You's Listening group led to Voices and nowhere else, so a
listener who looked for speed in Settings, where most apps keep it, found no way there.

## Decision

- **Search finds the sheet's places:** for the book search already names (the one typed, else the one read last),
  `FoundIndex` offers one row per `SpeechSpot`: Voice and speed (also found by "speed", "pitch", "pace", "faster",
  "slower", "voice" in all three languages), Voices, What to read, Sound, Cast, Study and Focus sound. Each row says
  where it lives ("The book you're reading · Voice and speed") and the book's title narrows it, as book actions do.
  None is offered for a book whose file must be located first, as its menu offers nothing either.
- **One way in:** a row is a `FoundTarget.Speech`; `LibraryActions.onOpenSpeech` hands it to
  `AppNavigation.openSpeech`, which keeps the place as a one-shot pending request (like `pendingPlace`) and opens the
  book. `ReaderControls`, shared by the reader and the player, opens its sheet at that place once and hands the
  request back (`SpeechRequest.onTaken`), so closing the sheet never brings it back and opening the book again later
  opens no sheet.
- **You's door:** Listening gets a "Voice and speed" row under Voices. It opens the book read last at the sheet's
  top. With no book (or only one whose file must be located), the row is shown but disabled, and says "Open a book
  first, then its speed and voice are here", so the option is findable before it is usable. Deviation from ADR
  2026-10-29-voice-and-speed-one-level's note "Voice and speed for every book": there is no separate every-book sheet;
  the player's sheet already changes what every book uses unless that book keeps its own settings, and a second copy
  of the same controls in You would be one more place to keep in step.
- **Guarded by** `FoundIndexTest` (rows, where line, words, none without a book), `SpeechRequestTest` (a request
  opens its place once and is handed back; the top opens with every group closed; no request opens nothing),
  `SettingsSpeechDoorTest` (the row opens the book read last; with none it says to open one and cannot be tapped)
  and release journey step 54 (search "focus" opens Focus sound in the book; You's row opens the sheet's top).

## Consequences

- Seven more entries in the search's in-memory list while a book is named. Matching stays a string compare per
  keystroke over a list of about a hundred entries; no measurable cost.
- The request is plain navigation state, not saved across process death: after the system kills the app the book
  reopens without the sheet, which is the safe side.
- Not in this PR: first-use hints for the player's buttons (moved to a later PR so this one stays small), and the
  player's Bookmark, Highlight and Share buttons (PR 5, after the share PR).
