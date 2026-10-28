# A book pane beside the library and a side panel in the reader on tablets (2026-10-28, ADR 2026-10-28-tablet-panes)
- Library on a wide screen: the picked book's menu stays in a pane beside the books, Save as open. A first tap picks a
  book, a second opens it; the ⋮ and a long press pick too. Phones and kids mode are unchanged.
- Reader on a wide screen: Contents, Notes and People tabs beside the page; a chapter or note opens with the panel
  left up; the top bar's Bookmarks and Chapters buttons give way to it.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-tablet-panes.txt`. `:pdf` unchanged. No new dependency.
- **Cost:** none on a phone beyond one width check in the library. On a tablet, the pane and panel are composed only
  while shown; the People tab rereads the guide file (a few KB) per page turn while in view. APK about 2 KB.
- **Could affect:** the book menu sheet (now drawn by the shared `BookMenuContent`), the bookmarks and chapters
  sheets (their lists are shared with the panel), the character guide dialog, a tap on a book in the library.
- **Verified in CI:** `BookPaneTest`, `PanelTabTest`, `BookPaneScreensTest` (pane shows, tap picks then opens, pane
  buttons act and stay, phone has no pane), `ReaderSidePanelTest` (chapter opens with the panel up, Notes and People
  tabs, phone keeps the buttons), `BookMenuScreensTest` (the sheet unchanged), release journey step 24.

**Real-device checks (a tablet or a large-screen emulator, landscape):** the library shows the pane; tap two books in
turn: the pane follows; tap the picked one: it opens. In the reader, tap a chapter in Contents: the page moves and the
panel stays; add a bookmark from Notes; open People and add a character. Rotate to portrait: the sheets come back.
