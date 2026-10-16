# ADR-028: Indic sentence rules after BreakIterator, NFC in text repair, Indic numbers at render time

**Status:** Accepted (P3-02, 2026-10-16).

## Context
Build Spec §5 step 7 splits sentences with ICU `BreakIterator`, with `।` as a sentence end and per-language
abbreviation lists; step 8 reads numbers "incl. Devanagari/Bengali digits"; ADR-020 left NFC to this ticket.
Measured on the JDK (unit tests) and known from ICU's rules (Android): both already end a sentence at the danda,
but both cut "डॉ. शर्मा" and "ए. पी. जे. अब्दुल कलाम" after every full stop, read a verse number "॥ १ ॥" as a
sentence of its own, and the JDK also breaks before a closing quote (`है।" उसने`) and before a comma after a danda.
Hindi and Bengali typed in Word often use the ASCII "|" for the danda, which no splitter treats as one and which
voices read as a symbol. PDFs and OCR spell some letters in different Unicode forms ("ো" as one code or two). And
the coming Hindi and Bengali voices differ on digits: espeak-based ones read them, character-based ones (MMS) only
know the letters they were trained on.

## Decision
1. **Splitting stays BreakIterator, followed by `IndicSentenceRules`** in `:pdf` (`BreakIteratorSentenceSplitter`):
   a break after every danda (plus closing quotes and spaces), closing quotes kept with their sentence, no break
   before a comma-like mark, no break after a listed abbreviation or initial (`SentenceConfig`, `:core`), and a
   verse number between dandas joined to its verse. The rules only move breaks, so units still cover the text in
   order, and the JDK and ICU split the same way.
2. **Initials are English letter names in Devanagari and Bengali** ("ए", "बी", "এ", "বি"), not every single
   syllable: Hindi text that uses a full stop for the danda ends sentences with "है.", which must still split.
   When a word is ambiguous ("ई." for ईस्वी) two sentences become one unit, which is cheaper than a cut name.
3. **NFC is the first text repair** (`UnicodeRepair.compose`, before folding), one cluster (a character and its
   combining marks) at a time; a cluster whose form changes maps all its new units to its first source, so glyph
   boxes and `charStart` stay valid (ADR-018, ADR-020). OCR text was already NFC (P3-05). NFC decomposes the nukta
   letters Unicode excludes from composition (क़, ड़, য়); that is the normal form every NFC text uses.
4. **"|" and "||" after Devanagari or Bengali text become "।" and "॥"** in the same repair step, when followed by a
   space, the end, a closing quote or more Indic text.
5. **Numbers are read as Hindi or Bengali words at render time** (`IndicNormalizer`, `:speech`), applied by
   `RoutedSpeech` to each Hindi or Bengali part (ADR-019, ADR-026): Devanagari, Bengali and ASCII digits; 0–99 by
   table, then hundred, thousand, lakh, crore; years 1100–1999 as "उन्नीस सौ सैंतालीस"; decimals digit by digit;
   ₹ amounts with paise; percentages; ranges; codes and leading zeros digit by digit. `ReadingUnit.text` keeps the
   digits as printed (display, fingerprints).
6. **`PIPELINE_VERSION` 7:** NFC, pipe dandas and the new breaks change `:pdf` output.

## Alternatives considered
- **Own sentence splitter for Indic text** (split on danda, ?, ! only): loses ICU's handling of quotes, numbers and
  mixed English, and would diverge from the English path. Rejected.
- **ICU4J on the JVM for tests:** a new dependency (AGENTS.md) only to make tests match Android; the post-pass makes
  both agree without it. Rejected.
- **NFC on the whole page text before layout:** breaks the one-offset-per-glyph mapping layout and highlight rely
  on. Rejected.
- **Convert digits in `:pdf`:** the reader would show words the page does not print, and resume fingerprints would
  depend on normalizer changes. Rejected (ADR-019).
- **Leave digits to the voices:** works for espeak-based voices only; MMS voices skip them. Rejected.

## Consequences
- Resume positions saved before version 7 re-anchor by fingerprint (ADR-015), as after every bump.
- The golden corpus score stays 0.9268 at four decimals: its scoring already ignores Unicode form. Two documents
  gain a little because closing quotes no longer start the next unit (Moby-Dick 0.9993 → 0.9999, Hindi fpdf2
  0.9317 → 0.9321).
- The English normalizer, when P2-04 part 2 wires it, goes into the same `RoutedSpeech` normalizer map for English
  parts.
- Known limits: native Bengali initials ("আ. ক. ম.") and abbreviations not on the lists still split; a "|" between
  Hindi words in a table becomes a danda; dates like "15-08-1947" in Hindi read digit by digit.
