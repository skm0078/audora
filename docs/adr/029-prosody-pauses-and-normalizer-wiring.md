# ADR-029: Pauses as silence at the end of each clip; list items from layout; every voice normalized

**Status:** Accepted (P2-04 part 2, 2026-10-16).

## Context
Build Spec §5 step 9 asks for pauses after headings (~600 ms), paragraphs (~400 ms) and list items. Since P2-02,
`ReadingUnit.pauseAfterMs` is set from the paragraph a sentence ends, but the values were 0 and nothing played them.
List items were not detected at all: P2-03 drops the bullet glyph, and a hanging indent made each continuation line
look like a new paragraph, so "• Apples / • Pears" ran together as one sentence. ADR-019 put the English normalizer
in `:speech` at render time but left it unwired; P3-02 then wired the Hindi and Bengali normalizers per script part
into `RoutedSpeech`. P4-02 sizes the render-ahead window from each voice's measured speed (ADR-027).

## Decision
1. **A pause is silence appended to the sentence's clip** in `EngineRenderer` (`PcmAudio.withSilenceAfter`). The
   player, `TtsDataSource`, highlight, sleep timer and resume keep working on one clip per sentence; nothing in
   Media3 learns about pauses. The pause adds to the short silence voices already end with.
2. **Defaults in `ReadingUnitConfig`:** sentence 0, paragraph 400, heading 600 (every sentence of a heading), list
   item 300 (its last sentence). Starting points from the spec, to be tuned by ear in the phone session.
3. **List items come from layout:** a line starting with a bullet, or with a label ("1.", "12)", "(a)", "(iv)",
   "a)") after a line that ends a clause or inside a list, starts a `ParagraphKind.LIST_ITEM`. Inside an item,
   indented continuation lines stay in it; a line back left of them ends the list. `PIPELINE_VERSION` 8, since
   units of lists change.
4. **`ReadingUnit.kind` (`UnitKind`: body, heading, list item)** carries the paragraph kind to playback, which maps
   headings to `TextRole.HEADING` for the normalizer (Roman numerals at the start of a heading).
5. **Every language has a normalizer** in `RoutedSpeech` (`speechNormalizers()`): English (P2-04 part 1) next to
   Hindi and Bengali (P3-02), applied to each script part with that part's language.
6. **Render speed and render-ahead:** the speed tracker measures the voice's audio without the pause (silence costs
   no render time; counting it would make a voice look faster). The window and the start hold count a sentence's
   pause as playable time but not as render time.

## Alternatives considered
- **Pauses in `TtsDataSource` or as separate silent media items:** keeps silence out of the render cache, but adds
  a second timeline (pause vs sentence) to the player, highlight and resume for a saving of ~30 KB per pause. Rejected.
- **Trim the voice's own trailing silence and set the total gap:** exact gaps, but trimming risks clipping soft word
  endings and varies per engine; not measurable without hearing it. Deferred to the phone session if gaps sound
  uneven.
- **Number labels anywhere at a line start:** splits wrapped prose ("… see step\n3. We …"). Rejected for the clause
  or in-list condition; bullets are unambiguous and always count.

## Consequences
- A saved position re-anchors once by fingerprint after the update (`PIPELINE_VERSION` 8).
- A list item that wraps without a hanging indent and is not followed by a gap may carry the next body line; the
  spoken text is unchanged, only the pause lands later.
- Pause lengths are untested by ear: the batched phone checks include listening to headings, paragraphs and lists.
