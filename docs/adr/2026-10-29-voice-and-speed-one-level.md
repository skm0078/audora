# ADR 2026-10-29-voice-and-speed-one-level: Voice and speed is one sheet, one level deep, with player shortcuts

**Status:** Accepted (2026-10-29). Third of six findability PRs from the maintainer-approved audit (layout rule:
sidebar and drawer for places and major features, sheets for quick changes, top bar for search, filter and ⋮).
Refines ADR-062, whose finer groups opened as further sheets over the Voice and speed sheet.

## Context

The player's only way into reading options was a text button, "Speed 1×". Everything else (What to read, Sound,
Cast, Study, Focus sound) sat behind it, and each of those groups opened another sheet over the first, so a listener
looking for Focus sound had to guess that it lived under "Speed", then find a link, then land in a second sheet whose
Back did not return where they expected. The audit counted this as the most buried part of the app. Nothing on the
player said which voice was reading.

## Decision

- **One sheet, one level:** the finer groups open in place under a header (`SpeechGroup`), one at a time. Each
  header says what is set in it ("Pause at punctuation · Even loudness", "All off", "Rain"), so a listener sees the
  state without opening it. TalkBack hears "expanded" or "collapsed". No sheet ever opens over this one.
- **The chip:** the player's speed button becomes a chip with the tune icon, the speed and, under it, the voice
  reading the book's language (`readingVoiceName`: the book's own pick while it keeps its own settings, else the voice
  every book uses). Its content description is "Voice and speed"; its state is "Speed 1.25×, Bruno".
- **Shortcuts:** a row under the player's buttons (`SpeechShortcut`): Voices, What to read and Focus sound. Each opens
  the same sheet at its place (`SpeechSpot`), with that group open and scrolled into view.
- **Not while the first-run tips show:** the reader hides the shortcut row while its gesture tips sit under the
  page, and shows it after Got it. Both together left a 640 dp emulator's page too short for the snippet picker.
- **Sleep stays in the main row**, at the right end, where `PlayerLayoutTest` pins it; the shortcut row holds the
  three choices the audit found hardest to find.
- **Guarded by** `SpeechSpotTest` (group order, shortcut targets, header summaries), `BookVoicesSectionTest` (the
  chip's voice), `SpeechSheetGroupsTest` (a group opens in place and closes when another opens; a shortcut opens its
  group; one sheet title), `PlayerLayoutTest` (chip left, play centred, sleep right at every font size) and release
  journey step 51.

## Consequences

- The player's control panel is one chip row taller (about 52 dp). Accepted: three options move from two taps and a
  guess to one tap. On the smallest screens the page above the controls shows a few lines less.
- The groups are composed only while open, so the sheet opens no slower than before; the one-group-at-a-time rule
  keeps it short.
- Not in this PR: a Settings entry "Voice and speed for every book" (PR 6, which shares the "open at a place"
  plumbing with search over player options), and the player's Bookmark, Highlight and Share buttons (PR 5).
- The Made tab's player keeps its own speed button (`MadeSpeedButton`), which opens only the speed slider.
