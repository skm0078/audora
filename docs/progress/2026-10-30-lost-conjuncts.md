# Bengali books whose font drops conjuncts go to OCR, and "৷" ends a sentence (2026-10-30, ADR 2026-10-30-lost-conjuncts)
- Found on a Bengali storybook the maintainer said the app "butchers". Its text layer has every conjunct missing (5
  viramas in 272,000 letters) and uses "৷" for every danda.
- `PageQualityScorer`: new `RouteReason.LOST_CONJUNCTS` and `PageSignals.viramaShare`; tunables
  `QualityConfig.minConjunctCheckLetters` (500) and `minViramaShare` (0.002). On the book's own text in 30-line
  pages: 97 pages went to OCR before (impossible sequences) and 111 were read garbled. All 208 go to OCR now.
- `UnicodeRepair.dandasFromBars`, called from `TextRepair` after the pipe repair: "৷" after a Bengali word becomes
  "।". It stays after a digit (a currency fraction), after a space, and inside a word.
- `PIPELINE_VERSION` 14. The page log line now includes `virama=`.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-lost-conjuncts.txt`. No new dependency.
- **Could affect:** which Hindi and Bengali pages are read from the text layer or by OCR; sentence ends in Bengali
  text that uses "৷"; resume anchors in Indic books (moved to the new text by the version bump).
- **Verified in CI:** `PageQualityScorerTest` (lost-conjunct Bengali and Hindi pages go to OCR in their own language;
  the same page with its conjuncts, and a page too short to tell, stay), `UnicodeRepairTest` (eight "৷" cases, sources
  kept), `GoldenRoutingTest` (the sample-only check turns the new rule off too), and the golden corpus.

**Real-device checks (the test phone):** open the storybook with the Bengali OCR model downloaded. Every page shows
the OCR badge, and the reading has its conjuncts ("সাহিত্য", "প্রথম"). Delete the OCR model and open it again: each
page shows the "download OCR" card instead of reading. Open a Unicode Bengali book (the magazine in the test PDFs):
no OCR badge, and the reading is the same as before.
