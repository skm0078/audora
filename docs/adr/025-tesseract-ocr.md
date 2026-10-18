# ADR-025: OCR with Tesseract4Android: upstream-pinned models, one model pack, disk cache, language probe

**Status:** Accepted (P3-05, 2026-10-16). Builds Build Spec §5 step 3c on ADR-011 (Tesseract for en/hi/bn, PaddleOCR
optional) and ADR-021 (pages routed to OCR, the book's OCR sample).

## Context
P3-03 routes scanned, hidden-layer and broken pages to OCR and samples one page of each Hindi/Bengali book against its
text layer; P3-04 sends unknown legacy glyphs there too. Until now those pages still read their text layer (usually
nothing, for a scan). Measured on the S2 corpus with desktop Tesseract 5.3.4 and the `tessdata_best` models at
300 dpi:

- Character error rate against the ground truth: English 2.3–2.6 %, Hindi 2.5–4.6 %, Bengali 3.9–5.2 %, the
  degraded 200 dpi scans (rotation, blur, noise, JPEG q45) included. Intelligible when read aloud.
- The book sample (OCR vs. a correct text layer, line-wise CER, ADR-021) is 2.7–3.7 % for Hindi and 2.1–3.0 % for
  Bengali on clean digital pages, so `QualityConfig.maxSampleCer` (5 %) stays.
- 2–3.7 s per page on a desktop core; a phone is slower, hence the cache and the per-page log.
- A 100 dpi pass with eng+hin+ben named the right script on every scan (generated and real, all three languages).

## Decision
1. **Engine:** Tesseract4Android 4.9.0 (Tesseract 5.5.1, Apache-2.0, built with 16 KB page support). It is published
   only on JitPack, so JitPack is declared as an `exclusiveContent` repository for its group alone, and
   `verifyTesseractAar` checks the AAR's SHA-256 against `tesseract4androidAarSha256` in the version catalog before
   every build. LSTM only, automatic page segmentation, the image's real dpi. One instance per process, reloaded only
   when the language set changes; calls serialized (Tesseract is not thread-safe).
2. **Models:** `tessdata_best` eng, hin, ben (≈ 38 MB together), downloaded at run time, never in the APK or git.
   URLs point at the upstream repository **at a fixed commit** (`speech/src/main/assets/ocr/tessdata.properties`:
   base URL, SHA-256 and size per file). This deviates from ADR-012/ADR-022's "mirror to our own release": a raw file
   at a commit is as immutable as a release asset, needs no repacking (no archive, unlike the voices) and no upload
   step; the SHA-256 pin still decides what is installed. A moved or deleted upstream file fails the download loudly;
   the fix then is a mirror, with the same pins.
3. **One model pack:** the three models download together through WorkManager (unique work, kept on conflict),
   reusing P4-01's resumable, SHA-checked downloader (generalised to `PinnedFile`). A scanned book's language is not
   known until it is read, and the probe needs every model; one Settings row and one card in the reader are simpler
   than per-language choices. Installed = right size + a `.sha256` marker written last, so a new pin re-downloads and
   a killed install never looks complete. Stored in `noBackupFilesDir/ocr` (large, re-downloadable).
4. **Pipeline:** `PdfBook` reads an OCR-routed page through its `PageOcr` (`RenderedPageOcr`): PDFium draws the page in
   greyscale at 300 dpi (lowered to fit 12 MP), Tesseract reads it, and the text is kept in a disk cache
   (`cacheDir/ocr`, 16 MiB LRU) keyed by book hash, page, languages, dpi and engine+model revision. Work on one page
   is serialized across the process, so the reader and the player OCR a page once. One log line per recognised page
   with `renderMs`, `recognizeMs` and the character count, never the text.
5. **Languages per page:** the page's own (converted) letters when its layer is only broken; the book's languages
   learned from readable pages or earlier OCR for scans and hidden layers; otherwise a 100 dpi probe with every
   installed model, once per book.
6. **OCR text into reading units:** Tesseract's lines and paragraphs become a one-block layout read by the same
   `ReadingTextBuilder` and sentence split as a text layer (line-end hyphens, junk, NFC); a lone page number at the top
   or bottom is dropped. Units have no glyph boxes (`bounds` null).
7. **Fallback and badge:** no models, a missing model or a failed recognition reads the text layer and returns
   `PageSource.OcrUnavailable` with the reason; OCR'd pages return `PageSource.Ocr`. The reader shows an "OCR" badge,
   or a card offering the model download, from that.
8. `PIPELINE_VERSION` = 6: OCR-routed pages now read different text, so their resume anchors re-anchor once.

## Alternatives considered
- **PaddleOCR (ONNX) Devanagari experiment:** ADR-011 made it optional and conditional on Tesseract falling short.
  Tesseract's measured Hindi error rate is under the 5 % sample bar, and adding ONNX Runtime and a second model pipeline
  for an experiment is not justified now. Not built; reopens only if the phone listening check finds Hindi OCR
  unintelligible.
- **Mirror the models on this repo's release:** extra workflow and upload for files that are already immutable at a
  commit (decision 2). Kept as the fallback.
- **`tessdata_fast`:** faster, but upstream ships `best` as the most accurate set and Indic error rates are already
  near the sample bar with `best`; quality first, the cache pays for speed. Not measured here.
- **Per-language downloads:** smaller first download for English-only users, but the probe and bilingual pages need
  several models anyway, and it doubles the UI. Revisit if storage complaints arrive.
- **ML Kit:** proprietary; excluded by ADR-011.
- **Keep OCR text in Room:** the cache is a cache; a cleared folder only costs a re-read, and files avoid a schema.

## Consequences
- Scanned pages read aloud once the models are downloaded; until then the reader offers the download.
- The device test (`TesseractOcrTest`) reads page 1 of the degraded en/hi/bn UDHR scans with the real Tesseract and
  PDFium on the CI emulator and fails above 10 % CER; the build downloads the pinned models into the test APK.
- The golden corpus is unchanged: its JVM pipeline has no OCR engine.
- **Known limits:** ~~running headers on OCR'd pages are read (only page numbers are dropped without positions); no
  rotation or orientation detection; no sentence highlight boxes on OCR'd pages~~ (fixed in ADR-032); the first page of a scanned book
  costs one extra low-resolution probe.
- **Amendment (2026-10-18, reading-intelligence follow-up):** pages are binarized with Sauvola
  (`thresholding_method=2`, `OcrConfig.threshold`) instead of Tesseract's default global Otsu, set through
  `TessBaseAPI.setVariable` on the bundled Tesseract; no new library. On UDHR pages in English, Hindi and Bengali
  degraded like phone photos it cut the character error under a lighting gradient from 12-19 % to 3-8 %, under
  blur, noise and JPEG from 34-80 % to 3-8 %, and under a hard shadow from 45-49 % to 15-33 %. On the generated
  300 dpi and degraded 200 dpi scans it stayed within half a point of Otsu (better on three of six), and on 19 pages
  of six real scans the word count and mean confidence moved by at most 2, at the same speed. The method is part of
  the OCR cache key (`TesseractEngine.cacheTag`).
- **Amendment (2026-10-18, same follow-up):** a page that still reads unsure (mean confidence below 85) with its
  paper unevenly lit (the 5th percentile of its estimated brightness under 0.85 of the 95th) is read again with its
  light evened out (`PaperLight`, `OcrConfig.lighting`): the paper's brightness is estimated on a copy sampled once
  per 1/32 inch (each block's brightest pixel, so ink drops out), widened and smoothed there, and each pixel divided
  by it; pure Kotlin on 8-bit grey, two passes over the pixels, about 50 ms for an A4 page at 300 dpi on a desktop
  JVM. The evened read is kept only when it reads at least 5 points surer with at least 90 % of the words. On UDHR
  pages with a hard shadow (Sauvola alone: 71-78 confidence, 15-33 % character error) it reads at 92-96 and 3-10 %,
  as the same pages unshadowed do. Gradient, vignette and blurred pages read at 87 or more and are not touched;
  every test scan's paper is at least 0.95 even, so scans are never touched. A cover with a white title on a grey
  band reads worse evened (the band turns white) and keeps its first read.
