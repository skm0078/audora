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
7. **Offline, no new library.** Word lists are data files built by our own script from openly licensed text and
   bundled with `:pdf` (0.9 MB for English, Hindi and Bengali; RI-2a), so the gate works on first launch with no
   download. Letter models are trained from the same lists on the phone. Spell lookup and trigram scoring are our own
   Kotlin. Online help (OpenRouter) is a separate, opt-in decision (ADR-037), never required.

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
- RI-2a (gibberish scoring) ships the word lists inside the APK instead of the planned 5–15 MB download pack: the
  lists are 0.9 MB (wordfreq 3.1.1, data CC BY-SA 4.0: English 78k words, Hindi 24k, Bengali 93k), smaller than one
  voice, and a gate that needs a download would be off for exactly the people with the worst scans. A list that
  fails to load is logged once and that language scores without the dictionary and letter signals. A language's
  list is loaded and its trigram model trained the first time a page in it is scored (a few hundred ms and about
  10 MB of heap per language, once per process). The CC BY-SA credit belongs on the "Voices & licences" screen.
- RI-2a signals and tuning. Five signals, each mapped by a `SignalRange` and blended by weight: OCR word confidence
  (0.3; abstains on a short unit without words, since list numbers read with a low confidence), the share of
  in-list words weighted by length (0.1; "a", "or" and "at" turn up in any noise), letter-trigram likelihood
  calibrated per language against the list's own words (0.4), clean tokens, i.e. no symbols, no capital after a
  small letter, no Indic letters next to Latin or ASCII digits (0.15), and words in one script (0.05). Threshold
  0.55. Tuned on 1,234 labelled lines (442 junk, 792 real) in pdf/golden/speakability/: 91.0% of junk caught, 1.4%
  of real lines flagged. With each document held out of the tuning in turn the same search gives 84% and 1.8%, so
  expect somewhat less on unseen kinds of noise; most misses are visual-order Bengali whose common words still
  look right. OCR confidence now travels with each word (`OcrWord.confidence`, OCR cache format 2; pages cached
  before it score without that signal). Scores only, no text change: PIPELINE_VERSION stays 9, golden and
  listening scores are unchanged.
- RI-7a (captions and equations) tags in `:pdf/layout/FurnitureFinder` after cleanup, on text-layer and OCR pages.
  A caption is a paragraph opening with a label ("Fig. 2", "Table 3", "चित्र", "सारणी", "চিত্র", "সারণি") that is set
  apart: a delimiter, nothing or a capital after the label, or a heading, bold or smaller line. "Fig. 4 shows…" stays
  body. A display equation is a line with few dictionary words and a maths symbol or equation number, set off by an
  equation number, an indent or a block of its own; code, garbled glyph runs and broken-font Indic text are not
  maths. Unit boundaries were kept where they were: a caption's label stays its own sentence and each display line
  its own unit, because merging them made resume fingerprints miss and gave the listening score nothing until
  playback reads equations (RI-7b reads a run of equation units as one). PIPELINE_VERSION 10: a display line or a
  caption that layout joined to the paragraph above now ends the sentence before it. Checked by
  pdf/golden/furniture/labels.tsv (all 36 labelled units of both corpora tagged, nothing else) and by eye on 12
  test PDFs (arXiv papers, a logic textbook, Pro Git, Hindi and Bengali government documents): no wrong caption;
  wrong equations only on table cells of tables cleanup does not find (RI-4a).
- RI-4a (table grid) keeps the tables cleanup finds instead of dropping their lines. `:pdf/cleanup/TableGrid` turns
  each into a header unit (`TABLE_SUMMARY`) and one `TABLE_ROW` unit per data row, cells as spans of the row's real
  text (ADR-018) in `TableRowInfo`. The RI-1 contract is amended so the summary unit is the header row (its cells,
  `row` null, `rowCount` counting data rows): the caption is already its own `CAPTION` unit (RI-7a), and a unit
  whose text is only a caption or only counts would either duplicate it or synthesize text. Columns come from
  geometry alone (ruled lines are not extracted): runs closer than 1 em are one cell, columns are the merged cell
  extents of the body rows, a line row filling more than half the columns starts a row, a line lacking a key cell
  carries on the row above (a wrapped cell), and a cell alone in columns empty above joins the row above (a rank
  spanning two records). The header is the rows before the first with a number for a cell (up to three lines). A
  header cut off into blocks of its own above the table, and a last row (a total) cut off below it, join the table.
  A grid less than half filled, or with more than a fifth of its runs single letters (a chart's rotated axis
  titles), is a chart and is dropped as before; a single cell across two columns at the table's edge is a title
  left to the text. Playback carries a placeholder until RI-4b (`TablePlaceholder`): rows skipped, the summary
  read as "4 rows, 5 columns.". Still PIPELINE_VERSION 10 (10 has not reached `dev`; a resume anchor saved inside
  a table before resumes like removed text did). Checked by pdf/golden/tables/labels.tsv (4 redistributable tables,
  145 of 145 cells) and by eye on a Bengali result list and a Hindi government report kept outside the repository.
  Tables on scanned pages are not read yet: Tesseract gives each cell its own block, which needs a block-grid
  path of its own.
- Logs carry kinds, scores, page and unit index, never the page's text (AGENTS.md).
