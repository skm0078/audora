# ADR-019: Speech normalization runs at render time in :speech, not in the :pdf pipeline

**Status:** Accepted (P2-04, 2026-10-15).

## Context
Spec §5 lists "Normalize for speech" as step 8 of the `:pdf` pipeline, and the §4 sample calls `ReadingUnit.text`
"normalized, speakable text". Since then three things pin the text down:
- `ReadingUnit.text` is what the reader shows (P1-05) and what resume fingerprints hash (C8, ADR-015). Rewriting
  "₹1,50,000" into "one lakh fifty thousand rupees" there would show the reader spoken words instead of the page,
  and every normalizer tweak would change fingerprints and force a `PIPELINE_VERSION` bump.
- ADR-012 requires the normalizer before *every* engine, because Supertonic has none of its own.
- AGENTS.md forbids `:pdf` from depending on `:speech`, and normalization is language and engine work.

## Decision
1. **The normalizer lives in `:speech`** (`speech.normalize`): `TextNormalizer.normalize(text, role)`, with
   `EnglishNormalizer` as the first implementation. Pure Kotlin, no Android types, table-driven tests.
2. **It runs at render time,** on the text handed to the engine, never on `ReadingUnit.text`. The reader keeps
   showing and highlighting the extracted text; `charStart`/`charEnd` and fingerprints are untouched, so a better
   normalizer never needs a `PIPELINE_VERSION` bump.
3. **A `TextRole` (BODY, HEADING) is the seam for layout-dependent readings** (Roman numerals at the start of a
   heading). Units carry no role until P2-02 detects headings, so render passes BODY until then.
4. **Indian-English defaults, all in `NormalizerConfig` (`:core`):** day-first numeric dates, lakh/crore for rupee
   amounts (grouping wins: 1,50,000 is Indian, 150,000 international), British unit spelling, links read as "link".

## Alternatives considered
- **Normalize in `:pdf` step 8 as written:** breaks the reader's display and resume stability (above), and needs a
  `:pdf` → `:speech` dependency or the normalizer in `:core`. Rejected.
- **Store both texts on `ReadingUnit`:** doubles memory per page and still invalidates cached units on every
  normalizer change, for no gain; rendering is the only consumer. Rejected.
- **Rely on each engine's own front end:** Supertonic has none (ADR-012), and espeak-ng and Android voices disagree
  on dates, ₹ and lakh. Rejected.

## Consequences
- Normalization costs a few regex passes per sentence (≤ 400 chars) on the render thread: negligible next to
  synthesis.
- Wiring (`EngineRenderer` in `:playback`) and prosody pauses after headings, paragraphs and list items come in a
  follow-up PR once P1-06 is on dev; pauses after headings need P2-02's headings.
- Hindi and Bengali normalizers (Indic digits, P3-02) implement the same `TextNormalizer`.
