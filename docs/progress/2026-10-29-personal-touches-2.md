# Personal touches, part 2 (2026-10-29, ADR 2026-10-29-personal-touches-2)
- Collections: a colour and an emoji for each (Collections sheet › Edit), shown on the chips, the sheet, the sidebar
  and the phone drawer.
- Book menu: the voice and speed the book keeps, "Words you looked up" with their meanings and a 5-word quiz.
- Reader: "Previously, N days ago" with the two sentences before your place after 3 days away; a book's first line
  large over the page on its very first open.
- Collections sheet: "Shelves you might like" from series, authors and languages, made with one tap.
- Home: "This week", the week's listening in the goal ring with the streak; a tap opens Your listening.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-29-personal-touches-2.txt`. `:pdf` unchanged. No new
  dependency.
- **Cost:** no new dependency, no new background work. Two small text files read once off the main thread
  (`shelf-looks.txt`, a few hundred bytes; `looked-up-words.txt`, at most a few hundred KB across a large library) and
  written only when a look or a word changes. "This week" sums the listening log off the main thread when it
  changes (each page turn while reading), a few milliseconds for a year of listening. Shelf suggestions are worked out
  only while the Collections sheet is open. APK about 40 KB (strings in three languages, one icon).
- **Could affect:** the book menu, the Collections sheet and chips, the sidebar and drawer shelf rows, the reader's
  resume message and first open, the dictionary look-up (one more in-memory call), the home's parts, backup contents.
- **Verified in CI:** `ShelfLookStoreTest`, `ShelfSuggestionsTest`, `LookedUpWordsTest`, `WordQuizTest`,
  `WeekGlanceTest`, `HomeLayoutTest`, `LibraryHomeTest`, `LibraryShelvesTest`, `PreviouslyOnTest`, `OpeningLineTest`,
  `WordQuizModelTest`, `BookVoiceNoteTest`, `BackupArchiveTest`, device tests `ShelfEditorTest`,
  `ShelfSuggestionsTest`, `BookWordsTest`, `BookMenuScreensTest`, `PreviouslyOnCardTest`, `OpeningLineCardTest`,
  `ThisWeekCardTest`, `MakeItYoursScreenTest`, release journey step 48.

**Real-device checks (the test phone):** Collections sheet › Edit a collection, pick Teal and a star: the chip, the
drawer row and the sheet show both. Add a fresh book and open it: its first line shows large, Play works under it,
and it goes by itself. Look up two words in a book, long-press the book: "Words you looked up" lists them; Quiz me
plays the word and then its sentence. Set the phone's date 4 days ahead and open a book you were reading: "Previously,
4 days ago" with two sentences; Hear them again reads from the first. Add three books by one author: the Collections
sheet suggests a shelf; Make shelf makes it. Listen a few minutes and go back to the library: "This week" shows the
time and ring; tap it: Your listening opens. TalkBack: the opening line is read once; each colour says its name; the
This week card says "Open your listening".

## Fixed on the way: a lost tap on a found setting

Typing in the library's search showed what it finds (settings, actions) above every book at once, then 200 ms
later, when the books narrowed to none, alone in their own list. A tap landing on that change was lost; CI's release
journey (step 24b) hit it every time once this branch's home made the library a little heavier. What search finds now
follows the text the books were narrowed for (`LibraryListState.Loaded.narrowedFor`), so both change together, once.
A cleared search finds nothing at once. Cost: none; the found rows appear with the books, 200 ms after typing pauses.
