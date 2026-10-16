# ADR-021: Page quality routing, Indic repair before layout repair, and a block-order-tolerant OCR sample

**Status:** Accepted (P3-03, 2026-10-16). Refines Build Spec §5 steps 2 and 3b and ADR-011 §3.

## Context
ADR-011 decided the routing rules (Unicode-map errors, hidden OCR layers, garbage or near-empty pages → OCR; legacy
fonts → converter; Indic documents: OCR one sample page, CER ≤ 5 % → trust the text layer, else OCR every page) and
left P3-03 to build them, with the Indic reorder pass as a repair before the cross-check. Building them against the
golden corpus and 16 local-only files from the S2 test set (`audora/test-pdfs`) showed:

- **Unicode text never puts a pre-base vowel sign (ि ি ে ৈ) at a word start before a consonant**, and never ends a
  syllable with a reph (ra + virama with no consonant after it). Visual-order exports do both often: the US HUD
  Hindi fact sheet starts 36 of its 70 i-matras that way, the HUD Bengali guide 49 of 125. Clean Unicode files score
  0 on both counts. Mis-mapped exports (Jal Shakti report, the HUD-like Office files) show rephs "at a syllable end"
  too, but their pre-base signs are in logical order: those are wrong letters, not visual order.
- **Visual order is per page, not per span:** a page either is or is not, but short pages have too few signs to
  tell.
- **Some fonts draw part of a vowel sign as a glyph with no Unicode value**, which arrives as U+FFFD between a
  consonant and its sign ("ক�ো", a Noto-based Bengali magazine): a clean layer that a junk count would reject.
- **A plain CER between OCR and the text layer depends on block order.** The pipeline compares OCR with the text in
  layout order; on the generated UDHR pages XY-cut puts the footer first, OCR reads it last, and a perfect text layer
  scored 3.5–4.6 % CER against a perfect stand-in OCR: no margin under 5 % for Tesseract's own errors.
- **Of the routing signals, the impossible-sequence rate does most of the work** on real files: every mis-mapped
  Office export in the set has 1.1–7.6 % of its Indic characters in impossible positions (a vowel sign after a vowel
  sign, a virama at a word start), clean Unicode files 0–0.2 %.

## Decision
1. **Scorer (`pdf.quality.PageQualityScorer`, per page, cached with the page's layout):** in order, fewer than 40
   letters → OCR; > 50 % invisible text → OCR; legacy-font runs (font name list, run ≥ 80 % ASCII) holding ≥ 20 % of
   the letters → converter; Unicode-map errors > 2 % → OCR; junk > 2 % of letters (U+FFFD, private use, controls,
   ASCII glued inside an Indic word) → OCR; Indic page with > 1 % of Indic characters in impossible positions → OCR;
   else text layer. Junk and impossible sequences are counted **after** the page's own Indic repair, so a visual-order
   page the reorder pass fixes stays on its text layer. All thresholds in `QualityConfig` (`:core`).
2. **Indic repair (`IndicReorder`) runs per paragraph before `TextRepair`**, inside `ReadingTextBuilder`, so source
   offsets and boxes stay mapped (ADR-018): characters move only within a syllable and keep the offset of the
   position they land on. Always: drop U+FFFD between an Indic letter and a vowel sign, virama or nukta. On
   visual-order pages: move each pre-base sign after its consonant cluster, compose ে + া → ো and ে + ৗ → ৌ, and (only
   when the page's rephs are also in visual order) move each reph before its syllable's cluster.
3. **Visual order is decided per page** from `IndicOrderEvidence`: ≥ 8 pre-base signs and ≥ 10 % of them
   word-initial; rephs: ≥ 3 and ≥ 25 % at a syllable end, and only on pages whose signs are visual. A page with too
   few signs uses the sum over its cleanup window (the evidence travels in `PageProfile`), else stays logical.
4. **Cross-check (`TextLayerCheck`, one per `PdfBook`):** the first Indic page that the scorer keeps on its text layer
   and that has ≥ 200 letters is OCR'd through a `PageOcr` port and compared with the page's repaired text in
   layout order, clutter included. The measure is **line-wise containment CER** (`CharacterErrorRate.ofLines`): each
   OCR line is aligned to its best-matching stretch of the layer text (semi-global edit distance) and the errors are
   summed over OCR's letters, after folding to letters, marks and digits (NFC). Block order and text OCR did not read
   cost nothing; every letter OCR read that the layer holds differently counts. ≤ 5 % → trusted, else every Indic page
   of the book goes to OCR. One Indic page in 50 is re-checked; a failed re-check rejects the book. A failed OCR call
   never rejects; with no OCR the book stays **unverified** and reads its text layer.
5. **Seams for P3-04/P3-05:** `PdfBook.readPage(page, builder)` returns the units and a `PageRouting` (route, reason,
   verdict). `PdfBook.open(..., ocr = PageOcr)` takes the OCR. Until P3-04 and P3-05 land, every page is still read
   from its text layer; routes are logged with their measurements (never text). `PIPELINE_VERSION` = 4.

## Alternatives considered
- **Plain CER for the sample (ADR-011 as written):** order-sensitive; a footer placed differently costs twice its
  length. Rejected for line-wise containment, which keeps the 5 % threshold meaningful.
- **Character-trigram overlap:** order-free too, but its scale (about 3 × CER) would have needed a new threshold with
  no S2 evidence behind it.
- **Reorder at extraction (`PageTextAssembler`):** would let layout see logical text, but composition deletes a
  character, which breaks `PageText`'s one-box-per-unit arrays and font-run offsets. Layout does not need the order.
- **Reph reorder whenever rephs look visual:** on the mis-mapped Jal Shakti report it would move mis-mapped र्
  around; gating it on visual pre-base signs leaves logical-order files untouched.
- **Comparing against the cleaned reading text:** OCR has no layout to clean, so its headers would count as errors.

## Consequences
- Golden corpus 0.9051 → 0.9187: the visual-order HUD Hindi fact sheet +0.06 and HUD Bengali guide +0.19; no other
  document moves. Every Unicode Indic golden document stays on its text layer and passes the sample (containment CER
  0–1 % against a stand-in OCR built from the expected text); every broken-map document goes to OCR, from the scorer
  alone and, with the scorer's thresholds off, from the sample alone (19–37 %) except S2's two fpdf2 files, whose
  letters are right and whose junk and spacing only the scorer sees.
- Local-only set (sampled pages): all 6 mis-mapped real Hindi/Bengali files route to OCR on every page, the 3
  legacy-font files to the converter (an English page among them stays on its text layer), image-only scans to OCR; the clean Noto Bengali magazine stays on its text layer once the U+FFFD placeholders are dropped.
  A scanned Hindi file whose hidden layer is Latin junk is not caught (its text is "English"); P3-05 can add a
  dictionary check if it matters.
- The 5 % sample threshold now has to absorb Tesseract's own error on clean Hindi/Bengali renders. P3-05 measures it
  on the phone and tunes `QualityConfig.maxSampleCer` if clean books get rejected.
- The sample runs inside `PdfBook`'s lock on the render thread. With Tesseract at 3–6 s a page that is one page of
  extra latency per book (inside the render-ahead window); P3-05 may move it ahead of the playhead.
