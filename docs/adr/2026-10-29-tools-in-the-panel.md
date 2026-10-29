# ADR 2026-10-29-tools-in-the-panel: The library's tools move from ⋮ into the side panel

**Status:** Accepted (2026-10-29). Fourth findability PR from the maintainer-approved audit. The maintainer chose
the side panel over ⋮ ("Side panel is more action oriented than 3 dots"). Ships with ADR 2026-10-29-add-a-book.

## Context

After Add a book took the ways in, the library's ⋮ still held sync, backup, the quiz, the listening stats and Make
it yours: tools that matter but that nothing on screen pointed to. The side panel (phone drawer, tablet sidebar, ADR
2026-10-28-phone-drawer) already had a Your library heading with Make it yours, the collections and the stats. Two
homes for the same tools meant two lists to keep in step and no clear place to look.

## Decision

- **The library has no ⋮ menu.** Its top bar keeps the drawer button, search, sort and filter, and Jobs while
  something runs.
- **Your library in the side panel lists every tool,** generated from `LibraryAction.inPanel` in enum order: Add a
  book, Make it yours, the collections, watched folders, folder sync, backup and restore, the quiz and the listening
  stats. Each row has its own icon. A row opens the library and runs the tool as its button would
  (`openLibraryAction`), so the phone drawer and the tablet sidebar do the same thing.
- **`PanelLinks`** takes one `onLibraryAction` instead of one callback per tool; a new tool needs only its enum entry.
- **Search** finds the panel's tools under "Menu › Your library", with their own icons.

## Consequences

- Three more icons (Material "sync", "settings_backup_restore", "school"; Apache-2.0), under 1 KB together.
- The panel is taller by five rows; it already scrolls. Nothing changes at start-up or while scrolling the books.
- Kids mode keeps whatever the side panel offers it; there is no second copy of the tools.
- Alternatives: Settings → Your library for backup and sync (rejected by the maintainer's layout rule: major features
  belong in the panel); keeping a shorter ⋮ (rejected: two homes again).
