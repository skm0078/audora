# ADR 2026-10-26-word-timings: the word highlight is timed from the voice's own audio

**Status:** Accepted (2026-10-26). Extends plan item 14 (word highlight, version one) and ADR-072 (its polling).

## Context
The reader lights the word being said. Version one spread each sentence's speech evenly over its letters, so it
drifted by about ±150 ms, worst at a comma: the voice pauses there, the letters do not, and the highlight ran ahead
into the next words during the pause. CF-10 in the coverage matrix asked for real word times. A spike on 26 Oct read
sherpa-onnx 1.13's Kotlin API: `GeneratedAudio` holds only `samples` and `sampleRate`, and no `generate` call returns
word or phoneme durations. Supertonic, Chatterbox and the phone's own voices give none either.

## Decision
1. **Read the times off the audio.** After a sentence renders, `WordTimes` measures the voice's loudness in 10 ms
   frames, finds where the speech starts and ends, and finds its pauses (quiet runs of 70 ms or more inside it).
2. **Pin punctuation to pauses.** Each word that ends a clause (`, ; : . ! ? । ॥ — – …`, after any closing quote) is
   matched, in order, to the pause nearest where the letters put it, within a quarter of the sentence (250 ms at
   least). A small table picks the matching with the most total closeness, so a breath where no mark is gets passed
   over and a mark the voice ran through is not forced onto a far pause.
3. **Letters share the rest.** Between two pinned pauses (or the speech's ends) the words share the time by their
   letters, digits and vowel signs, as before. The highlight holds a clause's last word through the pause and starts
   the next word when the voice does.
4. **Same words as the reader.** Words are what `Char.isWhitespace` separates, on both sides, so word i of the timing
   is word i the reader lights. The starts ride in `SentenceTiming` as an optional seventh field.
5. **Fallback stays.** A sentence the voice says differently from its text (a table row with its column names,
   "Equation 3", a citation the skips removed), studio audio, and a timing whose word count does not match the text
   all keep the letter spread.

## Alternatives
- **Durations from the model:** not exposed by sherpa-onnx; patching its C++ and JNI for VITS and Kokoro would cover
  only some voices and fork a native library we take as a pinned AAR.
- **Forced alignment with a speech recogniser:** accurate, but a second model per language and far more CPU per
  sentence than the voice itself on a low-end phone.
- **Per-word synthesis:** exact times, but every word loses its neighbours' intonation, which is the opposite of
  Q-3's aim.

## Consequences
- One pass over each sentence's samples and a match over at most a few dozen marks: well under a millisecond next to
  a render of hundreds.
- Inside a clause the highlight still moves by letters; it can lag or lead a long word slightly, but no longer drifts
  across a pause.
- `PIPELINE_VERSION` is unchanged: `:pdf` is not touched.
