# ADR-032: Known gaps after P3: Chanakya supplement glyphs, turned pages, scanned-page cleanup and highlight

**Status:** Accepted (2026-10-16).

## Context
The combined Phase 2–4 work left five code gaps in its known limits:
- P3-04 (ADR-023): a Rajasthan Board paper sets a glyph or two in a font named just "Chanakya" inside DevLys words.
  Its codes are not Gazette Chanakya's ("f" draws द्ध, "V" draws ङ्क), so "सिद्धिकार्यं" came out mangled. The same
  paper has a landscape table set sideways, whose text was read in drawing order.
- P3-05 (ADR-025): scanned pages kept running headers (only a lone page number was dropped), a sideways scan was
  read as noise, and OCR'd sentences had no highlight because Tesseract's plain text has no positions.
- P4-02 (ADR-027): the render-ahead window assumed 1.0× playback; two premium voices on a mixed page reloaded a
  model per language switch (both settled in ADR-027's follow-up sections). P2-03's citation-marker setting had no
  switch.

## Decision
1. **Chanakya supplement.** A Chanakya run of at most `QualityConfig.maxSupplementLetters` (2) letters touching a
   Kruti Dev or DevLys letter (one space allowed) is read with a small table of its own
   (`LegacyEncoding.CHANAKYA_SUPPLEMENT`: f → द्ध, V → ङ्क) and the Kruti Dev layout's reordering. Whole words in a
   Chanakya font keep Gazette Chanakya's table. Context decides, not the font name, because both files name the font
   the same way.
2. **Turned pages.** Before layout, the direction of each page's text is measured from the steps between
   consecutive glyph boxes (`TextDirection`, `LayoutConfig.minDirectionSteps`, `minTurnedShare`). A page whose text
   mostly runs up, down or upside down is laid out in its reading frame (boxes turned, `PageLayout.turn`); glyph
   boxes for the highlight stay in page space. Upright pages are untouched, so the golden corpus is unchanged.
3. **Scanned pages read with positions.** Tesseract returns hOCR: words with pixel boxes and confidences, lines and
   paragraphs (`Hocr`, `OcrText`). Boxes are kept as fractions of the page as displayed and mapped back to page space
   through the same PDFium transform the reader draws with (`PdfDocumentHandle.pageTransform`), so OCR'd sentences
   are highlighted line by line. The OCR cache stores this richer text in a new format (`.ocr`); old `.txt` pages are
   deleted and read again.
4. **Sideways scans.** A page whose mean word confidence is below `OcrConfig.orientationCheckBelow` (65) is read
   again shrunk to `orientationDpi` (150) in each of the four turns; when one beats the page as drawn by
   `orientationMinGain` (10), the full page is read turned that way. Upright print reads at 85–95, so the check costs
   nothing on good pages and four small reads on poor ones.
5. **Running headers on scans.** Neighbours are not OCR'd whole: only strips of the top and bottom
   `CleanupConfig.ocrMarginShare` (12 %) of the `ocrNeighbourPages` (2) pages on each side, at `OcrConfig.marginDpi`
   (200), turned like the page, and cached. A line of the page among the `maxMarginLines` nearest an edge, inside
   that band and on a page with body text outside it, is dropped when it is a page number by itself, carries a number
   that steps with the page across a neighbour's strip, or repeats in the same strip on enough neighbours
   (`minRepeatPages`, minus the page) at `ocrMatchSimilarity` (0.8) after folding digits. Reading strips rather than
   reusing cached neighbour pages keeps the result independent of what happens to be cached, so the reader and the
   player always produce the same offsets.
6. **Citation switch.** Settings → "Skip citation numbers" sets `CleanupConfig.skipCitationMarkers`, stored in
   `reading-text.properties` (`ReadingTextPreferences`, default on). Books opened afterwards use it; the player reopens
   a book it holds when the setting changed, so its offsets match the reader's.
7. `PIPELINE_VERSION` 9: legacy, turned-page and OCR output can change, so saved positions re-anchor by fingerprint.

## Alternatives considered
- **Detect the Rajasthan variant by font name or by unknown-glyph rate:** the name is the same as the Gazette file's
  bare "Chanakya" and the rate stays low; rejected for the context rule.
- **Tesseract's own orientation detection (OSD):** needs `osd.traineddata`, a further pinned download and the legacy
  engine, which `tessdata_best` does not include. Rejected; the confidence check uses the models already installed.
- **Compare against neighbours' cached full OCR:** free when cached, but the result would depend on reading order
  (the reader and the player could disagree on a page's offsets). Rejected for deterministic strips.
- **OCR every neighbour whole:** about five pages of OCR per page. Rejected; strips cost under half a page.

## Consequences
- A scanned book costs about 0.4 of a page of extra OCR per page for the strips (cached, read once per book), and a
  poorly printed page four extra small reads.
- A header that differs on every page (a chapter title that changes each page) is still read; so is a header OCR
  misreads badly on most pages.
- On a sideways scan the highlight shows a box per word rather than per line (lines run vertically in page space).
- The golden corpus has no OCR engine and no turned page with expected text, so these paths are covered by unit tests
  and the device checks in PROGRESS.md, not by the corpus score.
