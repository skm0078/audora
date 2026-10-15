# Research: PDF text extraction on Android (2026-10-14)

Source: research pass for Build Spec v0.2. Claims marked *unverified* must be measured in spike S2.

## Findings
- **PdfBox-Android (TomRoush)**: last release v2.0.27.0 (~Jan 2023), upstream PDFBox is on 3.x, 113 open issues. Effectively unmaintained. Apache-2.0, pure Java (no 16 KB issue). Slowest/heaviest option *unverified on device*. Indic: no shaping, combining-mark ordering bugs (PDFBOX-5747 open); pre-base matras (ि ি ে ৈ) and split vowels (ো ৌ) likely come out in visual order.
- **io.legere:pdfiumandroid (PdfiumAndroidKt)**: Apache-2.0, v2.0.3 on 2026-07-26 (native leak fixes in 2.0.2), 3.0.4-alpha01 exists. Exposes textPageGetCharBox, getFontSize, textPageGetRects, getUnicode, getCharIndexAtPos. Does NOT wrap FPDFText_GetFontInfo / GetFontWeight (small JNI patch or fork needed). Bundled arm64 libpdfium.so is 16 KB aligned (JNI wrapper .so not checked). Indic: same ToUnicode-only limitation; no evidence of matra reordering.
- **bblanchon/pdfium-binaries**: MIT, active; you write your own JNI.
- **Platform PdfRenderer (API 35 / S-ext 13)**: getTextContents() gives text + line-level RectF bounds, no per-char boxes, no font info. PdfPageTextObject (font size) only API 36.1. Fine for display/search, not for layout analysis.
- **androidx.pdf**: 1.0.0-beta01 (2026-08-26), minSdk 28 backport, OcrProvider with ML Kit default. Not stable; no font data *unverified*.
- **MuPDF**: AGPL-3.0, best structured text (char quads + font + flags). Artifex says AGPL apps can't include proprietary components like Play Services; ML Kit is proprietary. Old Maven artifact; would need own build.
- **ML Kit Text Recognition v2**: Latin + Devanagari yes, **Bengali no**. Bundled ~4 MB per script per ABI; minSdk 23.
- **Tesseract4Android**: Apache-2.0, Tesseract 5.5.1, v4.9.0 (likely Jun 2025), 16 KB support since 4.8.0. ben.traineddata 836 KB (fast) / 10.5 MB (best). Speed per page *unverified* (guess: seconds).
- **Layout analysis**: no Android library. Port pdfplumber-style heuristics / XY-Cut. References: OpenDataLoader-PDF (Java, Apache-2.0, XY-Cut++), xycut-plus-plus Rust crate. Docling/LayoutParser too heavy.
- **Legacy fonts (Krutidev, Bijoy/SutonnyMJ)**: deterministic table-driven converters exist (font-name triggered, reorder pre-base i-matra and reph); incomplete for rare conjuncts. Needs per-char font name.

## Consequences for the spec
1. Switch primary extractor to PdfiumAndroidKt; keep PdfBox-Android only as the S2 comparison.
2. Add an Indic visual-to-logical reorder pass and a legacy-font converter before falling back to OCR.
3. GPL-3.0 + bundled ML Kit (proprietary) is a licensing conflict. Decide license before adopting ML Kit.

## Links
- https://github.com/TomRoush/PdfBox-Android/releases
- https://github.com/johngray1965/PdfiumAndroidKt , https://central.sonatype.com/artifact/io.legere/pdfiumandroid
- https://pdfium.googlesource.com/pdfium/+/main/public/fpdf_text.h
- https://developer.android.com/reference/android/graphics/pdf/content/PdfPageTextContent
- https://developer.android.com/jetpack/androidx/releases/pdf
- https://mupdf.readthedocs.io/en/1.27.0/guide/using-with-android.html
- https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
- https://github.com/adaptech-cz/Tesseract4Android/releases
- https://digitalorientalist.com/2025/12/02/why-extracting-hindi-text-from-pdfs-is-so-much-harder-than-english-and-how-you-can-do-it/
