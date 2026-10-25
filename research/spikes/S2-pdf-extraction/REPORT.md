# S2 — PDF extraction spike: report

Date: 2026-10-14 · Ticket: S2 (TICKET-PLAN Phase 0) · Outcome: **ADR-008 confirmed, with amendments (ADR-011)**

## 1. Verdict

1. **PDFium stays the extractor (ADR-008 confirmed).** On Hindi and Bengali it extracts the right characters in the right order where PdfBox 2.0.27 (PdfBox-Android's base) scrambles them: 0–5 % vs 18–32 % character error once spacing is ignored. It opens a 1,323-page book in 10 ms (PdfBox: 915 ms) and extracts a page in 1–5 ms on a desktop core.
2. **No page cap is needed.** Page-at-a-time extraction of 400–1,323 page books costs 0.7–4.6 ms/page and +10–46 MB RSS. PDFium's document cache grows about 25–80 KB per page visited, so `:pdf` must reopen the document every 100 pages (halves the growth; open costs < 10 ms) or only extract near the playhead.
3. **PdfiumAndroidKt is not enough on its own.** The text signals S2 depends on (font name, generated-char flag, Unicode-map errors, `/ActualText` spans, text render mode) are not wrapped by PdfiumAndroidKt, but its bundled `libpdfium.so` exports all of them. Plan: a small `libaudora_pdfium_ext.so` in `:pdf` that calls those `FPDF*` functions on PdfiumAndroidKt's page pointers. No fork.
4. **PDFium splits Hindi/Bengali words.** On PDFs from Chrome/Skia ("Save as PDF", Google Docs export: very common for Indic documents) PDFium inserts a fake space after every `/ActualText` cluster ("मा नव अधि का रों"). Rebuilding text from page objects + `/ActualText` + real space glyphs cut CER from 9.4 % → 2.5 % (hi) and 18 % → 2.6 % (bn), with 100 % recall of words containing pre-base matras. This becomes part of P1-02/P3-03.
5. **Most real-world Indic text layers are unusable.** Of 11 real Unicode Hindi/Bengali source files, only 1 (a Noto Bengali magazine) had a near-clean text layer. MS Word/Mangal/Nirmala/Vrinda exports map conjunct glyphs to wrong letters or ASCII punctuation (`राष्ट्रीय` → `िाष्ट्रीय`, `बौद्धिक` → `बौ-.क`). The planned reorder pass fixes visual-order matras only (CER vs OCR 24 % → 19 %, 17 % → 11 %), not wrong glyph mappings. **OCR is the main path for Indic, not the last resort.**
6. **OCR: Tesseract for all three scripts; PaddleOCR only an optional Hindi experiment.** PP-OCRv5 has **no Bengali model** (confirmed in its language table). On Hindi, PaddleOCR had lower CER on clean scans (1.9 % vs 3.7 %) but lost more words with pre-base matras (recall 0.91/0.85 vs 0.97), lost to Tesseract on the degraded scan, and was 1.5–3× slower in this pipeline. English: Tesseract `best` ties or beats it.
7. **Legacy fonts are easy to detect** by font name, per glyph run (KrutiDev, DevLys, Chanakya, SutonnyMJ all caught across 10 files), with one false positive class (`ChanakyaUni` is Unicode) fixed by also checking that the run's text is ASCII.

## 2. Setup

- **Engines (desktop, x86-64, 4 vCPU):** pypdfium2 5.13 / PDFium 153.0.7999 (same engine as PdfiumAndroidKt), PdfBox 2.0.27 (`pdfbox-app`, page-at-a-time, `-Xmx256m`), Tesseract 5.3.4 with `tessdata_fast` (Ubuntu) and `tessdata_best`, PP-OCRv5 mobile det + `en`/`devanagari` rec as ONNX (oar-ocr v0.3.0 conversions of the Apache-2.0 Paddle models) on ONNX Runtime with 4 threads. MuPDF and Poppler measured only as references.
- **Network:** this environment blocks arXiv, Wikipedia/Wikisource, archive.org and Gutenberg, so the corpus came from GitHub releases, PMC Open Access, the CC-MAIN 2021 PDF crawl (public S3), pdf.js / py-pdf fixtures, and self-generated files.
- **Corpus (all in the project's shared `audora/test-pdfs/`, not committed):**
  - 40 real files: English 2-column papers, math, a 437-page textbook, 424/501/1,323-page books; Unicode Hindi/Bengali from Indian, Bangladeshi and US government sites; 10 legacy-font files (KrutiDev, DevLys, Chanakya, SutonnyMJ); 6 scans (image-only Hindi, Bengali, English; one Hindi scan with a garbage OCR layer); encrypted, rotated and broken-ToUnicode files. `MANIFEST.md` lists source and licence per file. 14 are redistributable.
  - 12 generated ground-truth files (`scripts/gen_*.py`): UDHR in Hindi/Bengali/English (free to reproduce) typeset by two producers (Chromium/Skia, fpdf2+HarfBuzz) and two fonts (Noto Serif, Lohit), plus simulated scans at 300 dpi clean and 200 dpi degraded (rotation, blur, noise, JPEG q45), plus long books: Moby-Dick (387 p), KJV Bible in two columns (1,191 p), Hindi and Bengali 1,000+ page books. All redistributable; regenerate with the scripts.
- **Metrics:** CER = Levenshtein / reference length after NFC and whitespace collapse (`cer_nows` also drops whitespace, isolating spacing errors); "bad signs" = dependent vowel signs with no base consonant (the visual-order fingerprint); "pre-base recall" = share of reference words containing ि ি ে ৈ found verbatim. Real files without ground truth were hand-checked against the rendered page image and compared to OCR output of the same page.

## 3. Extraction: PDFium vs PdfBox (ground truth)

CER on the paged UDHR documents, corpus header/page numbers removed.

| Document | PDFium CER | PDFium CER, spaces ignored | PDFium bad signs | PdfBox CER | PdfBox CER, spaces ignored | PdfBox bad signs | ms/page PDFium / PdfBox |
|---|---|---|---|---|---|---|---|
| hi, Chromium, Noto | 9.4 % | **0.0 %** | 0 | 15.0 % | 18.1 % | 65 | 1.5 / 6.8 |
| hi, Chromium, Lohit | 1.9 % | 1.6 % | 0 | 15.9 % | 17.9 % | 239 | 1.5 / 5.3 |
| hi, fpdf2, Noto | 7.9 % | 6.5 % | 0 | 5.6 % | 6.1 % | 0 | 1.0 / 4.3 |
| bn, Chromium, Noto | 18.5 % | 1.8 % | 0 | 28.0 % | 31.9 % | 351 | 1.7 / 6.9 |
| bn, Chromium, Lohit | 21.8 % | 5.5 % | 0 | 26.8 % | 30.5 % | 353 | 1.5 / 6.8 |
| bn, fpdf2, Noto | 11.4 % | 12.1 % | 94 | 11.8 % | 12.5 % | 1 | 0.9 / 4.0 |
| en, Chromium, Noto | 0.01 % | 0.0 % | 0 | 0.01 % | 0.0 % | 0 | 1.7 / 8.4 |

Reading it:
- PDFium's characters are right; its spaces are wrong (§5). PdfBox reorders and drops Indic characters (`चंू क मानव अ धकारा`), which no later pass can repair.
- The fpdf2 files have a genuinely broken ToUnicode for conjunct glyphs; every engine fails. PDFium flags it: `FPDFText_HasUnicodeMapError` fired on 598 (hi) and 1,189 (bn) characters and on 0 in every clean file. That is a free, exact routing signal.
- Reference engines on the same files: Poppler 0.2–0.5 % CER and MuPDF 0.5–0.8 % on the Chromium files. Both honour `/ActualText` + space glyphs; that is the gap §5 closes inside PDFium.
- **Font info:** `FPDFText_GetFontInfo` returned a name for every glyph character in every file except Chromium's Type3 header/footer glyphs. Font size: 100 %. **Font weight is unreliable** (Noto Serif Regular reports 704; arXiv fonts 245–700), so headings must use size and the `Bold` name suffix, not weight.

## 4. No page cap: time and memory

Page-at-a-time (open → load page → text page → text → close), each book in a fresh process.

| Book | Pages | MB | PDFium ms/page median (p95) | Whole book (s) | RSS growth over the book | Jump to last page (ms) | PdfBox open (ms) / heap peak |
|---|---|---|---|---|---|---|---|
| Moby-Dick (generated) | 387 | 2.9 | 4.2 (5.7) | 1.6 | +10 MB | 8 | 190 / 139 MB |
| KJV, 2 columns (generated) | 1,191 | 8.4 | 4.6 (6.4) | 5.6 | +23 MB | 16 | 245 / 149 MB |
| Hindi UDHR ×200 (generated) | 1,034 | 8.2 | 3.4 (4.8) | 3.7 | +21 MB | 16 | 215 / 149 MB |
| Bengali UDHR ×200 (generated) | 1,001 | 7.2 | 3.6 (4.0) | 3.6 | +20 MB | 16 | 221 / 147 MB |
| pgf manual (real) | 1,323 | 9.0 | 1.7 (3.1) | 2.3 | +45 MB | 48 | 915 / 242 MB |
| Pro Git (real) | 501 | 17.9 | 0.9 (1.1) | 0.5 | +26 MB | 8 | 278 / 146 MB |
| Chanakya gazette (real) | 574 | 5.6 | 1.6 (3.3) | 1.1 | +48 MB | 8 | 278 / 161 MB |

- Extraction speed is a non-issue even with a 3× phone penalty: < 15 ms/page, while one page takes minutes to read aloud.
- Memory is the one thing to manage. Without the text harness (`rss_probe.py`), PDFium alone grew +46 MB over 574 pages (gazette) and +34 MB over 1,323 pages (pgf). **Reopening the document every 100 pages halved it** (+22 MB, +18 MB). Unbounded books therefore need: reopen every `QualityConfig.reopenEveryPages` (100), extract only the playhead window, never hold all page texts in memory.
- Random access ("start from any page") costs one open + one page: 8–48 ms for the last page of a 1,323-page book.

## 5. PDFium's split Indic words and the fix

Chromium/Skia writes each Indic cluster inside `/Span <</ActualText (…)>> BDC … EMC` and a real space glyph between words. PDFium's text page ignores the space glyph and inserts its own generated space after every span, so `मानव` comes out `मा नव`. Changing the gap threshold on PDFium's char boxes only got to 5.2 % (hi) / 8.3 % (bn), because PDFium splits a cluster's width evenly across its characters.

`scripts/objtext.py` rebuilds the text from page objects instead: one `/ActualText` per span (mark identity), zero-width empty text objects are space glyphs, a big leftward jump is a new line.

| File | PDFium text page | Rebuilt from objects | Pre-base word recall |
|---|---|---|---|
| hi Chromium Noto | 9.4 % | **2.5 %** | 0.16 → **1.00** |
| bn Chromium Noto | 18.5 % | **2.6 %** | 0.12 → **1.00** |
| bn Chromium Lohit | 21.8 % | **2.3 %** | 0.10 → **1.00** |
| en Chromium Noto | 0.01 % | 2.7 % | n/a |

Production rule (P1-02/P3-03): keep PDFium's character stream; replace the characters of each `/ActualText` span with the span text, and keep a generated space at a span end only if a space glyph follows. Apply only on pages that contain `/ActualText`, so English (last row) is untouched.

## 6. Real-world corpus: what the page router saw

The candidate router (`analyze_corpus.py`) assigns each page **OK**, **LEGACY** (legacy font run), **REORDER** (bad signs > 8 per 1,000 Indic chars), or **OCR** (< 40 chars, Unicode-map errors > 2 %, or garbage).

- **English (12 files incl. 3 long books):** all OK except image-only pages. Two-column order and headers are layout work (P2-02/03), not extraction.
- **Legacy fonts (10 files):** every KrutiDev / DevLys / Chanakya / SutonnyMJ page caught by font name. Two fixes: decide per glyph run, not per page (a single stray `Kruti Dev 010` space flagged a gazette page); and require the run's text to be mostly ASCII (`ChanakyaUni`, `Walkman-Chanakya905Bold` in a Noto magazine).
- **Unicode Hindi/Bengali (11 source files):** only the Noto Bengali magazine was near-clean (6.6 % CER vs OCR of the same page, mostly layout noise). The rest had wrong glyph-to-Unicode maps or duplicated clusters, and the router sees only some of them (bad signs, map errors). Two files the router rated OK were broken: the bilingual CERC gazette (`िेयर` for `शेयर`, 19 % vs OCR) and the B.C. budget in Mangal (`अर्थ र्थपूर्ण र्ण मेलल-मिल`, duplicated `/ActualText` clusters, 10–26 % vs OCR). Typical: `िाष्ट्रीय ग्रामीर्` for `राष्ट्रीय ग्रामीण` (Mangal, Word export) has no invalid sequence at all. Tesseract read the same page perfectly.
- **Reorder pass vs OCR of the same page** (`results/reorder.txt`): helps where the only defect is visual order (HUD Hindi 24 % → 19 %, SSA 17 % → 11 %, HUD Bengali 42 % → 26 %), does nothing or harm where glyphs are mis-mapped (Jalshakti, NCERT Kokila, Vrinda, Nikosh).
- **Scans:** image-only pages caught. **Miss:** a Hindi scan with an invisible Latin OCR layer (`StEft-r1 FKIITFTI…`) routes OK. Signal: all text objects use render mode 3 (invisible) over a full-page image. Such pages must be re-OCR'd unless their text passes the quality gate.
- **Encrypted:** RC4 and AES-256 with an empty user password open fine; a user-password file fails with `Incorrect password`. The app needs a password prompt (PdfiumAndroidKt supports a password argument).

**Consequence: a text-layer quality gate with an OCR cross-check.** For Indic pages the router cannot prove a text layer correct from the text alone. The amended default (ADR-011):
1. Font-name legacy runs → converter (P3-04); converter unknown-glyph rate high → OCR.
2. Map errors, invisible-text-over-image, garbage, or < 40 chars → OCR.
3. Otherwise, for Indic documents, OCR **one sample page per document** and compare with its text layer (after `/ActualText` rebuild and reorder). CER ≤ 5 % → trust the text layer for the whole document; else OCR every page. Re-check one page per 50 to catch mixed documents.

English skips step 3: no English text layer in the corpus needed it.

## 7. OCR: Tesseract vs PaddleOCR (ground truth)

Simulated scans of the UDHR pages; CER after removing header/page numbers; desktop ms/page with 4 threads.

| Scan | Tesseract fast | Tesseract best | PP-OCRv5 mobile |
|---|---|---|---|
| hi 300 dpi | 3.7 % · 800 ms · recall 0.98 | 3.8 % · 1,156 ms · 0.97 | **1.9 %** · 1,247 ms · **0.91** |
| hi 200 dpi degraded | 3.9 % · 587 ms · 0.97 | **3.7 %** · 841 ms · 0.98 | 4.2 % · 939 ms · 0.85 |
| bn 300 dpi | 6.2 % · 591 ms | **5.5 %** · 1,832 ms | no Bengali model |
| bn 200 dpi degraded | 5.2 % · 377 ms | **3.2 %** · 1,425 ms | no Bengali model |
| en 300 dpi | 2.2 % · 1,000 ms | **0.07 %** · 1,429 ms | **0.07 %** · 1,766 ms |
| en 200 dpi degraded | 0.4 % · 761 ms | **0.08 %** · 1,233 ms | 0.6 % · 1,513 ms |

"Recall" = pre-base matra word recall: the metric closest to "does it sound right".

Hand-checked real pages:
- **UP revenue letter (Hindi):** both engines read the body correctly. Only PaddleOCR read the handwritten date "16" and the file number.
- **Jalshakti page (Hindi):** Tesseract was perfect. PaddleOCR systematically wrote `राष्टरीय` (broken ट्र conjunct).
- **Bangladesh notice (Bengali):** `best` read the underlined subject line in full; `fast` dropped words (`মুসক`, `বৃদ্ধির`).
- **1867 journal scan (English):** both engines near-perfect.

Decision: **Tesseract with `tessdata_best` for eng/hin/ben is the v1 OCR**, one engine and one code path. Model sizes: 15.4 / 11.9 / 11.0 MB, downloaded on demand like voices. PaddleOCR Devanagari stays an optional P3-05 experiment on a real phone (det 4.8 MB + rec 7.9 MB on the ONNX Runtime we already ship). Adopt it only if it beats Tesseract on real Hindi scans after batching and tuning. This pipeline is minimal: no recognition batching, a simplified DB post-process.

Phone time estimate (inference, not measured on device): Tesseract best 1–2 s/page desktop → ~3–6 s/page on the second test phone and up to ~2× that on the test phone. That is far below one page's reading time (2–4 min), so OCR runs in the render-ahead window. Bengali `best` is the slowest (1.4–7 s desktop) and should get a page or two of extra lead.

## 8. What this changes

| Spec item | Was | Now (ADR-011) |
|---|---|---|
| ADR-008 extractor | PdfiumAndroidKt (+ font-info JNI); S2 verifies | **Confirmed.** JNI scope widened: font info, generated flag, Unicode-map errors, page-object marks (`/ActualText`), text-object text, render mode. One small `.so` linking PdfiumAndroidKt's `libpdfium.so`; no fork. |
| §5 step 2–3 routing | Score → reorder / legacy / OCR fallback | Score → legacy / OCR; **Indic: OCR-sample cross-check per document**; reorder kept only as a repair tried before the cross-check |
| §5 step 3c OCR | PaddleOCR where better (likely eng, hin), Tesseract for ben | **Tesseract `best` for eng/hin/ben**; PaddleOCR Devanagari optional experiment in P3-05 |
| §4 stack Phase 3 | Tesseract4Android + PaddleOCR ONNX | Tesseract4Android; PaddleOCR only if P3-05 proves it |
| §9 risk "Large PDFs (500+ pages)" | Page-at-a-time, bounded caches | Plus: reopen the document every 100 pages; never hold the whole book's text |
| New | — | Password prompt for user-password PDFs; hidden-OCR-layer detection (render mode 3) |

## 9. Open items and limits

- All numbers are desktop. Real-device timing of OCR and extraction belongs to P1-02 (500-page memory test) and P3-05.
- The corpus has no public-domain Hindi/Bengali scanned **books** (archive.org and DLI are blocked here). Government letters and notices stand in. Add 2–3 book scans when the network allows.
- No real Bijoy/KrutiDev **ground truth**: legacy detection is measured, conversion is not (P3-04).
- PaddleOCR was run through a minimal pipeline. Its numbers are a floor, not a ceiling.
- Synthetic scans share one font family per script. Real scans in §7 cover the gap only qualitatively.

## 10. Reproduce

The scripts assume a workspace at `/tmp/s2` with `pdf/`, `html/`, `scans/`, `models/`, `tessdata_best/`, `tools/pdfbox-app-2.0.27.jar`, `java/` and `results/`. Python deps: `pypdfium2 psutil rapidfuzz onnxruntime opencv-python-headless numpy img2pdf pillow fpdf2 uharfbuzz` (+ `pymupdf` for the reference). Chromium via Playwright for `print.js`; `tesseract-ocr` with `hin`/`ben`; fonts `fonts-noto-core fonts-lohit-deva fonts-lohit-beng-bengali`.

```
python3 scripts/gen_html.py && node scripts/print.js pdf html/*.html && python3 scripts/gen_fpdf.py
python3 scripts/make_scans.py hi-udhr-noto bn-udhr-noto en-udhr-noto
python3 scripts/bench_quality.py extraction && python3 scripts/bench_quality.py ocr
python3 scripts/bench_long.py <book names>      # and rss_probe.py <pdf> <reopenEvery>
python3 scripts/objtext.py hi-udhr-noto bn-udhr-noto   # /ActualText rebuild
python3 scripts/analyze_corpus.py <test-pdfs dir> results/corpus-router.json
```

Raw results are in `results/`. Text samples from non-redistributable files are left out of them.
