# Golden corpus

The `:pdf` pipeline's main self-check (Build Spec §5, ADR-017). Every `./gradlew test` replays these documents
through the app's own pipeline and fails if the corpus score drops; `./gradlew :pdf:goldenTest` records the score in
[docs/QUALITY.md](../../docs/QUALITY.md) and `scores.tsv`.

| Path | What |
|---|---|
| `corpus.tsv` | One row per document: id, category, source file in the shared test-PDF folder, source pages, expected-text file, expected-text status |
| `pdfs/<id>.pdf` | The golden PDF: the source pages listed in `corpus.tsv` (whole file for small ones) |
| `rawpages/<id>.rawpages.gz` | PDFium's view of every page, dumped by `scripts/pdf/golden_corpus.py dump`; the JVM test reads these |
| `expected/<text>.txt` | What a listener should hear (rules below) |
| `scores.tsv` | The last recorded scores; the gate's baseline. Written by `goldenTest`, never by hand |

The test writes the spoken text of every document to `pdf/build/golden/testDebugUnitTest/spoken/<id>.txt`; diff it
against `expected/` to see what a score change means. CI uploads that folder when a run fails.

## Commands

```sh
./gradlew :pdf:goldenTest                      # score, write docs/QUALITY.md + scores.tsv (commit both)
pip install pypdfium2==5.13.0                  # once; its libpdfium.so is what the dumps are made with
python3 scripts/pdf/golden_corpus.py dump      # after changing a golden PDF or pdf/src/main/cpp/page_reader.cpp
python3 scripts/pdf/golden_corpus.py dump --check   # what CI runs
python3 scripts/pdf/legacy_golden.py           # after changing LegacyTables.kt: regenerate the legacy PDFs, then dump
```

CI fails when: a dump is stale; the corpus score is lower than `dev`'s on the documents both share; or
`docs/QUALITY.md` / `scores.tsv` differ from what the commit produces.

## Wiring a new pipeline stage

`GoldenPipeline.spokenText` (`pdf/src/test/.../golden/GoldenPipeline.kt`) is the only place the harness calls into
`:pdf`. A ticket that adds a stage (layout P2-02, clutter removal and repair P2-03, recovery P3) wires it there the
same way the playback service runs it, then runs `goldenTest` and commits the new scores. Keep its signature and the
`scores.tsv` columns stable: other branches and CI read them.

`GoldenRoutingTest` reads the same documents through `PdfBook.readPage` and checks page routing (P3-03): Unicode
Indic documents keep their text layer and pass the OCR sample, broken-map documents go to OCR.

## Expected text

What a careful human narrator would read aloud, in reading order:

- **Read:** title, author names, headings (section numbers included), body text, list items (without bullet
  glyphs), figure and table captions, display equations as their printed characters in reading order, footnote
  text where it appears on the page, author–year citations ("(Kot and Schaffer 1986)").
- **Skip:** running headers and footers, page numbers, print slugs and crop marks, journal banners and front-matter
  boilerplate (homepage, "Contents lists available at", DOI, received/accepted dates, licence lines, e-mail
  addresses, affiliations, keywords, abbreviation lists), text inside figures, table interiors, bracketed numeric
  citation markers ("[4–7]").
- **Repair:** join words hyphenated at a line end; keep real hyphens ("old-fashioned", "Cape-Horner",
  "block-pulse"). Use the correct Unicode text, not what a broken font map extracts.
- Whitespace, Unicode normalization form, soft hyphens and zero-width characters are ignored by the score.
- A PDF page that starts or ends mid-sentence starts or ends the expected text mid-sentence.

Status in `corpus.tsv`: `exact` = the generator's own text (S2's generated PDFs); `draft` = drafted (pdftotext for
English, Tesseract `tessdata_best` for Hindi/Bengali) and corrected by eye against the rendered pages, waiting for a
hand-check; `checked` = hand-checked by the maintainer. When you hand-check a file, fix it, set its status to
`checked`, run `goldenTest` and commit.

