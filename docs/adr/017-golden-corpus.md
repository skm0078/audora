# ADR-017: The golden corpus lives in pdf/golden, is scored on the JVM from PDFium page dumps, and gates CI

**Status:** Accepted (P2-01, 2026-10-15).

## Context
Build Spec §5 asks for ~25 redistributable golden PDFs in `code/testdata/pdfs/` with hand-checked
`testdata/expected/*.txt`, a similarity score that pipeline changes must not lower, and `./gradlew :pdf:goldenTest`
writing `docs/QUALITY.md`. Three things in that text do not fit the repo as built:
- There is no `code/` folder; module test data lives inside each module.
- The pipeline's first stage is PDFium (native). A JVM test cannot run it, and the device-test job is too slow and
  too flaky to be the agent's everyday self-check.
- "Must not lower the score" needs a definition that cannot flap and cannot be gamed by editing the score file.

## Decision
1. **Layout:** `pdf/golden/` holds `corpus.tsv` (id, category, source, page range, expected-text file, status),
   `pdfs/` (page excerpts of redistributable PDFs only, licences in `pdf/golden/README.md`), `expected/`,
   `rawpages/` and `scores.tsv`. Long books are excerpted to 2–5 consecutive pages, so the repo stays small (~6 MB)
   while running headers still repeat across pages.
2. **Scored on the JVM from page dumps.** `scripts/pdf/golden_corpus.py dump` runs the production page reader
   (`pdf/src/main/cpp/page_reader.cpp`, via `scripts/pdf/rawpage_dump.cpp`) against pypdfium2 5.13.0's desktop
   libpdfium.so and commits gzip dumps. `GoldenCorpusTest` replays them through the app's own `PdfBook` and
   `ReadingUnitBuilder` (`GoldenPipeline`, the single entry point later stages wire into) and scores the spoken text.
   CI re-dumps and fails if a dump is stale. The desktop PDFium is a different build from PdfiumAndroidKt's; the
   device tests keep checking the real one.
3. **Score:** per document, similarity = 1 − character error rate after folding only what a listener cannot hear
   (whitespace, Unicode form, soft hyphens, zero-width characters); word recall and word precision are reported to
   tell lost text from clutter. Corpus score = mean similarity over documents.
4. **Gate:** every `./gradlew test` fails if the corpus score, on the documents both runs share, is lower than the
   baseline at 4 decimals. CI's baseline is `dev`'s `pdf/golden/scores.tsv`, so a branch cannot lower the bar by
   editing its own copy. `docs/QUALITY.md` and `scores.tsv` depend only on code and corpus; CI fails when the
   committed copies differ, so the recorded score is always the real one.
5. **Expected text** follows written rules (`pdf/golden/README.md`): what a careful human narrator would read.
   Generated PDFs use their generator's exact text; real PDFs start as drafts (pdftotext for English, Tesseract
   `tessdata_best` for Hindi/Bengali) corrected by eye against the page images, and become `checked` after the
   maintainer's hand-check.

## Alternatives considered
- **Run the golden test on the emulator** with real PdfiumAndroidKt: exact engine, but minutes per run, only in CI,
  and not usable by the agent locally. Kept for engine checks (P1-02 device tests), not for scoring.
- **Score per page:** finer, but a sentence that crosses a page break would be charged twice and reading-order
  fixes that move text across pages would look like losses. Per document is what the listener hears.
- **Fail on any per-document drop:** stricter, but a layout fix that trades a little on one file for a lot on
  others would be blocked. The report lists every per-document change instead.
- **Word error rate as the headline:** blind to spacing and punctuation errors inside Indic words, which are
  exactly what `/ActualText` rebuild and the Indic passes fix.

## Consequences
- 18 documents, not ~25: scanned pages (OCR runs on the phone, P3-05) and legacy-font PDFs (no redistributable
  KrutiDev/Bijoy file exists; P3-04 adds a generated one) are not scored here. Password and damaged files stay in the
  device tests.
- A change to `page_reader.cpp` or a golden PDF needs `golden_corpus.py dump` and a new `goldenTest` run in the same
  commit. A change to the pipeline's output needs `goldenTest` and the two regenerated files committed.
- Spec §5's paths and the `:pdf:goldenTest` line in AGENTS.md now point here.
