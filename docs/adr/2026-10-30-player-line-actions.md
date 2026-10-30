# ADR 2026-10-30-player-line-actions: The player names its line actions, and the reader's tips can be found again

**Status:** Accepted (2026-10-30). Fifth of the findability PRs from the maintainer-approved audit (layout rule:
sidebar and drawer for places and major features, sheets for quick changes, top bar for search, filter and ⋮).
Builds on ADR 2026-10-29-share-credit-and-link (the Share choices, `BookNotesActions.openShare`).

## Context

The player showed the line being read, but bookmarking, highlighting or sharing it was hidden: a small icon beside
the title for the bookmark, and a long-press on the sentence for highlight and share. The reader's gesture tips
showed once and could never be seen again. The page/text toggle in the reader's top bar was an icon only, so a
listener could not tell which view it would switch to without trying it.

## Decision

- **Three named buttons under the sentences:** Bookmark, Highlight and Share, each an icon over a label that says
  its state ("Bookmarked", "Highlighted"). They act on `ReaderState.sentenceHere`, the same sentence "Bookmark here"
  marks. Share calls `openShare` for that sentence (the #117 choices), or shares the book while the page has no words.
  Bookmark and Highlight wait (greyed) until then. Hidden in kids mode, as the long-press menu is.
- **The bookmark moves** from beside the title into the row, so one row holds every action on the line. Its
  descriptions are kept, so TalkBack users hear the same words.
- **Highlights show in the player:** the player now places the page's highlights (`PlaceHighlights`), so the
  Highlight button can say "Highlighted" for the line.
- **"Gestures and tips" in the reader's ⋮:** shows the tips card again until Got it (`GestureTipsState.show`).
- **The view toggle is named:** a text button with its icon and "Text" or "Page" (the view it switches to). One
  named button, not a two-part switch, because the top bar already holds Bookmarks, Chapters, Share and ⋮, and a
  two-part switch would leave the book title about 50dp on a phone. The maintainer chose this on 2026-10-30.
- **Guarded by** `PlayerLineActionsTest` (labels, highlight toggles and says so, share opens the line's choices,
  before words only the book's share works) and release journey step 58.

## Consequences

- The player's sentence area is about 56dp shorter on a phone (one row of buttons). No new state, no new work per
  frame; the row reads values the player already holds.
- One new icon (Material "border_color", Apache-2.0).
