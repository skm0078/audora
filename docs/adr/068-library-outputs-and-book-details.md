# ADR-068: What the app made lives in the library; books carry their own details

**Status:** Accepted (2026-10-24).

## Context
Phone testing found that saved audiobooks, studio audio files and accessible or talking-book copies vanished into
shared storage: the app said where they went in a notification and never showed them again, so a listener could not
find, play, share or delete them from the app. Books had only a title from the file name and the first page as a
cover; nothing said who wrote a book or what language it was in, and nothing could be corrected. Pitch (ADR-067) was
one setting for every book. This is the second of three steps planned with the listener (the third is collections,
filters, author sort and search, with low-end tuning).

## Decision
- **Room v5** (`AutoMigration(4, 5)`, every v4 row kept): `books` gains `author`, `language`, `format`, `cover`,
  `edited_fields` and `details_version`; `book_settings` gains `pitch`; new tables `book_outputs` (one row per made
  file, cascade-deleted with its book; the file stays on the phone) and `collections` / `book_collections` (empty
  until step three, created now so the schema changes once).
- **Made files** (`BookOutput`, grouped by run into an `OutputSet`): the audiobook export replaces the book's earlier
  audiobook set when it finishes (as its files replace the old ones); a studio audio file and each copy are added.
  The location is a MediaStore URI, never logged. Earlier exports are found once from their export records
  (`EarlierAudiobooks`). Clips are not listed: they are shared at once and are short.
- **Where they show:** Jobs → "Everything made from your books" (every set, newest first, with its book) and a
  book's details → "Made from this book". Each set says what it is, chapters or files, length, size, date and pitch;
  Play (audio) or Open (a document), Share (all of its files, read access only) and Delete (with a confirmation;
  files of an earlier install go through Android's own consent dialog, `MediaStore.createDeleteRequest`). Files
  deleted in another app show as gone, with Remove from the list.
- **In-app player** (`MadeAudioService`, a second `MediaSessionService` with its own ExoPlayer): the set's chapters
  as a playlist, the phone's media notification and lock-screen controls, audio focus (starting it pauses the book
  and the other way round), back 15 s, forward 30 s, chapters, speed. Where the listener stopped is saved per set on
  pause, chapter change and stop (`MadePositions`), and cleared when the last chapter ends. The reading service is
  untouched.
- **Book details:** filled in once per book in the background, one book at a time, three seconds after start
  (`BookDetailsFill`), from the file itself: PDF document information (PDFium), EPUB package (`dc:creator`, language,
  cover image), Word `docProps/core.xml`, OpenDocument `meta.xml`, a web page's `<title>`/author/`lang`, RTF `\info`.
  Junk is ignored (placeholder titles, tool prefixes, file names, account names as authors). Without a language tag
  the title's script decides (Latin, Devanagari, Bengali). `details_version` makes a later reader improvement fill
  every book again. An EPUB's own cover is kept, cut to cover size.
- **Edit details** (book details → Edit details): title, author, language (As the book says, English, Hindi,
  Bengali) and cover (first page, another page of a PDF, a gallery picture, an EPUB's own cover). Each edited field is
  marked and never overwritten by a later fill; "Fill in from the file again" drops the marks. A gallery picture is
  decoded small (never a whole 12 MP photo) and kept as WEBP only when Save is pressed. The library shows the author
  under the title; the details sheet adds author, format and language.
- **Pitch per book:** "This book only" now keeps the book's own pitch with its speed and pace (`BookSettings.pitch`).
- **No `:pdf` change:** PIPELINE_VERSION stays 12; `documentInfo` only reads metadata.

## Alternatives considered
- **Scan shared storage for our files** instead of recording them: slow on a phone full of audio, and it cannot tell
  which book a file came from once it is renamed. Recording at the source is exact and costs one row per file.
- **Play saved files in the reading service:** it is built around sentences, highlights and resume anchors; a plain
  file needs none of that, and mixing them risks the reading path. A small second service is simpler and isolated.
- **Hand files to another app to play:** kept as Open for documents, but listeners asked to play inside the app and
  go on where they stopped.
- **Read details while adding a book:** adding stays fast and never fails on a bad metadata block; filling later in
  the background also covers books added before this change.
- **One table for details:** the fields are a book's own and read with it on every list; columns avoid a join.

## Consequences
- A database upgrade runs once on update (columns and three tables added; no data rewritten).
- Made files outlive their book; removing a book forgets its list but leaves the files, as before.
- A file renamed in another app keeps its MediaStore URI and still plays; a file moved off the phone shows as gone.
- New strings in `strings_made.xml` (en, hi, bn). MainActivity gains no field: new screens reach the library through
  `LibraryEntryPoint`.
- Tests: `BookDetailsTest`, `BookOutputsTest`, `LibraryTest` (fill, edit, reset), `BookTitlesTest` (new extensions),
  `FileMetaTest`, `MadePositionsTest`, `BookEditDraftTest`, `MadePlayerTextTest`, `AppDestinationTest`; on a device
  `AudoraDatabaseMigrationTest` (v1 to v5, outputs cascade, pitch kept) and `MadeAndEditScreensTest` (shows, an edit
  applies at once, screenshots `made-list.png`, `made-player.png`, `edit-details.png` in the `player-screens`
  artifact).
