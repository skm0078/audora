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
- RI-5a (notes) runs in `:pdf/layout/NoteFinder` after the furniture tags, on text-layer pages (OCR'd and turned
  pages carry no font sizes). A note marker is a run of small, raised glyphs (at most 0.85 of the line's size, bottom
  at least 0.25 em above its baseline) holding only numbers ("3", "8,16,17") or note symbols ("∗", "†"), after a
  word of two or more letters, closing punctuation or a bracket closing words, or opening a line before a word (a
  footnote's or affiliation's own number). After a digit, an operator, a single letter, a Greek letter or a unit
  ("deg²") it is an exponent and is read. Markers are left out of the reading text like citation markers, offsets
  still mapped (ADR-018); `NoteConfig.silenceMarkers` turns that off. Unicode superscript digits do not count by
  themselves: legacy and broken font maps reuse "²" and "³" for Bengali conjuncts. Footnotes are a column's
  trailing lines set smaller than the body (at most 0.92) and at least half an em below the line above, with
  nothing under them; each line opening with a marker starts a `FOOTNOTE` paragraph holding that note's number
  (`noteRef`), and lines before the first marker carry on a note from the page before (no number, and only when
  they run at least half the column's width, so a stray table cell is not one). Small print at a column's foot
  without a number (a publisher's licence lines, a print slug) is tagged the same: playback treats it alike.
  Footnotes stay where they are printed, so playback's page order is unchanged. A body paragraph opening with a
  note label ("Note:", "NOTE –", "टिप्पणी:", "দ্রষ্টব্য:") is an `ASIDE`. Still PIPELINE_VERSION 10 (not on `dev`
  yet). Checked by pdf/golden/notes/labels.tsv and by a count on 4 test PDFs kept outside the repository (an arXiv
  compiler paper, the LIGO detection paper with its author list of about 1,000 names, a Hindi–English gazette, a logic
  textbook): all 1,172 markers silenced, none of the 18 exponents and fractions taken for one.
- RI-10 (document kind) is `DocumentKindClassifier` in `:pdf/document`, run by `PdfBook.documentKind` on a dozen
  pages spread over the book (`DocumentKindConfig`), each read as playback reads it, OCR included. It is a decision
  list, first match wins: too little readable text (units below `SpeakabilityConfig.skipBelow` do not count) is
  GENERAL; then FORM (table rows a third of the units, or a fifth of the characters digits); PAPER (at most 50
  pages, with "[4]", "(Kot and Schaffer, 1986)" or "Murray (1989)" citations, or figures and equations); GAZETTE
  (official vocabulary in English, Hindi and Bengali); TEXTBOOK (12 pages or more with headings and list items,
  list labels like "(২)" or "•" counted in OCR'd text, which has no layout); NOVEL (20 pages or more of long
  sentences with almost no structure); else GENERAL. A decision list over counted signals, not a trained model: no
  new dependency, every rule readable, and a wrong call is traced to one threshold. No PIPELINE_VERSION change: units
  are unchanged. Measured on the 57 test PDFs (pdf/golden/kinds.tsv): 50 right (88%); 34 of 41 (83%) without the 16
  generated UDHR files. The ticket's 90% is not met. Misses: OCR'd books lose the headings and lists that tell a
  textbook (the NCERT curriculum reads as a novel, the Bengali fair-lending guide as general), a magazine about
  government schemes reads as a gazette, a scanned table reads as a gazette (OCR gives no table), a Hindi exam paper
  set in a table reads as a form, a merged notice of mostly school assignments reads as a textbook, and the RTI scan
  whose hidden OCR layer is Latin junk is too unreadable to tell (GENERAL). The next lever is headings and tables
  from OCR boxes, which changes OCR'd units and so bumps PIPELINE_VERSION.
- RI-3 (offline OCR repair) has two parts in `:pdf/ocr`. **Line retry** (`OcrLineRetry`, tunables in `OcrConfig`):
  a page is OCR'd in its book's languages, so a line in another script reads as noise; each line whose
  letter-weighted confidence is below 60 is cut out of the page image (padded by half its height) and read alone
  with each other installed model, and the best read replaces it when it gains at least 10 and itself reaches 60
  (punctuation read confidently counts for nothing, so "AYE |" cannot replace a Bengali word). Least confident lines
  first, at most 8 per page, within 2 s. **Word repair** (`OcrRepair`, `OcrRepairConfig`), on the words before they
  become page text so offsets and highlight boxes follow: two unknown neighbours that are one known word together
  are rejoined; an unknown word Tesseract was unsure of (below 90) becomes the one common known word one OCR
  confusion away (`OcrConfusions`: "rn"/"m", ध/भ, ण/ए, ব/ষ…), in English also one plain edit away; a colon read for
  a visarga and, in Bengali, "!" or "৷" read for the vowel sign aa are put back. Short words, compounds, words with
  digits and ties are kept. Hindi and Bengali change only through the confusions and the sign rules: their word
  lists lack many real inflected words, and free edits turned more of those into other words ("अकृत" → "अकूत") than
  they fixed. No new dependency (the RI-2a word lists); no PIPELINE_VERSION change of its own (10 has not reached
  `dev`); cached OCR from before is read again (its cache key changed). **Measured** on the six UDHR scans of the
  P3-05 set (200 dpi degraded and 300 dpi, all pages, the same pinned tessdata_best models, desktop Tesseract 5, the
  printed header stripped): en 0.12% → 0.12%, hi 3.89% → 0.31%, bn 1.16% → 1.12%. The ticket's baselines (2.6%,
  4.5%, 4.3%) were inflated by the emulator test, whose header pattern wanted a page number the header does not have
  and so counted the header as errors; fixed, and the test now asserts the ticket's targets (2.0%, 3.5%, 3.5%). Most
  of the Hindi gain is the retry reading the English header in English. The ground truth carries a few source typos
  (ঘোবণা, नির্বিশেবে), so some correct repairs still score as errors. On 13 real scans outside the repository (a CERC
  Hindi–English gazette, a UP revenue board letter, others) the retry replaced 257 of 502 unsure lines, turning their
  English forms and letters from noise into text. Limits: the 8-line, 2-second cap leaves whole English pages inside
  a Hindi book partly unread; a word the list lacks is never repaired; online repair waits on ADR-037.
- RI-4a part 2 (scanned tables) in `:pdf/ocr`. Tesseract reads a ruled table cell by cell and often a column at a
  time, and a borderless one as a line per row or as blocks of columns, so OCR order alone cannot rebuild either.
  **Rules** (`RuleFinder`, `RuleConfig`) are found in the upright page image right after OCR: ink below the page's
  Otsu threshold (capped at 200, for faint grey rules), runs at least 8 em long across (2 em down), rows smeared by
  0.2 em for skew and gaps up to 0.15 em bridged; a stroke thicker than 0.5 em is split into its well-inked bands (a
  double rule) and dropped if still thick (a filled bar, a photo). Linear in pixels, 30-200 ms a page on a desktop;
  rules are cached with the words (cache format v3; v1 and v2 still decode, the page cache key changed). **Ruled
  tables** (`RuledTables`): rules within 0.5 em are one edge; edges that meet make a grid, a table when it has at
  least three each way; slots join into cells where no rule divides them, and only rectangles are cells (the space
  around a box drawn inside a page frame is not); each word goes to the cell holding its centre; a word no wider
  than the snap on a vertical rule is a "|" or "।" read from the rule and is left out of the cells; the grid is cut
  where a row is divided differently from the one above (wall sets less than half shared), so a footer or title
  across it is text. A grid row with words in two or more cells that makes no table (one tall row of ruled columns)
  keeps OCR's column-at-a-time order rather than being cut into rows line by line. **Borderless tables**
  (`OcrTables`): the words left are joined into rows of OCR lines side by side and handed to the text-layer path
  (`TableFinder`, `TableGrid`); prose stops a run (a Tesseract paragraph of two or more lines of running text, or a
  row of two stretches of it side by side; running text is a stretch of at least 5 words or wider than 15 em, since
  OCR's em is a line's height and a narrow prose column is under 15 of them), as does a gap wider than 2 em, and a
  grid whose median cell is wider than 15 em is prose. A cell wrapped over several lines is therefore read whole, in
  OCR's order, rather than as a table cut line by line. A table's units go where its first word was read; pages without tables lay out
  exactly as before, and a lone number a table took is no longer dropped as a page number. **Measured** on the four
  labelled tables of the corpora OCR'd from 300 dpi renders (desktop Tesseract 5, the app's models): 145 of 145 cells
  in the right row and column, 106 exactly as printed (the rest are OCR misreads of ×, −, superscripts and
  symbols). On 9 scanned pages of two government documents kept outside the repository (a Bengali list, a Hindi
  form) the ruled tables come out row by row where Tesseract read them cell by cell, each cell only as clean as
  Tesseract read it; the six UDHR scans are unchanged (no table, same character error rate). Book kinds (RI-10) on
  the 57 test PDFs, re-measured with OCR'd pages read through this path: 49 right, from 50. The one change is the
  Bengali staff-transfer notice, labelled a gazette, whose tables are now found and make 56% of its units table
  rows, so it reads as a form; the label is kept (a notice), and the gazette rule does not outrank tables.
  Limits: a cell Tesseract never read stays empty (serial numbers in thick-ruled forms); a table continued from the
  previous page takes its first row as its header; a letterhead set as two aligned blocks reads as a small table;
  two prose columns that Tesseract ran together across the gutter stay run together. Same PIPELINE_VERSION 10.
