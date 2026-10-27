# The narrator rises and falls across a paragraph (2026-10-26, ADR 2026-10-26-natural-narration)
- Q-3: `NarrationArc` shapes each narrated sentence by its place in the paragraph (`ParagraphPlace`, from the quote
  carry): pitch from a little high to a little low, slower at a section's start and a paragraph's end, longer comma
  pauses in long sentences, and a small steady variation per sentence. Only with "Read to suit the book" on, and
  only for narration (not quoted speech, headings, tables, captions, equations or footnotes).
- Stress on single key words is not possible with the shipped voices (no emphasis input); recorded in the ADR.
- Studio audio version 5. "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-natural-narration.txt`.
  `:pdf` not changed. No new dependency.
- **Could affect:** how narration sounds with "Read to suit the book" on, studio re-renders, the delivery passed to
  `RoutedSpeech` (pitch and rate). Quoted speech, character voices, headings and playback timing are unchanged.
- **Verified in CI:** `NarrationArcTest` (arc steps down a paragraph, section and paragraph start speeds, long
  sentence pauses, steady per-sentence variation, quoted speech and non-narration left alone, shaping stacks on a
  planned delivery), `QuoteCarryTest` (paragraph places and after-heading marks).

**Real-device checks (the test phone):** with "Read to suit the book" on, play a few paragraphs of an English novel,
a Hindi and a Bengali page. Each paragraph should start a touch higher and land lower, the first sentence after a
heading a little slower. Turn the setting off: the narration should sound as before.
