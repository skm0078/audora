# Four places: Library, Notes, Made and You (2026-10-28)
- The bottom bar now has **Library, Notes, Made and You**. Made holds everything made from your books, with
  "Jobs and downloads" as its first row; You is the old Settings. ADR `docs/adr/2026-10-28-four-places.md`.
- **Tablets:** a rail from 600 dp as before, and from 840 dp a permanent sidebar with the four places and
  "Your collections" (each with its book count; tapping one opens the library on it). `ui/shell/Sidebar.kt`,
  `ui/library/LibraryShelves.kt`.
- The library's Jobs button shows only while something runs; Jobs is otherwise reached from Made.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-four-places.txt`. No `:pdf` change. No new dependency.
- **Could affect:** every place's bottom bar or rail, the Made and Jobs screens, Back from Jobs, the Settings title
  (now "You"), tablets from 840 dp.
- **Verified in CI:** `PlacesFormTest`, `AppDestinationTest`, `LibraryShelvesTest`, `AppShellTest` (screenshots
  `places-phone-bar.png`, `places-tablet-sidebar.png`), `MadeAndEditScreensTest`, the UI tour and
  `ReleaseJourneyTest` step 19 (the four places open; Jobs goes back to Made).

**Real-device checks (the test phone):** the bar shows Library, Notes, Made, You in that order in English, Hindi and
Bengali at the largest font size; Made → Jobs and downloads → Back returns to Made; start an audiobook export and
the library shows the Jobs button with a count until it ends.
