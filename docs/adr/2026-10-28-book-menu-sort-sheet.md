# ADR 2026-10-28-book-menu-sort-sheet: one-sheet book menu and a sort sheet with grid or list

**Status:** Accepted (2026-10-28). Third step of the design pass the maintainer asked for before v0.1.0 ("ListenLock:
where every feature lives", mockups Polish-menu, Polish-menu-hi-large and Polish-sort-sheet on the design canvas).
Builds on ADR-062 (one line per action, help behind an info button) and ADR 2026-10-28-four-places.

## Context

A book's menu was eleven plain lines of equal weight: Play was missing, Save as audiobook sat beside Mark as not
started, and the four exports were four long rows. It opened only from the ⋮, never from a long press, though the
feature map lists long press as the way people meet it. Sort and filter was a drop-down of radio items; grid or list
could not be chosen at all (the grid turned into a list only at 150 % text).

## Decision

- **Book menu, one sheet, at most eight lines** (the rule for sheets):
  1. The book: cover, title, where reading stands and its language, a progress bar. TalkBack hears "What to do with
     …" as the heading.
  2. **Big buttons, all the same width:** Play (filled: what most people open the menu for), Up next, and Finished,
     a switch that is on for a finished book; turning it off marks the book not started again. An action placed
     `MenuPlace.QUICK` (the share card, when it lands) joins this row.
  3. **Details**, with a chevron.
  4. **Save as**, closed until tapped, then two tiles to a row: Audiobook, Talking EPUB, Accessible copy, Notes, each
     with one line on what comes out. Its info button holds the long help for all four.
  5. **Studio mode** (and any other `MenuPlace.ROW` action), with its info button.
  6. **Remove from library**, in the error colour, with its info button.
- **Where each action lives is data:** `BookAction.place` (`QUICK`, `ROW`, `SAVE_AS`, `FINISHED`, `DETAILS`), a new
  action defaulting to a row; `BookMenuChoices.of(item)` works out the menu from the book alone, so a unit test checks
  that every action keeps a home and the sheet keeps to eight lines.
- **Moved, not removed:** the character guide and "Mark as not started" for a book half read move to the book's
  details sheet (one tap further); the feature map already lists Details as the guide's shortcut.
- **Long press** on a cover or a list row opens the same menu (none in kids mode); TalkBack offers it as the long
  press action, named for the book.
- **Sort sheet** replaces the drop-down: Sort by (five orders as radio lines, since five do not split evenly into
  chips), Show (the four shelves as equal chips, two to a row), **Layout: Grid or List**, and a "Show N books" button
  that closes it. Everything applies the moment it is picked. Reset puts back the library's order, all books and the
  automatic layout.
- **Layout** is kept in its own small Preferences DataStore (`library_view`): automatic until picked (covers, a list
  from 150 % text), then the listener's choice at any text size. The books wait the few milliseconds the first read
  takes, so they never jump from grid to list on opening.

## Alternatives

- **Keep the eleven lines and only restyle them:** still more than a sheet should hold, and the exports stay four
  long rows.
- **Save as as a second sheet:** a sheet over a sheet is against the navigation rules; tiles in place keep one level.
- **A Studio switch as in the mockup:** Studio opens its own screen with progress and files; a switch would claim a
  state the row does not have.
- **The mockup's "With notes", "Made by you" and "Downloads" filters and a language row in the sort sheet:** the
  library has no such filters today, and language is already in the Filters chip; left for a later step rather than
  added here without their data.

## Consequences

- Cost on the device: no new dependency; seven vector icons (about 3 KB); the menu and sort sheet are composed only
  while open; one tiny DataStore read when the library first opens. Scrolling the library is unchanged (a long press
  handler replaces the tap handler on each book).
- Strings: new `strings_book_menu.xml` in English, Hindi and Bengali. `library_sort_by`, `library_show` and the sort
  and filter labels are reused.
- Tests: `BookMenuChoicesTest`, `LibraryLayoutTest`, `BookMenuScreensTest` (long press, Play, Save as tiles,
  Finished, Remove, sort and layout applying at once, Reset; screenshots `book-menu.png`, `book-menu-save-as.png`,
  `library-sort-sheet.png`), the UI tour's book menu and release journey step 20.
