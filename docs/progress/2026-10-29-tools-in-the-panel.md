# The library's tools are in the side panel (2026-10-29, ADR 2026-10-29-tools-in-the-panel)
- The library's ⋮ menu is gone. The side panel's Your library lists Add a book, Make it yours, the collections,
  watched folders, folder sync, backup and restore, the quiz and the listening stats, each with its icon, on the
  phone drawer and the tablet sidebar alike.
- Watched folders moved from the Add a book sheet to the panel; the sheet keeps the five ways that read something now.
- Search shows the panel's tools under "Menu › Your library".
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-tools-in-the-panel.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** three vector icons (under 1 KB); the rows are composed only while the drawer is open (always on the
  sidebar, as before). Nothing at start-up or while scrolling.
- **Could affect:** the library top bar, the phone drawer, the tablet sidebar, search's library rows and anything that
  reached a tool through ⋮.
- **Verified in CI:** `LibraryPanelRowsTest`, `AddBookSourceTest`, `FoundIndexTest`, `PhoneDrawerTest`,
  `AppShellTest`, `MakeItYoursScreenTest`, `FolderSyncScreenTest`, release journey steps 44 and 48.

**Real-device checks (the test phone):** the library's top bar shows ☰, search and sort, no ⋮. ☰ shows Your library
with eight rows; each opens its tool (Add a book its sheet, Backup its sheet, Quiz its sheet) with the drawer gone
first. On a tablet the sidebar shows the same rows. Search "restore": Back up and restore says "Menu › Your
library". TalkBack reads each row's name.
