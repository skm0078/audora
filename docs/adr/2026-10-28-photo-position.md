# ADR 2026-10-28-photo-position: the listener places their own picture behind a card or video

**Status:** Accepted (2026-10-28). Follows the share plan's 2b (ADR 2026-10-28-quote-video-screen), approved by the
maintainer for v0.1.0: "Your own picture" was cropped to its middle, so a face or a view off centre could be cut off.

## Context
A card or quote video is a fixed shape (story, square, post) and a picture rarely is, so the picture is cropped to fill
it. Which part shows is the listener's to choose. The quote video's backdrop is the card captured as drawn, so placing
the picture on the card places it in the video too.

## Decision
1. **Drag the picture on the card.** With "Your own picture" chosen, a drag on the card moves the picture under it;
   the hint under the swatches says so. The card is drawn at its full size and scaled to fit the screen, so the drag
   is read in card pixels and a finger's move is the picture's move on the shared card.
2. **The place is a focus point, not an offset.** `PhotoFocus` (`:core`, pure JVM, unit tested) keeps 0..1 across and
   down: the point of the picture kept in view, as a crop alignment. It does not change with the card's shape or the
   screen's size, so switching story to square keeps the same part in view. A drag can only move the side the picture
   overflows, and stops at its edges, so no empty edge ever shows.
3. **TalkBack:** the card has four actions, "Move the picture left / right / up / down", a fifth of the way each.
4. **A new picture starts centred.** The place is kept for the screen's life with the picture itself, which is not
   saved across a recreated screen (the picture look falls back until one is picked again, as before).
5. **Only the listener's own picture moves.** The book's softened cover stays centred: it is blurred, so its middle
   is all that matters.

## Alternatives considered
- Pinch to zoom as well: a scale on top of the crop doubles what can go wrong (empty edges, a blurry over-enlarged
  picture in a 1080p video) for little gain on a 9:16 card; not done.
- A separate crop screen before the card: one more step and one more screen to translate; the card itself shows the
  result exactly.

## Consequences
- No new dependency; nothing at cold start. A drag redraws the card's picture, already decoded; no new bitmap.
- The video's backdrop is captured with the picture where the listener put it; the encoder is unchanged.
