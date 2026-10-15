# ADR-018: Layout rebuilds reading order; `charStart` indexes the page's reading-order text

**Status:** Accepted (P2-02, 2026-10-15). Revises Build Spec §4 (`ReadingUnit.charStart` comment) and §5 step 4.

## Context
P2-02 puts each page's lines in reading order: columns, then paragraphs and headings. Until now
`ReadingUnit.charStart`/`charEnd` were offsets into the extracted text (`PageText.text`, PDFium's content-stream
order after the `/ActualText` rebuild), and Build Spec §4 says "offset into the page's raw extracted text".

Once lines are reordered, raw offsets stop working for the code that already depends on them:
- The reader keys its sentence list by `charStart` and finds the playing sentence with
  `charStart in unit.charStart until unit.charEnd` (`ReaderState`).
- Resume (ADR-015, `ReAnchoring`) picks `units.indexOfLast { it.charStart <= anchor.charStart }`, which assumes
  units ascend by `charStart` in reading order.
- The playback service starts a book at a `charStart` (`PLAY_FROM`) and walks units in order.

In raw offsets a sentence that runs from the bottom of the left column into the right column covers two disjoint
ranges, and units no longer ascend: a two-column page drawn row by row would give the right column's first sentence
a smaller `charStart` than the left column's last.

S2 also found PDFium's font weight unreliable (ADR-011 §1), and on Elsevier PDFs PDFium's font size is the `Tf`
operand (1 pt) because the size is set by the text matrix.

## Decision
1. **`charStart`/`charEnd` index the page's reading text** (`pdf.layout.ReadingText.text`): the layout's lines in
   reading order, `\n` between lines of a paragraph, a blank line between paragraphs, a space between runs of one
   line where the source had a gap. A line ending in PDFium's line-end hyphen mark (U+0002) or a soft hyphen joins
   the next line with no break, as PDFium's own text did. Units are contiguous, disjoint and ascending in reading
   order, so every consumer above keeps working unchanged. `ReadingText.sourceOffset(i)` maps each offset back to
   `PageText.text` (separators map to `INSERTED`), and `ReadingUnit.bounds` come from the source glyph boxes.
   Later stages keep the rule: P2-03 builds the reading text from its cleaned layout, so offsets then index the
   cleaned text.
2. **`PIPELINE_VERSION` = 2.** Every offset moves, so anchors saved at version 1 re-anchor by fingerprint (ADR-015).
3. **Layout** (`pdf.layout.PageLayoutAnalyzer`, all tunables in `LayoutConfig`):
   - *Runs:* glyphs in extraction order, split at line breaks, at a jump to another line, at a gap of at least 0.75 em
     (PDFium joins two columns' lines when a content stream alternates between them) and at a jump back of more than
     2 em (Indic pre-base vowel signs jump back less, so words never split).
   - *Recursive XY-cut, columns first:* an empty vertical strip at least 0.75 em wide and 2 em tall, with at least 5 em
     of text on each side, is a gutter. Otherwise cut at the widest horizontal gaps. Columns first is what keeps two
     aligned columns apart: their line gaps line up into full-width strips that a rows-first cut would take. Leaves
     stacked in one column are joined back into one block.
   - *Lines* by vertical overlap; *paragraphs* by line spacing (1.3× the block's usual pitch), first-line indent and
     style change; *headings* are short paragraphs (≤ 3 lines, ≤ 200 chars) whose lines are ≥ 1.15× the body size or
     in a font whose name marks it bold (`bold`, `black`, `heavy`, `demi`, TeX's `cmbx`, `-Medi`, `-Bd`). Font weight
     is not used.
   - *Size* is PDFium's font size; below 2 pt (the `1 Tf` case) the glyph box height stands in. Box heights alone are
     unusable: Devanagari mixes loose and tight boxes (7.5 to 18.5 pt on one 16 pt page).
   - *Column continuation:* a block that starts a new column continues the previous block's last paragraph when both
     are body text of one size and the earlier one stops mid-sentence, so a sentence crossing the gutter is one unit.
   - *Fallback:* a page where under half the glyphs have a box (OCR'd or converted text) or under half sit in runs of
     two or more (vertical text) keeps extraction order as one paragraph, which is P1-02's behaviour.
4. **Sentences are split within each paragraph**, so a heading without a full stop never runs into the text below.
   A heading's unit and a paragraph's last unit take `ReadingUnitConfig.headingPauseMs` / `paragraphPauseMs`; both
   are 0 until P2-04 sets them by ear.
5. **The model is public for P2-03:** `PageLayout` → `TextBlock` → `Paragraph` (`kind`, `continuesPrevious`) →
   `TextLine` (spans into `PageText.text`, box, size, bold). Header/footer removal drops `TextLine`s by position and
   repetition and rebuilds the reading text with `ReadingTextBuilder`; de-hyphenation joins line ends, including
   across a `continuesPrevious` boundary.

## Alternatives considered
- **Keep raw offsets** and give a unit a list of ranges: `ReadingUnit`, `ResumeAnchor`, the playback contract and the
  reader would all need multi-range offsets, and "ascending by `charStart`" would still be false. Rejected.
- **Rewrite `PageText` in reading order:** same result with no way back to the extraction for P2-03's statistics and
  P3-03's OCR cross-check, which compare against PDFium's text. The offset map is that rewrite with the way back kept.
- **Full XY-Cut++** (pre-masking cross-layout elements, density-ordered merging): better on magazine layouts, more
  code and more tunables. The column-first cut reads the two-column fixtures correctly; revisit if the golden score
  (P2-01) shows layouts it misses.
- **PDFium's own order:** right for single-flow generators (Chromium, most LaTeX) and wrong where the content stream
  is not in reading order (on the Elsevier fixture PDFium reads the page footer before the masthead and interleaves
  a table's rows with its columns).

## Consequences
- One-time re-anchor of every saved position when this ships (fingerprint match, ADR-015), logged per resume.
- Known limits, measured on the fixtures (`pdf/src/test/resources/layout/`): tables are read column by column
  (P2-03 decides on table interiors); a running header beside a table can be read after the table (P2-03 removes
  running headers); Indic paragraph breaks are often missed because Devanagari and Bengali glyph boxes are uneven
  (sentences still split at the danda); several articles whose gutters line up (newspapers) read column by column
  across articles.
- Spec §4's `charStart` comment and §5 step 4 now point here; §11 lists this ADR.
