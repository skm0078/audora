# ADR-076: Feelings you can hear: pitch on every voice

**Status:** Accepted (2026-10-25). Amends ADR-056's feeling effects.

## Context
Testers called the emotional reading dull. ADR-056 gave each feeling a nudge of speed (at most 8 %) and loudness
(at most 2.5 dB), and "Even loudness" levels each sentence first. Rendered side by side, the "before" and "after" of a
sad or happy line were hard to tell apart. Only Rasa Bengali acts feelings in a style of its own (ADR-057); every
other voice had no way to sound sad or bright.

What a listener hears first in an emotional line is pitch: sad speech sits lower, happy, surprised and scared speech
higher. Rendered samples with a small pitch shift per feeling (in the project's `audora/samples/character-voices`,
files `5-C-right-feelings`) made the feelings clear without making the voices sound like someone else.

## Decision
1. **Each feeling moves the pitch** (`FeelingEffect.semitones`): sad -1.5 semitones, soft -0.7, trailing -0.5,
   asking +0.5, loud and emphatic +1, scared +1.5, lively and surprised +2. The book's feeling strength scales it
   (a storybook 1.25, a story 1, general text 0.6, factual and sacred text 0), and `DeliveryConfig.maxSemitones` caps
   it at 2.5 (15 %), where ADR-071 found a voice stops sounding like itself.
2. **Speed, loudness and pauses are stronger:** sad 0.9 speed, -3 dB, 1.4 pause; scared 1.07, -2 dB; lively 1.06,
   +2 dB; loud +3 dB (the existing 3 dB ceiling stays).
3. **How the shift is made:** like a tape played faster or slower (`PcmAudio.pitched`). The voice is asked for
   `rate / pitch`, so the words come back to the planned length once shifted. No new dependency, no extra model, and
   the cost is one resample of the sentence.
4. **Only the quoted words are shifted** in a sentence with quotes, as with acted styles (ADR-058): in "“Don't go,”
   she sobbed" the plea is lower, "she sobbed" is plain. A sentence with no quotes ("She wept.") is shifted whole.
5. **A voice that acts feelings itself** (Rasa, the studio voice) is not shifted as well.

## Alternatives considered
- **Sonic's pitch (Media3) per sentence.** Same sound (it also moves formants), but it lives in `:playback` and
  runs on byte buffers; the resample is a few lines in `:speech` and testable on the JVM.
- **A pitch contour (falling for sad, rising for questions) inside the sentence.** Better in theory, but it needs
  per-word timing the voices do not give. Kept for later.
- **An emotional voice model for every language.** None with a commercial licence runs in real time on a phone
  for Hindi and English today; the optional "story brain" (LLM director) is a separate decision.

## Consequences
- Feelings are audible on every voice, including Supertonic Hindi and the English voices.
- A wrong feeling is now also audible. The rules still miss 63-68 % of feelings; "!"
  alone stays EMPHATIC, never joy (ADR-075's story set guards it).
- **Needs a phone check:** the shift on the test phone's speaker, for a sad and a lively passage in each language.
