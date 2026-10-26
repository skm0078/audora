# ADR 2026-10-26-natural-narration: the narrator rises and falls across a paragraph

**Status:** Accepted (2026-10-26). Builds on ADR-056 (reading to suit the book) and ADR-076 (a feeling's pitch).

## Context
Q-3 in the coverage matrix: narration sounded flat because every voice reads each sentence on its own, with the same
tune, so a page came out as one sentence shape repeated. A person reading aloud does three things the app did not:
starts a paragraph a little higher and settles lower by its end, slows at the start of a section, and takes a breath
in a long sentence. Q-3 also asked for stress on key words.

## Decision
1. **Each sentence knows its place.** The quote carry, which already walks a page's paragraphs, records each
   sentence's `ParagraphPlace`: its index, the paragraph's size, and whether the paragraph follows a heading on the
   same page (the page's first paragraph is never counted as after a heading, as the heading may be a page back).
2. **`NarrationArc` shapes the planned delivery.** After the `DeliveryPlanner`, with "Read to suit the book" on:
   - pitch steps evenly from +0.5 semitone (a paragraph's first sentence) to −0.6 (its last); a lone sentence takes
     the middle;
   - speed ×0.94 for a section's first sentence, ×0.98 for any other paragraph's first, ×0.96 for a paragraph's last;
   - a sentence of 24 words or more gets its comma pauses ×1.25;
   - every sentence varies by up to ±0.2 semitone and ±1.5 % speed, from a hash of its text, so it sounds the same
     every time it is read (studio audio and cached clips stay valid).
3. **Narration only.** Body text, list items and asides that are not quoted speech. Quoted speech keeps its own
   delivery and character voice; headings, tables, captions, equations and footnotes are left as planned.
4. **No word stress.** No engine we ship takes emphasis marks (SSML or otherwise); stressing one word would mean
   splitting the sentence around it, which costs the neighbouring words their intonation. Not attempted.
5. Studio audio version 5: books rendered before this are re-rendered with the arc.

## Alternatives
- **Pitch contour inside a sentence:** needs per-phoneme control the voices do not expose.
- **Bigger shifts:** past about a tone the arc sounds sung; the tunables are capped at 2 semitones in `NarrationConfig`.
- **Random variation:** would change a sentence between plays and invalidate cached audio; the hash keeps it steady.

## Consequences
- No new dependency or model; the shift reuses the feeling pitch path (`SpeechRequest.pitch`), and voices that act
  feelings in their own style ignore pitch as before.
- Every tunable lives in `NarrationConfig`; the arc is off with "Read to suit the book" off.
