# Add book folds to its icon while the books scroll down (2026-10-29, no ADR)
- From the accessibility and code structure audit (finding A4).
- **Problem:** at large text sizes the Add book button grows wide. It covered most of each row as the list scrolled
  under it, including the row's ⋮ menu, which could only be reached by scrolling to the very end.
- **Change:** Add book shows only its icon while the books scroll down, and its label again when they scroll back up.
  This is Material's own extended button behaviour (`expanded`). TalkBack still reads "Add book" in both states, and
  the bottom space the list keeps for the button is unchanged.
  - `ui/library/AddBookButton.kt`: `AddBookButton(expanded)` and `AddBookFolding`, a nested scroll watcher on the
    library's content that never takes any scroll for itself.
  - `LibraryScreen.kt`: two lines wire it in, for both covers and list.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-add-book-folds.txt`. `:pdf` changed: no. New
  dependency: no. Performance cost: none measurable (one state read per scroll event; the label's fold is Material's
  own short animation).
- **Could affect:** the library (covers and list), at every text size.
- **Verified in CI:** `AddBookFoldingTest` (JVM): folds down, opens up, ignores a resting finger, takes no scroll.
  `AddBookButtonTest` (emulator, text at 200%): narrower while reading down, the same width again going up, found by
  its TalkBack name throughout.

**Real-device checks (the test phone):** Settings > Display > Font size at the largest. In the library, scroll down:
Add book shrinks to a round + button and each row's ⋮ can be tapped. Scroll up a little: "Add book" comes back.
