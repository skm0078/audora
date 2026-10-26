# TalkBack works on every screen, and the UI tour fails when it does not (2026-10-26, ADR 2026-10-26-talkback-pass)
- The sleep timer is read as "15 minutes left" (rounded up, never zero) instead of a ticking clock; the sentence being
  read says "Now reading"; a turned page is announced when the book is not being read aloud.
- Every slider says what it sets and its value ("Speed, 1.25×"); every sheet's handle reads "Close sheet" and closes
  it (`AppSheet`); the library's Add book button has a name; the bookmarks sheet and the search screen have a heading.
  Search now shows its title above the search box. At large text sizes "Bookmark here" moves under the bookmarks
  title instead of squeezing it onto two lines.
- The UI tour checks TalkBack on every screen, sheet and menu it visits (names, 48 dp targets, a heading) and writes
  `talkback.txt`; it now also opens the reader's sheets and a book's menu and details.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-talkback-pass.txt`. `:pdf` not changed. No new
  dependency.
- **Could affect:** every bottom sheet (handle), the speech sheet's speed and pitch, page text size, expressiveness,
  narrator blend, focus sound level, and the audiobook player's seek bar, the search screen layout.
- **Verified in CI:** `AccessibilitySemanticsTest` (named slider, the sheet handle named and closing the sheet, and the
  audit still catching a bare slider), `ReaderAccessibilityTest` (minutes left), the UI tour's TalkBack audit at font scale 2 in English,
  Hindi and Bengali, and the release journey.

**Real-device checks (the test phone):** with TalkBack on, swipe through the library, the reader, the speech sheet and
a book's menu: nothing should say only "button" or "slider"; set a sleep timer and move to it (it says minutes left);
open search and use the headings gesture to jump to its title.
