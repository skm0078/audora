# ADR 2026-10-28-page-snippet: share part of a drawn page, cut from the page on screen

**Status:** Accepted (2026-10-28). Part 1b of the share plan (share cards, quote video, page flips), approved by the
maintainer for v0.1.0.

## Context
A quote card retypes the words in the card's font. For a page whose look matters (a poem's layout, a diagram, a
scanned book, a page in a script the card fonts do not cover) the page itself is the thing to share. The plan's
rule: at most a third of a page, always with the book, author and page under it (the same fair-dealing guard as the
60-word quote).

## Decision
1. **Entry:** "Share part of the page" in the sentence menu's Share choices, for a sentence placed on a drawn page
   (it has line boxes). The box starts around that sentence's lines, so the usual case is two taps, and TalkBack
   users get a sensible box without dragging.
2. **The box's rules live in `:core` (`SnippetBox`, unit tested):** inside the page, each side at least 8 % of the
   page's, the area at most a third. Past the limit while a corner is pulled, the side the finger moved less gives
   way, so the pulled side follows the finger. A sentence taller than the limit keeps its first lines.
3. **Cut from the page as drawn**, in memory (`PageSnippets`), and handed to the card screen: a locked PDF needs no
   password again, nothing renders twice, and nothing is written until the listener presses Share. The card screen
   lets it go when it closes.
4. **The card:** the snippet as large as the space allows, never stretched, then the same credit and ListenLock mark
   as a quote card; every size and background of the picture card applies. No video for a snippet.

## Alternatives
- **Render the region again at full resolution:** sharper on a zoomed-out page, but needs the document (and its
  password) in the card screen; the page is already drawn at screen width, which is enough for a phone picture.
- **Free-form selection by long-press drag on the page:** conflicts with tap-to-read, long-press and page swipes.
- **Write the cut to a file for the card screen:** survives Android stopping the app, costs a write per snippet; the
  card screen instead says the snippet is gone and the listener picks it again.

## Consequences
- Memory: the cut bitmap (a third of a page at screen width, about 2 MB) while the card screen is open. Nothing at
  cold start; no new dependency.
- While the box shows, the page neither turns nor reads on a tap; Back or Cancel closes it, a page turn drops it.
- Text books (EPUB, TXT) have no drawn page, so no entry; flagged for the text-pages work (3b).

**Update 2026-10-30:** the card screen can now show the whole page instead of the box, as a choice the listener makes
(ADR 2026-10-30-whole-page-cards). The box itself keeps its third limit.
