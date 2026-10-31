# ADR 2026-10-30-whole-page-cards: a share card can show the whole page, as the listener picks

**Status:** Accepted (2026-10-30). Asked for by the maintainer, who also asked that the whole page and the third be
choices the listener makes on the card.

## Context
ADR 2026-10-28-page-snippet capped a boxed part of a page at a third, and ADR 2026-10-27-share-cards capped a quote at
60 words: a fair-dealing guard. Listeners want to share a page as it looks, the way they would photograph it. Still,
the guard is the right first choice: most shares are a passage, and a short one reads better on a phone.

## Decision
1. **A "Card shows" choice on the card screen: Selection (the default) or Whole page.** It shows for a page-part card
   and for a quote card whose page the reader sent along, and never for a video (a video reads the lines picked).
   The last pick is kept for the next card (`CardSpanPreferences`, its own small DataStore), so a listener who always
   shares pages is asked once. The boxed part keeps its third limit; the whole page is a separate, explicit pick.
2. **A drawn page (PDF):** `PageSnippets` keeps the page as drawn beside the part it cut, and the card shows it as
   large as the space allows, never stretched, the same as a part.
3. **Any book, as text:** the reader sends the shown page's sentences with the lines (`ReaderState.pageText`). The
   card sets them without the quote mark, stepping down from 22 to 7 card sp (21 px on the 1080 px picture) with
   tighter lines (`QuoteFit.PAGE`). A page is cut at `ShareCardConfig.maxPageWords` (400); when the page is still too
   long for the size picked it ends in "…" and the screen says so, pointing to Story, which holds the most.
4. **Nothing else changes:** the card keeps the ListenLock mark, the reader's colours (ADR
   2026-10-29-share-credit-and-link) and the credit; the message keeps the credit and link. Video stays the lines.

## Alternatives
- **Lift the third limit on the box:** a box dragged to the page's edges is fiddly; one tap on "Whole page" is not.
- **A separate "Share this page" row in the Share choices:** one more row in a list already seven long; the card
  screen is where the listener sees what is shared, so the choice sits there.
- **Shrink the font with no floor:** a 4 sp page is a grey block; a floor with an honest "does not fit" note is clearer.

## Consequences
- Memory: while the card screen is open, the drawn page is held beside its part (a page at screen width, about 6 MB on
  a 1080 px phone, instead of about 2 MB for a third). It is let go when the screen closes. A text page adds at most
  8,000 characters to the screen's intent.
- Perf: nothing at cold start or while reading. The card screen reads one more DataStore value once; the text page
  is laid out a few more times while its size steps down (about 15 steps, only while "Whole page" shows).
- Copyright stays the listener's call: the credit and the book's name are always on the card, as before.
