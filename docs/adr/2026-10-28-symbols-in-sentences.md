# ADR 2026-10-28-symbols-in-sentences: symbols in sentences are said in the part's language, or left out

**Status:** Accepted (2026-10-28). Extends ADR-019 (normalize at render time) and RI-7b (equations read as written).

## Context
A listener heard "unrecognised characters" and arrows, maths and equations that "fail miserably". The normalizers read
symbols only in display equations (`MathSymbols`, `TextRole.EQUATION`); in a sentence only `& # × + = ~ ± @` were read.
Everything else reached the voice as written: an espeak-based English voice skips most signs or says nothing useful,
and a character-based Hindi or Bengali voice drops every character it was not trained on. Measured on 53 test
sentences in English, Hindi and Bengali (arrows, comparisons, maths signs, Greek, powers, subscripts, emoji, stars,
private-use glyphs from broken fonts, "* * *"): 49 handed the voice at least one symbol it cannot say. After: 0.

## Decision
1. **`SymbolWords`** reads a sentence's symbols in the part's language, in two passes around the number rules:
   - before them, powers and subscripts (`25 m²` "square metres", `10⁶`, `x²`, `H₂O` "H 2 O"), fractions (`2¾`),
     micro units and ohms; a superscript after a word longer than three letters is a footnote mark, left out;
   - after them, rules of one repeated mark (`_____`, `* * *`) become a pause, ASCII arrows and comparisons (`->`,
     `<=`, `!=`) their signs, `<` `>` between operands, `^` and `*` between numbers are read, then each sign in the
     language's table (arrows, comparisons, operators, set and logic signs, geometry, `§ © ✓ ✗`, Greek letters).
   Hindi and Bengali run both passes after their numbers, so their unit rule still sees `37°C` and `m²`, then read the
   digits a power leaves in a second pass.
2. **Words follow the text's order** ("x ≤ 5" "x at most 5"; Hindi "दिल्ली → मुंबई" "दिल्ली से मुंबई"), as equations
   already did. A sign without a word in Hindi or Bengali (a left arrow) is left out, never said in English to a voice
   that cannot read English.
3. **`SpeakableText`** is every normalizer's last step: what is left that is not a letter, digit, mark on a letter,
   space or phrasing punctuation is left out (emoji with their selectors and joiners, stars, private-use glyphs, "�",
   daggers, stray `* # @ ^ _`); bullets, pipes and middle dots between words become a comma. ZWJ and ZWNJ after a letter
   stay (Indic clusters).
4. **Equations keep their own readings** (`→` "implies", `/` "over") in `MathSymbols`, whose tables now hold only the
   signs an equation reads differently; every other sign falls back to `SymbolWords` in the part's language, so a Hindi
   equation's Greek letters are "अल्फ़ा", not an English "alpha".
5. **A sentence of only symbols hands the voice nothing.** `RoutedSpeech` no longer falls back to the raw text when
   normalization leaves it blank; every engine already renders blank text as silence.

## Consequences
- Studio audio version 8: the same sentence can now sound different.
- `:pdf` is unchanged (the page still shows every symbol; resume anchors unchanged), so PIPELINE_VERSION stays.
- The listening corpus should not drop: its scripts already say "less than", "greater than" and "not equal to" where
  the pages have `<`, `>` and `≠`.
- Cost: plain sentences pay one character scan (measured the same as before within noise, about 0.07 to 0.35 ms per
  sentence on a desktop JVM); sentences with symbols about 0.02 to 0.05 ms more. Synthesis takes hundreds of ms.
- Not done: chemistry coefficients ("3H₂" stays "3H two"), units with a power in Hindi and Bengali beyond their unit
  table ("मी²" is "मी का वर्ग"), primes for feet and inches.
