# ADR 2026-10-29-say-what-it-does: every option row says what it changes and what it costs

**Status:** Accepted (2026-10-29). First of six findability PRs from the maintainer-approved audit (layout rule:
sidebar and drawer for places and major features, sheets for quick changes, top bar for search, filter and ⋮).
Refines ADR-062, which put long help behind an info button and allowed one short subtitle line.

## Context

The audit found more than 20 switches with no line under their name: Pause at punctuation, Even loudness, Trim long
pauses, Smart speed, Running paragraphs, Turn pages, Highlight each word, Loop, Keep screen on, True black and
others. What they do, and what they cost (battery, a microphone left on), showed only after a tap on ⓘ, so listeners
could not tell what a switch would change before flipping it. Download packs said "Not downloaded · 85 MB" with no
word on why to download them. Two names described the app rather than the listener's goal: "Studio mode" and "Read
aloud to me", which is a tutor that listens to the listener read.

## Decision

- **Every `SwitchOption` has a subtitle:** one line of at most 52 characters saying what changes and, where there is
  one, the cost ("Uses more battery while it reads", "Microphone stays on · more battery"). The long help stays behind
  ⓘ. Lines live in `strings_row_lines.xml` with Hindi and Bengali copies. Where the existing help was already one short
  line (What to read's skip and page-number rows), it becomes the subtitle and the ⓘ goes.
- **Download packs** show what they add ("Reads pages that are photos or scans") above "Not downloaded · N MB".
  `ModelPackTexts` gains `pitch`.
- **Renames:** the book menu's "Studio mode" is "Prepare ahead (studio mode)", which keeps the term the studio sheet
  and the job card use. "Read aloud to me (experimental)" is "Practise reading aloud (beta)".
- **Guarded by `RowLinesTest`:** every `SwitchOption(` call in the app has a `subtitle =`; every row line exists in
  all three languages, fits the length and is shown somewhere.

## Consequences

- A new switch without a line fails the unit test, so the rule holds as the app grows.
- Rows grow by one text line; sheets scroll a little more. Accepted: the effect is visible at a glance.
- Not in this PR: the Voice and speed sheet's layout, re-homing the library ⋮, visible line actions, search over
  player options and first-use hints (the next findability PRs).
