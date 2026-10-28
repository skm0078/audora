# Book menu and sort sheet (2026-10-28)
- A book's menu is one tidy sheet: the book on top, **Play, Up next and Finished** as big buttons, Details, **Save
  as** tiles (Audiobook, Talking EPUB, Accessible copy, Notes) that open in place, Studio mode and Remove. A **long
  press** on a book opens it too. ADR `docs/adr/2026-10-28-book-menu-sort-sheet.md`.
- The character guide and "Mark as not started" for a half-read book moved to the book's details sheet.
- The sort button opens a **sort sheet**: sort order, which books show, **Grid or List** (kept between visits) and
  "Show N books". `ui/library/LibrarySortSheet.kt`, `ui/library/LibraryLayout.kt`.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-book-menu-sort-sheet.txt`. No `:pdf` change. No new
  dependency.
- **Could affect:** every book's menu and its long press, the details sheet's buttons, the library's layout at large
  text once a layout is picked, the sort button.
- **Verified in CI:** `BookMenuChoicesTest`, `LibraryLayoutTest`, `BookActionTest`, `BookMenuScreensTest`
  (screenshots `book-menu.png`, `book-menu-save-as.png`, `library-sort-sheet.png`), the UI tour and
  `ReleaseJourneyTest` step 20.

**Real-device checks (the test phone):** long-press a cover and a list row; Play starts reading aloud; Save as opens
its four tiles and Audiobook starts the export; Finished on a finished book marks it not started; in the sort sheet
pick List, leave the library and come back (still a list), then Reset; repeat in Hindi and Bengali at the largest
font size.
