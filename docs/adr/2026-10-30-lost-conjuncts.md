# ADR 2026-10-30-lost-conjuncts: a Hindi or Bengali page that lost its conjuncts goes to OCR

**Status:** Accepted (2026-10-30). Found on a Bengali storybook from a Kolkata publisher that the maintainer said the
app "butchers", while the same voice read clean text well.

## Context
The book is typed, not scanned, but its font's Unicode map leaves every conjunct glyph unmapped. Its text layer holds
5 viramas in 272,000 Bengali letters: "সাহিত্য" extracts as "সাহিত", "প্রথম প্রকাশ" as "থম কাশ", the author's
own name loses its "স্ত". Its pre-base vowel signs are also in visual order, which `IndicReorder` already fixes.

The scorer sent such a page to OCR only when something left was impossible (`INVALID_INDIC`: on this book, about
half the pages). The rest are made only of possible letters, so they stayed on the text layer. The book-wide OCR
sample (ADR-011 §3) would reject the book, but only on a phone that has the OCR models. Without them the book is
`UNVERIFIED` and half its pages were read with every conjunct missing, with no notice.

Separately, the book draws every danda with "৷" (U+09F7, a currency sign of the same shape): 7,391 of them. The text
repair turned only "|" into a danda, so its sentences never ended and ran on for a whole paragraph.

## Decision
1. **`RouteReason.LOST_CONJUNCTS`.** An Indic page with at least `QualityConfig.minConjunctCheckLetters` (500) Indic
   letters and a virama share below `QualityConfig.minViramaShare` (0.2 %) goes to OCR. The virama is how Unicode
   spells every conjunct, ya-phala, ra-phala and reph, so a font that drops them drops the virama.
   It is measured on the text as it will be read: after the legacy conversion and the reorder pass.
2. **`UnicodeRepair.dandasFromBars`.** A "৷" right after a Bengali letter or sign and before a space, a line end or a
   closing quote becomes "।". After a digit it stays ("১৷" is a currency fraction), and so do a bar after a space
   and one inside a word.
3. `PIPELINE_VERSION` 14: these pages read differently, so resume anchors move to the new text.

## Why these thresholds
Measured virama shares: the golden Unicode documents hold 3.7-8.6 % on every 400-letter stretch; the story eval
passages (plain prose and dialogue) hold at least 0.5 % on every 600-letter stretch (3 viramas), and 0.25 % at worst
on a 400-letter one. Every page of the broken book holds 0-0.5 % (at most 3 in 600). Over the test-pdfs folder (text by
pdftotext, 2,000+ pages of 500+ Indic letters), the only pages under 0.2 % are known-broken files: the Hindi HUD
factsheet (already a golden broken-map document, plain and encrypted), the agri manual exported by "Print to PDF"
(broken the same way as the book, 5 of 5 pages caught), and the Chanakya gazette, whose legacy runs are converted
before the scorer counts.

## Alternatives
- **Rely on the OCR sample alone:** it needs the OCR models on the phone. Without them, a broken book is read
  garbled with no sign that anything is wrong.
- **A dictionary check (share of unknown words):** stronger in theory, but it needs a word list per language at
  routing time and is slower. The missing virama is a one-pass count and catches this whole class.
- **Learn the font's glyph map from one OCR'd page** (ri-offline-next option 1): it would read such books exactly and
  fast, but it is about a week of work. It stays the next step for this class of book, after this release.

## Consequences
- A wrongly flagged page (a long page with no conjunct at all, which none of the measured text has) goes to OCR: it
  reads a little slower, may show the OCR badge, and on a phone without OCR models shows the "download OCR" card.
  It is never read garbled.
- On a phone without OCR models, a book like this one now shows the "download OCR" card on every page instead of
  reading half its pages wrong. That is the honest outcome.
- The "৷" repair also runs on OCR text, after OCR's own repair (`OcrAaBars`, `OcrDandas`) has turned a misread
  vowel-sign bar into "া". A confident OCR word that still ends in "৷" before a space now ends a sentence.
- Perf: one more character count over text the scorer already walks; no new allocation per page. Nothing at cold
  start. Pages newly routed to OCR take the OCR time (about 3-5 s a page on the test phone) instead of text-layer
  time, which is the price of reading them right.
