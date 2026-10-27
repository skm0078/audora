# Phone fixes: Bengali voices in the speech sheet, book language after a switch (2026-10-27)
- Speech sheet (speed button): the open page's language (Hindi or Bengali) is listed first and always shown, even
  with one voice downloaded; that row names the voice and points to Settings → Voices. Before, a language was shown
  only with two or more voices, so once Piper Google Bengali was withdrawn a Bengali book with only SYSPIN showed
  English's voices alone.
- Book language (Edit details): the session now compares the library's language with the one it reads in
  (`BookSession.onBookLanguage`), and sets it with the book before the first suspension. Before, the service only
  acted on a change between two library updates for the same book, so a change made right after switching books,
  before the library said anything about the new book, was lost.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-27-phone-fixes.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged).
- **Could affect:** the speech sheet's voice rows, restarts of the sentence playing when the library changes.
- **Verified in CI:** `BookVoicesSectionTest`, `BookNotesTest`, `BookSessionTest`
  (`aLanguageChangedRightAfterSwitchingBooksTakesEffect`), release journey step 07b.

**Real-device checks (the test phone):** open a Bengali book with only SYSPIN downloaded, tap the speed button:
Bengali is the first voice row. Open book A, then book B, and at once change B's language in Edit details: the
sentence playing restarts in the new language. Do the same for a third book.
