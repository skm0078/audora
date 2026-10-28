# One pronunciation fixes dialog, easier with TalkBack and large text (2026-10-28, no ADR)
- From the accessibility and code structure audit (finding S4). The sentence menu's "This sounded wrong > fix a word"
  and Settings' "Words in every book" each had their own copy of the same dialog (fields, Whole word only, Add, the
  list with Delete). Both now use `ui/pronunciation/PronunciationFixDialog.kt`; each passes only what differs (the
  sentence or the language chips above, "Only in this book" for a book's fixes, what the brackets in the list say).
- Accessibility fixes that come with it:
  - Every Delete in the list was read as just "Delete". TalkBack now says which fix it removes
    ("Delete the fix: colonel, say it like kernel"), from the new `strings_fix_a11y.xml` (en/hi/bn).
  - The switches, Add and each Delete are at least 48 dp tall (the Settings switch row and the buttons were shorter).
  - The sentence menu's dialog scrolls, so at large text sizes Add and the list stay reachable.
  - Settings' language chips wrap onto a second line at large text instead of cutting off Bengali.
  - "Whole word only" and "Only in this book" keep their state across rotation, as the typed words already did.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-28-pronunciation-fix-dialog.txt`. `:pdf` changed: no.
  New dependency: no. Performance cost: none (one dialog instead of two; same work).
- **Could affect:** the reader's fix-a-word dialog, Settings > Words in every book.
- **Verified in CI:** ktlint, detekt, unit tests, `PronunciationFixDialogTest` (Add sends what was typed and clears it;
  each Delete is 48 dp and named after its fix; screenshot `pronunciation-fixes`).

**Real-device checks (the test phone):** in Settings > Words in every book, add two fixes, then with TalkBack on swipe
to each Delete: it names the fix. At the largest text size, all three language chips show.
