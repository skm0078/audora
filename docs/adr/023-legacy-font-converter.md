# ADR-023: Legacy-font converter: four encodings by font name, converted wherever read, unknown codes → OCR

**Status:** Accepted (P3-04, 2026-10-16). Builds Build Spec §5 step 3a; refines ADR-021's scorer order.

## Context
Pre-Unicode Hindi and Bengali fonts draw their glyphs on ASCII and Windows-1252 codes, so PDFium extracts "izsl
foKfIr" for "प्रेस विज्ञप्ति" and "MYcÖRvZš¿x" for "গণপ্রজাতন্ত্রী". ADR-011 routes runs in such fonts to a converter;
ADR-021's scorer already finds them by font name. Building the converter against the S2 legacy-font files
(`audora/test-pdfs/legacy-fonts/`, local only: UP and Bihar press releases, a Gazette of India issue, a Kundli salary
list, a Bangladeshi college notice and a typed diary) showed:

- **The "Kruti Dev family" is not one layout.** DevLys 010 draws श at ’ (0x92) where Kruti Dev 010 draws ष; Chanakya
  (the Gazette's font) has a full ध at "/", "." at ö, त्र at "=k" and brackets at ¹ º. Kundli and MINNO-GOPI copy
  Kruti Dev exactly. Shusha is a different layout altogether.
- **Fonts switch inside a word.** The Bihar file sets a word's pre-base ि in DevLys and the rest in Chanakya; a
  per-run converter cannot move the sign to its cluster.
- **PDFium inserts generated spaces inside syllables** where a sign glyph is drawn apart from its consonant ("g qbZ"
  for "हुई", "i zns”k" for "प्रदेश").
- **Typists place signs by eye:** anusvara before or after the vowel sign, nukta after the vowel sign, ्र after the
  vowel sign. Both orders occur in one file.
- **SutonnyMJ conjunct glyphs sit on Latin-1 letters** (š ¿ Í ÿ), so a run check for "mostly ASCII" misses them.
- **"0" after a Hindi letter is the abbreviation sign** in typed government text ("पी0एस0").

## Decision
1. **Encodings by font name** (`QualityConfig.legacyFonts`, a name fragment → `LegacyEncoding`): KRUTIDEV (krutidev,
   kruti dev, kundli, minno-gopi), DEVLYS, CHANAKYA, BIJOY (sutonnymj, sutonnyomj, bijoy, boishakhimj) and
   UNSUPPORTED (shusha). A run counts when ≥ 80 % of its letters are Latin (code < U+0250), so a Unicode font that
   kept a legacy name (ChanakyaUni) is read as is; a run with no letters (a lone ’) is converted.
2. **Conversion (`pdf.legacy.LegacyConverter`) is table-driven** (`LegacyTables.kt`): longest match per run's table;
   neighbouring runs of one script, and spaces layout inserted between them, are converted as one stretch. Per
   stretch: drop spaces inside a syllable (before a dependent code or after a pre-base code); map codes; move pre-base
   signs (ि ি ে ৈ) after their consonant cluster and rephs (Kruti Dev Z, Bijoy ©; Æ is reph + ि) before their
   syllable; put a syllable's tail in Unicode order (nukta, phala, vowel sign, anusvara); compose split vowels (अ + ा,
   ে + া) and doubled viramas; "0" after a Hindi letter → ॰. Characters keep the source offset of the position they
   land on (ADR-018), as `IndicReorder` does. A code no table covers is dropped and counted.
3. **Conversion runs wherever legacy text is read**, before `IndicReorder` and `TextRepair` in `ReadingTextBuilder`,
   whatever the page's route: a mixed page (English with a Kruti Dev name) is converted too.
4. **Scorer order (refines ADR-021 §1):** legacy runs holding ≥ 20 % of the letters with > 2 % unknown codes
   (`maxLegacyUnknownRate`; every code of an UNSUPPORTED font is unknown) → OCR (`LEGACY_UNKNOWN_GLYPHS`), checked
   first so a page the converter cannot read is never judged on its gibberish; then ADR-021's checks, measured on the
   converted text; then legacy → `LEGACY_FONT`. `PageQuality` carries the runs and the OCR languages of the converted
   text. Legacy pages are not OCR-sampled (the conversion is deterministic; the unknown rate is their check).
5. **Bengali অ্য/এ্য (the "a" of "acid") is a valid sequence** in the invalid-Indic counter; Unicode Bengali writes it
   too, so this applies to every page.
6. **Golden corpus:** two generated documents (`scripts/pdf/legacy_golden.py`): UDHR Hindi in Kruti Dev 010 codes and
   UDHR Bengali in Bijoy codes, PDFs that name the font without embedding it (no legacy font may be redistributed).
   They are encoded from the app's own tables, so they prove the converter undoes the tables, not that the tables
   match the fonts; the real files were checked by hand against their rendered pages.
7. `PIPELINE_VERSION` = 5: legacy-font pages read differently, so their resume anchors re-anchor once.

## Alternatives considered
- **OCR every legacy page:** always correct glyphs, but Tesseract costs seconds per page on the phone and makes its
  own errors on clean print; conversion is instant and exact where the table is right. OCR stays the fallback.
- **One Kruti Dev table for every "Kruti Dev-like" font:** reads श as ष in every DevLys file. Separate encodings cost
  a few override entries.
- **Convert per run:** breaks words whose font changes mid-word (Bihar file).
- **Convert in `PageTextAssembler` at extraction:** composition and syllable moves would break `PageText`'s
  one-box-per-unit arrays and font-run offsets, as ADR-021 found for the reorder.
- **Shusha table:** no redistributable reference and no file in the set; routed to OCR instead.

## Consequences
- Golden corpus 0.9187 → 0.9268 (20 documents): hi-udhr-krutidev 0.9989, bn-udhr-bijoy 1.0000; no other document
  moves. With every font mapped to UNSUPPORTED both route to OCR (`GoldenRoutingTest`).
- Real files: the UP press release, DMPI college notice, Bihar press release, Kundli salary list and diary read
  correctly, unknown rate ≈ 0, every legacy page → `LEGACY_FONT`. The Gazette goes to OCR for the Unicode-map errors
  of its other fonts; ChanakyaUni stays on its text layer.
- **Known limits:** rotated text in a table header converts in drawing order; a second "Chanakya" layout (one
  Rajasthan file, f = द्ध) is read with Gazette Chanakya's table and mangles that glyph (its unknown rate stays low,
  so it is not caught); typos in the source PDF are read as typed; NFC of nukta letters is P3-02's.
- Adding a font is a config entry when its layout matches a known encoding, else a new table and encoding.
