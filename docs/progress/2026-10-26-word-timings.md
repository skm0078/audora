# The word highlight follows the voice's pauses (2026-10-26, ADR 2026-10-26-word-timings)
- The voices give no word times (sherpa-onnx returns samples only), so `WordTimes` reads them from each sentence's
  audio: the speech's start and end and its pauses, with the sentence's punctuation pinned to the pauses in order.
  Between them the words share the time by their letters, as before. `SentenceTiming` carries the starts; the reader
  uses them when they match the sentence's words and falls back to the letter spread otherwise.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-word-highlight-follows-voice.txt`. `:pdf` not changed.
  No new dependency.
- **Could affect:** the word highlight (reader setting "Highlight each word"), the sentence timing sent to
  the reader. Playback, the sentence highlight and the sleep timer do not read word starts.
- **Verified in CI:** `WordTimesTest` (a comma waits through the pause, a breath with no mark is passed over, a mark
  far from any pause is not forced, dandas, silence, word counting), `SentenceTimingTest` (starts round-trip, old
  lines still read, broken starts rejected, word at a clip time), `SpokenWordTest` (voice times used, wrong count
  falls back), `EngineRendererTest` (a start per written word is recorded).

**Real-device checks (the test phone):** turn on Highlight each word; play an English, a Hindi and a
Bengali page with commas and dandas. The highlight should stay on the word before a comma during its pause and move
on when the voice does. Try a table row and an equation: the highlight still moves (by letters).
