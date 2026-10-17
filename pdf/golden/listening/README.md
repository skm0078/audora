# Listening corpus

The second self-check next to the [golden corpus](../README.md) (RI-8a, ADR-035). The golden corpus scores the text
`:pdf` extracts; this corpus scores **what the voice is given**: the `:pdf` units, after playback's default reading
modes and the `:speech` normalizers, against a script of what a careful human reader would say. A footnote marker
read as "twelve", a table read cell by cell, OCR noise, "(x)" read as "ten" or chart labels read as letters all lower
it. CI fails when it drops against `dev`; [docs/LISTENING.md](../../../docs/LISTENING.md) has the numbers.

| Path | What |
|---|---|
| `corpus.tsv` | One row per document, the golden corpus's columns: id, category, source, pages, script file, script status |
| `pdfs/<id>.pdf` | The pages listed in `corpus.tsv` |
| `rawpages/<id>.rawpages.gz` | PDFium's view of every page, dumped with the golden corpus's (CI re-dumps and compares) |
| `ocr/<id>.p<page>.hocr.gz` | Tesseract's hOCR of each page of a scanned document (category `scan-*`), read instead of the phone's OCR |
| `spoken/<script>.txt` | What a careful reader says (rules below) |
| `scores.tsv` | The last recorded scores; the gate's baseline. Written by `listeningTest`, never by hand |

## How it runs

1. `:pdf`'s `ListeningUnitsTest` (inside every `:pdf` unit test task) reads each document through the app's own
   `PdfBook` and writes its units to `pdf/build/golden/<task>/listening/<id>.tsv`. Scanned pages come from `ocr/`
   through the app's own hOCR reader. `:pdf` cannot depend on `:speech`, hence the file hand-off.
2. `:playback`'s `ListeningCorpusTest` (inside every `:playback` unit test task, which waits for `:pdf`'s) feeds the
   units through `HeardText`: playback's reading modes, then the real `EngineRenderer` with a voice that writes down
   what it is asked to say. The heard text goes to `playback/build/listening/<task>/heard/<id>.txt`; diff it against
   `spoken/` to see what a score change means. CI uploads that folder when a run fails.
3. The score per document is 1 − character error rate over **words only** (case, punctuation, spacing and
   zero-width characters are ignored: the listener hears those as pauses), plus word recall and precision.

```sh
./gradlew :playback:listeningTest      # score, write docs/LISTENING.md + scores.tsv (commit both)
python3 scripts/pdf/golden_corpus.py excerpt --corpus listening --sources <test-pdfs folder>   # new document
python3 scripts/pdf/golden_corpus.py dump                       # both corpora's page dumps
python3 scripts/pdf/golden_corpus.py ocr --corpus listening     # needs tesseract with eng, hin, ben
python3 scripts/pdf/golden_corpus.py draft --corpus listening --sources <test-pdfs folder>
```

### For the reading-mode tickets

`HeardText.spokenUnits` is where playback's modes apply. RI-2b (skip unreadable text), RI-4b (tables), RI-5b
(footnotes) and RI-7b (captions, equations, cues) each call their real mode code there with its default setting, so
the score follows the app. The unit TSV format is a contract between `ReadingUnitTsv` (`:pdf` tests) and
`ListeningUnits` (`:playback` tests); change both together.

## Writing a script

What a careful human narrator would say with the app's **default settings**, in reading order:

- **Words as spoken.** Numbers, dates, units, abbreviations and symbols as a person says them: "1st" → "first",
  "5:55 min" → "five minutes fifty-five seconds", "57 Gy" → "fifty-seven gray", "Fig. 2" → "Figure two",
  "Eq. (8)" → "Equation eight", "et al." → "and others", "−0.55" → "minus zero point five five", "∴" → "therefore",
  "x = ±L/2" → "x equals plus or minus L over two", "१९४८" → "उन्नीस सौ अड़तालीस", "ধারা ১" → "ধারা এক".
  Letter-and-digit names a voice reads right as written stay as written ("T2", "R1", "MRI", "USA").
- **Skip:** running headers and footers, page numbers, text inside figures (axis labels, legends), OCR noise,
  footnote markers in the body ("both.¹" → "both."), the number that starts a footnote, bracketed numeric citations.
- **Tables (default: Summary):** the caption, then "<rows> rows, <columns> columns." counting data rows (not the
  header row), e.g. "Table two. Student's t-test of … Four rows, five columns." Cells are not read.
- **Footnotes (default: at the end of the page):** the note's text where it is printed, without its number.
- **Captions (default: Announce):** read in full; the cue before them is a sound, not words.
- **Display equations:** "Equation" and its printed number ("Equation nine."), or just "Equation." when unnumbered.
  Inline maths is read as words.
- **Hindi and Bengali:** the correct Unicode spelling of what is printed, numbers in words.
- A page that starts or ends mid-sentence starts or ends the script mid-sentence.

Status: `draft` = written from the page (ground truth or pdftotext) and corrected by eye against the rendered pages;
`checked` = hand-checked by the maintainer. When you hand-check a script, fix it, set its status to `checked`, run
`listeningTest` and commit.

## Documents

| Id | What it tests | Source and licence |
|---|---|---|
| en-udhr-p0-1, hi-udhr-p0-1, bn-udhr-p0-1 | Clean English, Hindi and Bengali text layers; numbers in words | S2's generated UDHR PDFs (Noto); UDHR text free to reproduce, fonts OFL |
| hi-udhr-scan200bad-p0-1 | A degraded 200 dpi scan read by OCR (same words as hi-udhr-p0-1) | Rasterised from the same; recorded with Tesseract 5.3.4 (Ubuntu `tesseract-ocr-hin`/`eng`) |
| bn-udhr-scan200bad-p4-5 | A degraded 200 dpi Bengali scan: dandas and the vowel sign aa misread as "৷", "\|" or "!" (false stops, run-on sentences) | Rasterised from S2's bn-udhr-noto; recorded with Tesseract 5.3.4 (Ubuntu `tesseract-ocr-ben`/`eng`); script from its ground truth |
| en-physimaging-tables-p2-3 | Two tables, figure captions, chart labels, units | PMC10500023, CC BY 4.0 |
| en-forallx-footnotes-p22-23 | A footnote marker and a footnote running over the page | forall x: Calgary, CC BY 4.0 |
| en-jmathbiol-math-p6-7 | Display equations and inline maths | PMC10500018, CC BY |
| hi-udhr-krutidev | A legacy Kruti Dev font | Generated by `scripts/pdf/legacy_golden.py` (golden corpus) |
