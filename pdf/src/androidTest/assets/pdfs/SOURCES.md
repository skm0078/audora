# Test PDFs for :pdf instrumented tests

All files are redistributable. The UDHR files were generated for S2 (`research/spikes/S2-pdf-extraction/scripts/`)
by printing the Universal Declaration of Human Rights to PDF in Chromium with Noto Serif fonts; each page has
one block of text, so `*.gt.json` holds the exact text of every page (the S2 page footer is not in it).

| File | Source | Licence | Why it is here |
|---|---|---|---|
| en-udhr-noto.pdf | S2 generator, Chromium/Skia, Noto Serif | UDHR text free to reproduce; Noto OFL-1.1 | Plain English baseline |
| hi-udhr-noto.pdf | S2 generator, Chromium/Skia, Noto Serif Devanagari | as above | `/ActualText` spans split Hindi words in PDFium (S2 §5) |
| bn-udhr-noto.pdf | S2 generator, Chromium/Skia, Noto Serif Bengali | as above | Same for Bengali, plus reph reordering |
| encrypted-userpassword-libreoffice.pdf | https://github.com/py-pdf/sample-files `005-libreoffice-writer-password` | CC BY-SA 4.0 (py-pdf/sample-files) | User password `openpassword`: the password prompt path |
| *.gt.json | S2 generator | as above | Ground truth: `{"mode": "paged", "pages": [...]}` |
| {en,hi,bn}-udhr-noto-scan200bad-p1.pdf | Page 1 of the S2 `*-scan200bad.pdf` files (the Noto PDFs rasterised at 200 dpi with rotation, blur, noise and JPEG q45), cut with `pdfseparate` | as above | Image-only scans for the OCR device test (P3-05); page 1 of the matching `*.gt.json` is their text |

The 500-page memory test generates its PDF at run time (`GeneratedPdf.kt`), so no large file is committed.
