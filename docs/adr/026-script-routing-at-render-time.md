# ADR-026: Script routing splits a sentence's audio at render time, not its ReadingUnit

**Status:** Accepted (P3-01, 2026-10-16).

## Context
ADR-005 (Build Spec §3) routes by Unicode script: Latin → English, Devanagari → Hindi, Bengali → Bengali, and "v1
splits English runs of ≥3 words inside Indic sentences". Until now `:pdf` gave each `ReadingUnit` one language, the
dominant script of the sentence, and one voice read all of it. Bilingual PDFs break that: in the UDHR test file an
English clause ending in a comma runs straight into a Hindi sentence, so one unit holds both, and the Hindi voice
read the English (or the English voice skipped the Devanagari). The per-language voice choice itself already exists
(Settings → Voices, P4-01, ADR-022).

## Decision
1. **The router lives in `:speech`** (`speech.route.ScriptRouter`): pure Kotlin, deterministic. It cuts a sentence
   into script runs; punctuation, spaces and ASCII digits stay with the run before them, and a cut falls after the
   last space before the new script.
2. **Thresholds:** in a mostly Hindi or Bengali sentence, an English run of fewer than
   `ScriptRoutingConfig.minEnglishWordsInIndic` (3) words stays with the Indic voice ("PDF", "Article 12"). Indic
   text inside an English sentence always gets its own voice, since an English voice cannot read it at all.
3. **Routing happens at render time** (`RoutedSpeech`, called by `EngineRenderer` in `:playback`). Each part is
   rendered by its language's voice and the parts are joined into one clip, resampled up to the highest sample rate
   among them. `ReadingUnit` is unchanged: the sentence stays the unit of playback, highlight and resume
   (ADR-015), `:pdf` output does not change, and no `PIPELINE_VERSION` bump is needed.
4. **Fallback:** if a part in another language than the sentence's own has no voice (no Hindi voice on the phone),
   the whole sentence is read with its own language's voice, as before P3-01, and a warning is logged with the
   page, sentence and reason. A failure of the sentence's own language still fails the sentence.
5. **One script-to-language map** (`ScriptLang`, `:core`) serves both `:pdf`'s unit language and the router.

## Alternatives considered
- **Split units in `:pdf`** (one `ReadingUnit` per script run): changes `:pdf` output, so a `PIPELINE_VERSION` bump,
  golden re-scoring and re-anchoring of every saved position; and highlight would jump mid-sentence. Rejected.
- **One playlist item per part:** keeps clips separate (no resampling) but breaks the one-unit-one-item mapping the
  render queue, resume recorder and sleep timer rely on. Rejected.
- **Downsample to the lowest rate:** linear interpolation down aliases; up is safe. Rejected.

## Consequences
- A mixed sentence costs one engine call per part (usually two or three), all on the render thread already
  budgeted for one sentence.
- P2-04 part 2 (normalizer wiring) applies the English normalizer per English part inside `RoutedSpeech`, so
  numbers in an English clause are read the English way and Indic parts wait for P3-02's normalizers.
- P3-02 (Indic splitting, digits, NFC) and P3-06 (Hindi and Bengali voices) need no routing changes.
