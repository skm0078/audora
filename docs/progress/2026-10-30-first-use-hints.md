# First-use hints (2026-10-30, ADR 2026-10-30-first-use-hints)
- Seven one-time tips show when a feature would help: keep a cloud book on the phone, smart speed at high speed,
  Cast for stories, Quiz me after a highlight, stats at the end of a book, studio mode while charging a long book, and
  more ways to add books after a few starts with one book. At most one per app start, above the play controls.
- Settings › Advanced › Help › "Tips as you go" mutes them.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-30-first-use-hints.txt`. `:pdf` unchanged. No new
  dependency or permission. New strings in `strings_first_use_hints.xml` (three languages).
- Instrumented tests now use `AudoraTestRunner`, which mutes hints so other tests see the screens they expect.
- **Cost:** one SharedPreferences read per app start, one battery-state read per book open, a few comparisons per
  reader redraw. No background work.
- **Could affect:** the reader's bottom bar (a card above Play, shortcuts hidden while it shows), the library's top
  (one card), Settings › Help (one switch), every instrumented test (the new runner).
- **Verified in CI:** `FirstUseHintsTest`, `ReaderHintsTest`, `ReaderNoticeTest`, `FirstUseHintsUiTest` (screenshot
  "first-use-hint"), release journey step 60.

**Real-device checks (the test phone):** clear the app's data. Open a book, set speed to 2x: the smart speed tip shows
above Play; Show me opens Sound. Restart the app and open a story: the Cast tip shows (only one per start). Highlight
a line and restart: the Quiz tip shows; Show me opens Quiz me. Go to the last page and restart: the end tip shows.
Open a 100+ page book about half read while charging: Start studio mode starts it. Open a book from Drive: Keep a copy
copies it. With one book, open the app three times: the library tip shows; Show me opens Add book. Turn off You ›
Advanced › Help › Tips as you go: no tip shows. Check kids mode shows none, and repeat in Hindi and Bengali with the
largest text size.
