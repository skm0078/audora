# Text pages, part 3b (2026-10-28, ADR 2026-10-28-text-pages)
- "Turn pages" in the text view's look: a screenful at a time, a sideways swipe or the arrows turn, no line cut in
  two, on to the next page at the end and back to the page before at its end; reading on keeps the spoken sentence
  on screen; TalkBack hears "Page N of M, continued".
- `TextScreens`, `TextTurnBack`, `TextPagesBar` (`TextPages.kt`); the look's `turnPages`. What's new entry
  `2026-10-28-text-pages`. `:pdf` not changed (PIPELINE_VERSION unchanged).
- **Could affect:** the text view only while the switch is on (off by default).
- **Verified in CI:** `TextPagesTest` (turn, no cut, next and previous page, reading keeps the sentence on screen,
  Bengali at 200 %), release journey step 27 (a text book with Turn pages on in the installed app).

**Cost:** about 8 KB of code and strings; nothing at cold start; a turn is one list jump.