- RI-10 on scanned pages: headings and lists from OCR (`OcrParagraphKinds`, `OcrBullets` in `:pdf/ocr`). OCR gives
  no fonts, so a scanned textbook had no headings or list items and read like a novel. Tesseract's hOCR gives each
  line's type size (`x_size`); it is kept on `OcrLine.size` (cache format v4; v1-v3 still decode, page cache key
  changed). A line at least 1.3 times the page's median line size is heading-sized (`LayoutConfig.ocrHeadingSizeRatio`;
  higher than the text layer's 1.15 because the estimate varies a little between lines of one type); a change of size
  splits a Tesseract paragraph, and a run of heading-sized lines within the text layer's heading limits is a heading
  when it reads like one: mean OCR confidence at least 80 (`ocrHeadingMinConfidence`), starting with a letter or
  digit, no "|", no sentence end. Those guards come from false headings on a UP revenue letter whose pages are
  screenshots of a web form: large text there is noise, pointers ("> click here") or table rows read badly. **Misread
  bullets:** Tesseract's Hindi and Bengali models have no bullet, so "•" and "○" come out as "o", "०", "০", "*", "+",
  "°" or "©" (66 lines of "*" and 19 of "o" in one NCERT book); a line's first word that is one of those, followed by
  a word starting with a letter at most 2.2 times its height away (`ocrBulletMaxGapEm`; farther, the "©" is "↔" in
  a table's first column), becomes "•", so the item is a list item and is no longer read as "zero" or "star".
  List labels after a clause end start items as on a text layer. **Measured** on the 57 test PDFs: 51 right (89.5%),
  from 49; 35 of 41 without the UDHR copies. The two scanned textbooks now classify (the Bengali fair-lending guide,
  the NCERT preschool curriculum); no right answer changed. Still short of 90% by one PDF; the six misses are not
  structure: a Bengali magazine and a scanned land-revenue table read as gazettes, a SutonnyMJ notice as a textbook,
  a DevLys exam paper and a Bengali notice as forms, an RTI reply so badly scanned it has 101 words. Golden,
  listening and the UDHR character error rates are unchanged (no scanned headings or bullets in them).
  PIPELINE_VERSION 11.
- Close table columns (`ColumnChannels`, in `TableGrid`, text-layer and scanned tables alike). Two columns become one
  when set closer than `cellGapEm` or when a header cell reaches across both (the Jal Shakti report's serial number
  and state, under one "क्र.सं. राज्य/केंद्र शासित प्रदेश" header). The gap between them still runs down the column at one
  place, while a cell's word spaces fall anywhere and are narrower: a column splits where at least 3 line rows, and
  80% of those reaching across that place, have a gap of at least 0.5 em there (`splitGapEm`, `minSplitRows`,
  `splitShare`); a total row to one side does not count. Of the 30 tables in the table corpora (18 text-layer, 12 scanned), the one that ran its
  serial numbers into the state names now reads them as two columns and nothing else changed; the 145 labelled cells
  and the scanned tables are unchanged.
- Note markers on scanned pages (`OcrNoteMarkers`, RI-5a). OCR gives no font sizes, so `NoteMarkers` cannot run, and
  Tesseract reads a superscript marker as a sign glued to the word ("Vaswani*", "Eich*,", "Haghighat®,", "Smith#,",
  "double.!") or drops it. A run of note signs (stars, daggers, "#", "®", "™", "§", "¶", superscript digits) after a
  word of at least two letters is left out, with any punctuation after it kept, and so is a "!" glued after a full
  stop; a sign set as its own word right against the word before, small and raised (the text layer's
  `markerSizeRatio` and `markerRaiseEm`) is left out too. Plain digits glued to a word ("Fig.1") and raised numbers on
  their own are kept: on 364 scanned test pages every raised number was a table figure or chart label. **Measured**
  on 130 footnoted text-layer pages rendered at 300 dpi and OCR'd, plus the 234 scanned pages of the other
  measurements: 51 lines changed, every change a marker or a form's required-field star; the title blocks of two
  papers now read their 23 author names without "star", "hash" or "registered"; no figure or word was dropped. Two
  markers stay: one OCR read as a letter ("Bebenitat") and one read as "™#," with its comma. Most in-text numeric
  markers are dropped by Tesseract itself.
- Sentence ends on scanned Hindi and Bengali pages (`OcrDandas`, `OcrAaBars`, RI-3). There is no offline Hindi or
  Bengali punctuation model, so the danda Tesseract misreads is restored from the page itself. **Measured** against
  the hand-checked text of the 40 UDHR scan pages (200 dpi degraded and 300 dpi): Hindi lost 2 of 208 sentence ends,
  both at 200 dpi (one danda merged into a misread word, one heading's full stop read as a comma), and dandas read as
  "|" were already restored by the text layer's pipe repair, so Hindi needs nothing. Bengali lost 14 of 130 to
  misreads: a line-end danda read as "৷" (U+09F7, a currency sign drawn as the same bar) or as a lone "|" after a
  misread word, and three dropped outright; worse, the vowel sign aa (also a bar) was read as "!", "|" or "৷" glued to
  a word, making 14 false stops on the 200 dpi pages and the wrong word ("কর" for "করা", "ধার" for "ধারা"), which
  `OcrRepair` left alone because the word as read is a word. Now a line-final "৷", or a lone "|" on a line of at least
  `LayoutConfig.ocrDandaMinIndicWords` Hindi or Bengali words, is the danda; mid-line, an unsure word's glued bar that
  runs on to another Bengali word is the sign aa after a consonant when that word is common, is dropped after the sign
  itself (the bar read twice; "৷" after the sign is dropped anywhere), completes the sign o after the sign e when that
  word is clearly commoner and is otherwise the danda. Result: running on 4 → 2 (one danda Tesseract dropped
  mid-line, one "= |" mid-line), false stops 14 → 0, Bengali character errors 1.12% → 0.91%. Across the 221 pages of
  the book-kind corpus, 47 units changed (into 23, as false stops no longer split sentences), all of them those repairs, except a "|" separator inside a bracket after
  "JPG" now read as a danda, and in a scanned table a "!" after "আলোচনা" (perhaps a danda) dropped. The listening
  corpus has no Bengali scan and its Hindi scan pages had no lost sentence end, so the listening score is unchanged.
- Logs carry kinds, scores, page and unit index, never the page's text (AGENTS.md).
