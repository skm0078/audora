# ADR-073: One notice at a time in the reader; the voice is picked in the player; squeezed rows fail CI

**Status:** Accepted (2026-10-25). Follows the UI sanity pass before v0.1.0 (release blocker 3).

## Context
An emulator tour of every screen in English, Hindi and Bengali at font sizes 1, 1.3 and 2 found three
things a tester would notice first:

- **Stacked reader cards.** At large text sizes, the voice pack offer, the gesture tips, the time-left line and a
  permanent tap hint buried the book. Hindi at font size 2 showed no book text at all. PR #56 hid the tips while the
  voice pack card showed. That was a patch, not a rule.
- **No voice in the player.** The Voice and speed sheet showed voices only with "Use for this book only" on, so
  changing the voice for every book meant leaving the book for Settings → Voices.
- **Squeezed rows came back unseen.** A button beside a long title broke words in half ("comman / ds") at font
  size 2, and nothing in CI looks at font size 2.

## Decision
- **One notice slot** (`ReaderNotice`). The reader shows the first notice that applies and nothing else, in this
  order: a page that cannot be read (OCR files missing or OCR failed), then the voice pack offer, then the one-time
  gesture tips. The order is a pure function with unit tests. The permanent tap hint above every book is gone. Its
  tap line moved into the tips card (now "Quick tips"), and the hold line now also names "This sounded wrong".
- **The voice is always in Voice and speed.** With "Use for this book only" on, it picks this book's voice, as
  before. With it off, the group is called "Voices", and a pick there is the same choice as Settings → Voices
  (`VoiceManager.use`, shared by both screens).
- **The tour fails on broken words.** `UiTourTest` reads every text layout on screen. A line break inside a word
  fails the run. The only exception is the test books' file names, which may wrap anywhere. `ui-tour.yml` also runs
  on pull requests that change `ui/` or strings, touring only font size 2 (three passes, about 10 minutes) and
  publishing no screenshots. A feat/ui-* branch keeps the full tour on push.

## Alternatives considered
- **Dismiss-all "x" on each card.** Each card stays, and there are still several of them. The slot keeps the screen
  calm without asking for taps.
- **A screenshot diff (Paparazzi or Roborazzi).** It catches every pixel change, so every intended change needs new
  golden files. It also renders on the JVM without the phone's Hindi and Bengali fonts. Broken words are the defect
  we keep shipping, so we check for exactly that on a real emulator.

## Consequences
- A notice waits behind a more urgent one. The tips may appear only after a voice pack is downloaded or put off.
- The UI tour check is not a required check. It costs one emulator job per UI pull request.
- What's new (ID 5) lists the voice picker, the calmer reader and the large-text fixes.
