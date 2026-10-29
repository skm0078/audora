# ADR 2026-10-28-text-pages: the text view turned a screenful at a time

**Status:** Accepted (2026-10-28). Part 3b of the share plan (share cards, quote video, page flips), approved by the
maintainer for v0.1.0. 3a (ADR 2026-10-28-page-flip) turns drawn PDF pages; this turns the text view, which is how
EPUB, FB2, Kindle, TXT and other text books are always shown.

## Context
A text book scrolls. Many readers want a book's feel instead: a screenful, then a turn. The plan's rules: screen-sized
pages, the place kept by sentence (never by pixel offset, which font size and rotation change), and TalkBack told
where it is.

## Decision
1. **A "Turn pages" switch in the text view's look** (off by default, kept with the other look settings), so
   nothing changes for anyone until they choose it.
2. **The list stays the list.** The same `LazyColumn` of sentences or paragraphs, its own scrolling off; a turn moves
   it so the first sentence or paragraph not wholly on screen starts the next screen, so no line is cut in two. One
   taller than the screen is stepped through a screen's height at a time. Earlier screens are remembered, so a turn
   back returns exactly where each began. Measuring every page's text ahead (true pagination) was not needed for
   this and would cost a full text layout per page and per font change.
3. **Turning:** a sideways swipe (56 dp, so a slanted scroll or a tap never turns) or the arrows under the text. At a
   page's last screen a turn shows the book's next page; at its first, the page before opens at its last screen.
4. **The place is the sentence.** While reading aloud with Follow along on, the spoken sentence starts a screen of its
   own when it is not wholly on screen.
5. **TalkBack:** the line between the arrows says "Page 5 of 40", then "Page 5 of 40, continued", as a polite live
   region; the arrows are labelled "Previous screen" and "Next screen".

6. **A turn slides** (added 2026-10-28, after 3b): the new screen comes in from the side it was turned from, a fifth
   of the width, fading up, over 200 ms. It is the list's graphics layer only (a translation and an alpha read in the
   layer), so a turn lays out nothing again and each frame only redraws; with the phone's animations off, Compose
   finishes it at once. Turns made by reading on (Follow along) jump without a slide, so the text does not move
   about while the listener is listening.

## Not done (decided or flagged)
- A curl animation: done since as a flat fold on a Canvas, not a mesh (ADR 2026-10-29-page-curl).
- Volume keys turning screens: **not doing** (maintainer, 2026-10-28). A key that turned the screen and also
  changed the volume would fight with reading aloud.
- "Turn pages" as the default for text books: on since ADR 2026-10-29-page-curl (maintainer, 2026-10-29).

## Consequences
- No new dependency; nothing at cold start. A turn is one `scrollToItem` of an already composed list, then about
  12 frames of a layer-only slide.
- Taps still read from a sentence and long-presses open its menu; only a sideways swipe turns.
