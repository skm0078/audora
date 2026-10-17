# ADR-035: Speakability layer between the page and the voice

**Status:** Accepted (2026-10-17). Covers reading-intelligence tickets RI-1 to RI-9 (plan items 53–60); later
tickets append to Consequences.

## Context
The first hands-on runs showed that what makes a page sound wrong is rarely the voice. Scanned pages read OCR noise
aloud ("|| ;: =~ 1l"), tables are read cell by cell as one run-on sentence or dropped without a word, footnote
markers are spoken as stray numbers ("the method¹² works" → "the method twelve works"), display equations become
symbol soup, and nothing tells the listener that a figure caption or a footnote has started. The maintainer called
this make-or-break.

Until now `:pdf` had one way to deal with page furniture: drop it (`ClutterRemover`). Dropping is right for running
headers and page numbers, but wrong for tables and notes: a listener may want them, the reader shows them, and resume
and highlight need a unit to land on.

## Decision
1. **Tag, don't drop.** `:pdf` keeps tables, footnotes, captions, equations and asides in its output and tags each
   unit with what it is (`UnitKind`: `TABLE_SUMMARY`, `TABLE_ROW`, `FOOTNOTE`, `CAPTION`, `EQUATION`, `ASIDE`, next
   to `BODY`, `HEADING`, `LIST_ITEM`). Running headers, footers, page numbers and citation markers are still removed
   as before (ADR-020).
2. **Score, don't judge.** `:pdf` gives every unit a `speakScore` (0–1): how likely it is real language a voice can
   read, from OCR word confidence, dictionary words, letter-trigram likelihood, symbol soup and script mixing inside
   a word. The weights and the threshold live in `SpeakabilityConfig` (RI-2a tunes them on hand-labelled lines).
   Null means not scored, and is read.
3. **Where each decision lives.**
   - `:pdf` detects, tags and scores. It never consults a user setting, so the same book gives the same units on
     every phone and the resume fingerprint stays a function of the page.
   - `:playback` decides by setting what is read, announced with a short generated earcon, or skipped ("Skip
     unreadable text", "Tables", "Footnotes", "Captions", "Equations"). A skipped unit stays in the page's unit list,
     so resume onto it lands on the next spoken unit, and the highlight and sleep timer are unaffected.
   - `:speech` turns a tagged unit into words (a table row read as "Name: Ram. Age: 34.") at render time (ADR-019).
4. **Table units carry their structure.** `TableRowInfo` gives a row's table, row index, size, the header row's cell
   texts and the row's cells as spans of its own text. Nothing is synthesized into `text` (ADR-018): the summary
   unit's text is the table's caption when it has one, and its size is in `TableRowInfo`.
5. **Footnotes are read where they are printed** (the bottom of the page, after the body), so playback order stays
   `charStart` order, which `PageCursor` and resume rely on. `noteRef` links a `FOOTNOTE` unit to its marker. Reading
   a note at the end of its paragraph needs playback order to differ from page order and is a later follow-up.
6. **Two scores guard it.** The golden corpus (ADR-017) keeps scoring `:pdf` text under the rules its expected texts
   were written with: `GoldenPipeline` leaves out `TABLE_ROW` units (table interiors were never in the expected
   texts); captions, footnote text and equations stay scored. A listening corpus (RI-8a) scores what the voice is
   given, after playback's default modes and the normalizers, against scripts written as a careful human reader would
   speak them, and CI fails if it drops against `dev`.
7. **Offline, no new library.** Word lists and letter models are data files built by our own script from openly
   licensed text and downloaded like the OCR models, not shipped in the APK. Spell lookup and trigram scoring are our
   own Kotlin. Online help (OpenRouter) is a separate, opt-in decision (ADR-037), never required.

## Alternatives considered
- **Keep dropping, add more drop rules.** Simplest, but a dropped table cannot be read on request, and every wrong
  drop silently loses text the listener cannot get back.
- **Decide in `:pdf` with settings passed in.** Fewer moving parts, but `:pdf` output (and the cache, and resume
  re-anchoring) would vary by setting; a setting change would need the book re-extracted.
- **A separate "skip list" next to the units instead of fields on `ReadingUnit`.** Keeps the model small, but every
  consumer (reader, player, export, highlight) would have to join two lists by offset.
- **An on-device language model to clean pages.** Too large and slow for the Moto Edge 50 Fusion as a default; it
  stays a candidate behind the online opt-in.

## Consequences
- RI-1 adds the fields with defaults, so every existing call site compiles unchanged and `:pdf` output does not
  change: PIPELINE_VERSION stays 9 and resume fingerprints (which hash `text` only) are untouched.
- Every `when` over `UnitKind` must handle the new kinds; the renderer reads them as body text until RI-4b, RI-5b
  and RI-7b give them their own readings.
- Tickets that start emitting new kinds or change unit text (RI-3, RI-4a, RI-5a, RI-7a) bump PIPELINE_VERSION once
  each and regenerate `docs/QUALITY.md`. RI-2a only adds scores and does not bump it.
- A download pack (word lists and letter models, about 5–15 MB for English, Hindi and Bengali) joins the OCR models;
  without it, scoring falls back to the signals that need no data and logs that once.
- Logs carry kinds, scores, page and unit index, never the page's text (AGENTS.md).
