# Symbols and equations read aloud (2026-10-28, ADR 2026-10-28-symbols-in-sentences)
- Arrows, comparisons, maths signs, Greek letters, powers, subscripts and fractions in sentences are said in the part's
  language (`SymbolWords`); emoji, stars, broken-font glyphs and stray signs are left out (`SpeakableText`); a line of
  only symbols ("* * *") is silence. Equation tables now hold only their own readings. Studio audio version 8.
- What's new entry `2026-10-28-symbols-in-sentences`. `:pdf` not changed (PIPELINE_VERSION unchanged). No new
  dependency.
- **Could affect:** everything the English, Hindi and Bengali normalizers read (numbers, units, equations, tables), the
  listening score, and character voices (same normalizers).
- **Verified in CI:** `SymbolWordsTest`, `SpeakableTextTest` (53 test sentences in three languages: no symbol a voice
  cannot say reaches it; before the fix 49 did), `MathSymbolsTest`, `EnglishNormalizerTest`, `RoutedSpeechTest` (a
  symbols-only sentence is handed to the voice as nothing), the listening corpus, release journey step 43 (the
  installed app's bundled voice is handed words and makes sound; "* * *" is silence).

**Real-device checks (the test phone):** play a page with arrows (→), ≤ or ≥, x², H₂O and an emoji, in English, Hindi
and Bengali: each is said as a word or skipped, never as a noise or a pause-less stumble. A novel's "* * *" scene break
is a short silence.

**Cost:** no new dependency or asset; a few KB of tables. Plain sentences: no measurable change; sentences with symbols
about 0.02 to 0.05 ms more normalizing on a desktop JVM, next to hundreds of ms of synthesis.
