# Speakability labels

The check behind the gibberish gate (RI-2a, ADR-035). `SpeakabilityLabelsTest` (in every `:pdf` unit-test run)
scores each line below with the default `SpeakabilityScorer` and the bundled word lists, and fails if the default
threshold (`SpeakabilityConfig.skipBelow`) catches fewer than 90% of the junk lines or flags more than 2% of the real
ones. It also checks that scoring stays under 5 ms a line.

`labels.tsv` columns: `source` (document, below), `route` (`text`: the PDF's text layer; `ocr`: Tesseract 5.3.4
hOCR at 300 dpi, read through the app's own hOCR reader), `ocr` (the engine's mean word confidence for the line,
0–1, as the pipeline passes it to the scorer; empty on text-layer lines), `label` (`junk` or `real`), `text` (the
unit as `ReadingUnitBuilder` cut it).

## What the labels mean

- **junk**: a voice reading it would be noise to a listener: OCR of the wrong script or language, an upside-down or
  very low-resolution scan, a bad hidden OCR layer ("Trqqrd sfrsmq"), a broken font map whose words come out
  unrecognisable, symbol soup.
- **real**: words a listener understands, even with an OCR slip ("fafa" for "वि.वि."), plus list numbers, years,
  table cells and headers ("3.", "1867", "पद का नाम").
- Lines that are partly garbled but still mostly understood (the `*-fpdf2` and HUD fact sheets, where a few
  clusters are broken) are left out: they are for OCR repair (RI-3) and the confidence badge (RI-9), not the gate.

## Sources

| Source | Route | Labels | Licence |
|---|---|---|---|
| hi-scan-mp-governor-rti-bad-ocr (pp. 3–6) | text: its bad Latin OCR layer; ocr: Hindi | junk; real except 5 noise lines | local-only (real lines kept only up to five words) |
| en-scan-1867-chicmedexam-imageonly | ocr | real, 1 noise line | CC0 |
| hi-/bn-udhr-noto-scan200bad | ocr | real | generated (UDHR text, Noto fonts) |
| hi-broken-tounicode-planning-commission-ssa (pp. 11–13) | text | real (broken matras, still understood) | local-only (up to five words) |
| bn-us-fda-factsheet-vrinda-pd | text, no OCR | junk (visual-order Bengali: "মকোধভড" for "কোভিড") | public domain |
| hi-scan-up-revenue-board-letter, hi-table-raj (hi-scan-raj-landrevenue-table-ccitt), bn-scan-gov-bd-cevdsc-notice | ocr | real | local-only (up to five words) |
| hi-wronglang-eng, bn-wronglang-eng | UDHR scans read with the English model | junk | generated |
| hi-wronglang-ben | the UP letter read with the Bengali model | junk (list numbers real) | local-only (junk and numbers only) |
| en-wronglang-hin, en-wronglang-ben | the 1867 scan and Moby-Dick read with the Hindi or Bengali model | junk | CC0, public domain |
| en-upsidedown, hi-upsidedown | pages turned 180° with no orientation check | junk | CC0, generated |
| en-lowres | the 1867 scan at 60 dpi | junk | CC0 |
| en-novel-moby-dick-gitenberg, en-textbook-forallx-yyc-logic, en-2col-physimaging-radonc-ccby, en-math-jmathbiol-integrodifference-ccby, hi-udhr-noto, bn-udhr-noto | text (golden corpus dumps) | real | public domain, CC BY |

Labels were set by reading every junk-route and scan line; clean-book lines were checked where they scored low.
Pages come from the shared test-PDF folder (`audora/test-pdfs`, pdf/golden/README.md). To add lines, run the pipeline
over new pages (as `ListeningUnitsTest` does), label them by reading, and re-run `./gradlew :pdf:test`; retune
`SpeakabilityConfig` only with the whole file, and record the new numbers in ADR-035.
