# Outside-view benchmark: how other read-aloud apps handle the golden PDFs (P2-01)

**Status:** waiting for the listening session (maintainer). The harness and baseline scores are done
(docs/QUALITY.md); this note records what two existing apps do on the same pages, as a quality bar for P2-02/P2-03.

**Rules (D8):** black-box observation only. Listen, note, compare. No decompiling, no copying of code or assets.

## What to listen to

Five golden PDFs (download from `pdf/golden/pdfs/` on GitHub once P2-01 is on dev:
`https://github.com/skm0078/audora/raw/dev/pdf/golden/pdfs/<file>`). Each is 2–4 pages, about 3–5 minutes of
listening.

| # | File | Why |
|---|---|---|
| 1 | `en-2col-physimaging-radonc-ccby.pdf` | Two-column journal article: front matter, reading order, table, figure caption, citations |
| 2 | `en-textbook-forallx-yyc-logic.pdf` | Running headers and page numbers, chapter start, logic symbols (¬ ∧ ∨ →), a table |
| 3 | `en-novel-moby-dick-gitenberg.pdf` | Plain prose: pauses, dialogue, words hyphenated across lines ("old-fashioned") |
| 4 | `hi-us-hud-factsheet-pd.pdf` | Hindi with a partly broken font map: does it read Hindi, garbage, or nothing? |
| 5 | `bn-us-fda-factsheet-vrinda-pd.pdf` | Bengali with a broken font map, mixed with English drug names |

## The two apps

- **A. Adobe Acrobat Reader** (free) on the Windows PC: open the PDF, View → Read Out Loud → Activate Read Out Loud,
  then View → Read Out Loud → Read To End of Document. Default voice and speed.
- **B. Librera Reader** (free, open source) on the Moto: open the PDF, menu → Read aloud (TTS), with the phone's
  Google TTS voice. For files 4 and 5, also note whether it switches to the Hindi/Bengali voice.

## What to note (one row per app per file; a few words each is enough)

| File | App | Skipped (headers, page numbers, captions, footnotes?) | Order (columns, table, caption) | Pauses (headings, paragraphs, list items) | Misreads (numbers, symbols, hyphenated words, Hindi/Bengali) | Worth copying |
|---|---|---|---|---|---|---|
| 1 | A | | | | | |
| 1 | B | | | | | |
| 2 | A | | | | | |
| 2 | B | | | | | |
| 3 | A | | | | | |
| 3 | B | | | | | |
| 4 | A | | | | | |
| 4 | B | | | | | |
| 5 | A | | | | | |
| 5 | B | | | | | |

## What Audora's pipeline does today (from the golden run, pipeline version 1)

For comparison when filling the table; details per document in docs/QUALITY.md and in the spoken text the golden
test writes.

1. Reads the journal banner, DOI, licence line, affiliations and e-mail addresses before the title; reads columns in
   PDFium's object order (mostly right for this file) but puts table cells and figure labels inline; reads citation
   markers. Similarity 0.60.
2. Reads "CHAPTER 5. CONNECTIVES" and the page number on every page; reads the symbol table cell by cell.
3. Close to perfect, except that a word hyphenated at a line end comes out with PDFium's hyphen marker, a control
   character (U+0002), in place of the hyphen ("old\u0002fashioned"); P2-03 must turn it into the right word.
4. and 5. Speaks what the broken font map gives: Hindi word recall 0.64, Bengali 0.20. ADR-011's OCR cross-check
   (P3-03/P3-05) is the fix.
