# Page snippet, part 1b (2026-10-28, ADR 2026-10-28-page-snippet)
- "Share part of the page" in the sentence menu's Share choices on a drawn page: a box around the sentence, dragged
  or resized by its corners within a third of the page, then Share opens the card screen on that part of the page
  with the book, author and page under it.
- `SnippetBox` and the `snippet*` tunables of `ShareCardConfig` (`:core`); `SnippetPicker` and `SnippetBar` in the
  reader; `PageSnippets` and `SnippetCard` on the card screen. What's new entry `2026-10-28-page-snippet`.
- `:pdf` not changed (PIPELINE_VERSION unchanged).
- **Could affect:** the reader's page view (taps and swipes are off only while the box shows), the Share choices.
- **Verified in CI:** `SnippetBoxTest`, `SnippetPickerTest` (move, resize within a third, bar at 200 %, the cut),
  `SentenceMenuTest`, `ShareCardScreenTest` (snippet card at 200 %), release journey step 26 (long-press, box, card,
  picture in the share folder).

**Cost:** about 15 KB of code and strings; the cut bitmap (about 2 MB) while the card screen is open; nothing at
cold start.
