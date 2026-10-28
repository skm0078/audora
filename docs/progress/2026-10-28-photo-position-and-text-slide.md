# Place your own picture on a card, and text turns that slide (2026-10-28, ADR 2026-10-28-photo-position)
- "Your own picture" behind a card or quote video can be dragged on the card to choose which part shows (it was
  always the middle); TalkBack can move it with four actions. A new picture starts centred.
- With Turn pages on, a text turn now slides in from the side it was turned from (200 ms, layer only); turns made by
  reading on still jump. ADR 2026-10-28-text-pages updated: volume keys turning screens is now "not doing".
- `PhotoFocus` (`:core`), `CardPhoto` (`QuoteCard.kt`), `photoDrag` (`PhotoPosition.kt`), `TurnSlide` (`TextPages.kt`).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-photo-position.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged). No new dependency.
- **Could affect:** the card screen's preview and its made picture and video backdrop; the text view with Turn pages
  on.
- **Verified in CI:** `PhotoFocusTest` (drag, edges, the side that fits never moves, nudges); `ShareCardScreenTest`
  (a drag moves the picture on the made card, TalkBack's action moves it back); `TextPagesTest` (a turn slides in
  from the right and settles); release journey step 28 (a text turn back settles where the text was, in the installed
  app).

**Real-device checks (the test phone):** pick your own picture for a story card, drag it left and right, then make a
video and check the same part shows; with Turn pages on, turn on and back and watch the slide.

**Cost:** about 6 KB of code and strings; nothing at cold start; no new bitmap; a turn adds about 12 layer-only
frames.