## Adding a document

1. Only files whose licence allows redistribution (table below). Never files with personal data.
2. Add a row to `corpus.tsv`, then `python3 scripts/pdf/golden_corpus.py excerpt --sources <test-pdfs folder>`
   and `... draft --sources <folder>` (never overwrites an existing expected file).
3. Correct the draft by eye against the page images, following the rules above; status `draft`.
4. `python3 scripts/pdf/golden_corpus.py dump`, `./gradlew :pdf:goldenTest`, commit everything. A new document is
   not gated until its first score is on `dev`.

## Sources and licences

Source files come from the shared test-PDF folder (`audora/test-pdfs/`, licence notes in its `MANIFEST.md`).

| Document | Source | Licence |
|---|---|---|
| en-udhr-noto, hi-/bn-udhr-noto, hi-/bn-udhr-lohit, hi-/bn-udhr-fpdf2, enhi-udhr-bilingual | Generated in S2 (`research/spikes/S2-pdf-extraction/scripts/`) from the Universal Declaration of Human Rights with Noto and Lohit fonts | UDHR text free to reproduce; Noto and Lohit fonts OFL-1.1 / GPL with font exception |
| en-mobydick-1col-long, en-kjv-2col-long | Generated in S2 from Project Gutenberg texts (Moby-Dick #2701, King James Bible) | Public domain |
| en-novel-moby-dick-gitenberg | https://github.com/GITenberg/Moby-Dick--Or-The-Whale_2701/releases/download/0.2.6/book.pdf, pages 21–23 | Public domain |
| en-textbook-forallx-yyc-logic | https://github.com/rzach/forallx-yyc/releases/download/F23/forallxyyc.pdf, pages 41–44 | CC BY 4.0 (forall x: Calgary, R. Zach et al.) |
| en-2col-physimaging-radonc-ccby | PMC10500023, Phys Imaging Radiat Oncol 27:100451 (2023), pages 1–2 | CC BY 4.0 |
| en-math-jmathbiol-integrodifference-ccby | PMC10500018, J Math Biol 87:57 (2023), pages 2–3 | CC BY (PMC open-access licence code, Springer OA) |
| hi-us-hud-factsheet-pd | US HUD fact sheet (Hindi), https://www.hud.gov/sites/dfiles/FHEO/documents/MD%20Fact%20Sheet%20Individual%20HIN.pdf, pages 2–3 | Public domain (US federal government work) |
| bn-us-fda-factsheet-vrinda-pd | US FDA Pfizer-BioNTech EUA fact sheet (Bengali), https://www.fda.gov/media/150079/download, pages 1–4 | Public domain (US federal government work) |
| bn-rotated-pages-90-180-270-fda | The FDA file above with pages rotated 0°, 90°, 180°, 270° (derived in S2), pages 1–4 | Public domain |
| bn-us-hud-fairlending-guide-pd | US HUD Fair Lending guide (Bengali), https://www.hud.gov/sites/documents/FAIR_LENDING_GUIDE_BN.PDF, pages 8–10 | Public domain (US federal government work) |
| hi-udhr-krutidev, bn-udhr-bijoy | Generated here by `python3 scripts/pdf/legacy_golden.py` (P3-04, ADR-023) from the first five paragraphs of hi-/bn-udhr-noto's expected text, encoded with the app's Kruti Dev and Bijoy tables; the PDFs name KrutiDev010 and SutonnyMJ without embedding them | UDHR text free to reproduce; no font included |

Page numbers above are 1-based PDF pages; `corpus.tsv` uses 0-based indices.

## Not covered here

Scanned pages (OCR runs on the phone; scored when P3-05 lands), real legacy-font Hindi/Bengali files (none is
redistributable; the two generated ones check the converter against its own tables, ADR-023), and password or damaged files (device tests in
`pdf/src/androidTest`). Pro Git (CC BY-NC-SA) and the pgf manual (LPPL/FDL) were left out to keep the corpus's
licences simple.
