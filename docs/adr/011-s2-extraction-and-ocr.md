# ADR-011: S2 outcome: PDFium confirmed, Indic text layers cross-checked by OCR, Tesseract as the v1 OCR

**Status:** Accepted (2026-10-14, S2). Evidence: `research/spikes/S2-pdf-extraction/REPORT.md`.

## Context
ADR-008 chose PdfiumAndroidKt and left three things for S2 to settle: whether PDFium beats PdfBox on real
Indic PDFs, what the JNI addition must cover, and which open OCR engine to use per script (D2: Tesseract
and/or PaddleOCR ONNX). The spec's §5 pipeline assumed most Indic text layers are usable after a
pre-base-matra reorder pass, with OCR as the fallback.

S2 measured 12 generated ground-truth PDFs and 40 real-world PDFs (Indian, Bangladeshi and US government
documents, legacy-font files, scans, 400–1,323 page books). Findings that drive this ADR:
- PDFium extracts the right Indic characters in the right order (0–5 % CER with spacing ignored).
  PdfBox 2.0.27 scrambles them (18–32 %). PDFium opens a 1,323-page book in 10 ms, PdfBox in 915 ms.
- PDFium inserts a fake space after every `/ActualText` span (Chrome/Skia and InDesign output), splitting
  Hindi/Bengali words. Rebuilding those spans from page objects brings CER from 9–22 % to 2–3 %.
- PdfiumAndroidKt does not wrap the functions needed for this or for routing (font info, generated flag,
  Unicode-map errors, page-object marks, text render mode), but its bundled `libpdfium.so` exports them.
- Of 11 real Unicode Hindi/Bengali source files, only 1 had a near-clean text layer. Office exports
  mis-map conjunct glyphs to wrong letters, so text-only checks cannot prove a layer correct. Reorder
  fixes visual-order matras only.
- PP-OCRv5 has no Bengali model. On Hindi it was not consistently better than Tesseract and lost more
  pre-base-matra words. Tesseract `best` was best or tied on English and best on Bengali.
- PDFium's document cache grows about 25–80 KB per page visited. Reopening every 100 pages halves it.

## Decision
1. **ADR-008 confirmed.** PdfiumAndroidKt stays. `:pdf` adds one small JNI library
   (`libaudora_pdfium_ext.so`) linked against PdfiumAndroidKt's bundled `libpdfium.so` and called with its
   page/text-page pointers. It exposes `FPDFText_GetFontInfo/GetFontSize/IsGenerated/HasUnicodeMapError`,
   `FPDFPage_GetObject`, `FPDFPageObj_GetMark` + `FPDFPageObjMark_GetParamBlobValue("ActualText")`,
   `FPDFTextObj_GetText` and `FPDFTextObj_GetTextRenderMode`. No fork. Font weight is not used (unreliable).
2. **`/ActualText` rebuild** is part of extraction (P1-02 for the API, P3-03 for Indic tuning): replace each span's
   characters with its ActualText, and keep a generated space at a span end only if a space glyph follows.
3. **Page routing (§5 step 2–3):**
   - legacy-font glyph runs (font-name list, run text mostly ASCII) → converter (P3-04);
   - Unicode-map errors > 2 %, invisible text over a page image, garbage, or < 40 chars → OCR;
   - **Indic documents: OCR one sample page and compare it with the rebuilt + reordered text layer.**
     CER ≤ 5 % → trust the layer; otherwise OCR every page. Re-check one page in 50.
   - English: text layer as-is.
   Thresholds live in `QualityConfig`.
4. **OCR = Tesseract (Tesseract4Android) with `tessdata_best` for eng, hin and ben.** Models are downloaded on
   demand through the model manager, not bundled. PaddleOCR Devanagari becomes an optional P3-05 experiment,
   adopted only if it beats Tesseract on real Hindi scans on a phone.
5. **Unbounded books:** extraction reopens the document every `reopenEveryPages` (100) pages, extracts only
   around the playhead, and never holds the whole book's text in memory. No page cap.
6. **Password-protected PDFs** get a password prompt. Empty-user-password encryption already opens.

## Alternatives considered
- **PdfBox-Android:** loses on Indic correctness, open time and heap. Rejected (as in ADR-008).
- **MuPDF (AGPL) or Poppler (GPL):** both handle `/ActualText` out of the box (0.2–0.8 % CER). But both would
  need our own NDK build, 16 KB alignment work and a second native PDF engine, for a gap a ~200-line JNI
  pass closes. Kept as the fallback if the rebuild proves fragile on real files.
- **PaddleOCR as the Hindi default:** lower CER on clean synthetic scans, but worse on degraded scans and on
  pre-base-matra words (what the listener hears), slower, and a second OCR code path. Not the default.
- **Trust the text layer unless the scorer objects (spec v1.0):** the corpus shows the scorer misses
  mis-mapped glyphs, and those pages would be read aloud as wrong words. The cross-check costs one OCR page
  per document.

## Consequences
- More Indic pages go through OCR. Expected cost is ~3–6 s/page on the second test phone (desktop 1–2 s, not yet
  measured on device). Pages take minutes to read aloud, so OCR runs inside the render-ahead window;
  Bengali gets extra lead.
- P1-02 grows by the JNI library and the `/ActualText` rebuild; its 500-page memory test now includes the reopen rule.
- P3-03's reorder pass becomes a repair step before the cross-check, not the main path.
- P3-05 shrinks to Tesseract integration plus an optional Paddle experiment.
- Spec §4, §5, §9 and §11, `AGENTS.md` and the ticket plan updated accordingly.
