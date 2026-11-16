# EPUBs list all their chapters: scanned books, one-file books, nested contents (2026-11-15, ADR 2026-11-15-epub-contents)
- A scanned EPUB whose contents name only a "Notice" (the Internet Archive's) now lists the chapters found in its text
  ("Chapter 38: The Ascent of Mount Suvela", and the Books/Kandas); the test book went from 1 row to 633. A book that
  keeps several chapters in one file lists each (`#id` links), nested contents keep their levels, and a stub
  navigation document no longer hides a full NCX. A contents link with a stray `%` no longer stops the book opening.
- The sleep timer's "end of chapter" and audiobook chapters follow the chapter list: the scanned book had 1,705
  chapter ends (one per page file), now 633.
- Pages are cut exactly as before, so saved places and bookmarks in existing books stay where they were.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-11-15-epub-contents.txt`. `:pdf` unchanged. No new dependency.
- **Perf:** books with a real contents: no measurable change. Books with sparse contents: one regex pass over the
  text at open (about 30 ms more for a 1,705-page scan on a desktop JVM).
- **Could affect:** the chapter list, sleep timer chapter ends and audiobook chapters of every text book (EPUB, Word,
  MOBI, CHM, DjVu text, plain text); skip front matter reads the same outline.
- **Verified in CI:** EpubContentsTest (nav/NCX nesting, ids, plain-label rows, fuller contents, placing rows),
  EpubChapterListTest (one-file, stub nav, scanned, picture page and stray %, heading-only files), ChapterMarkersTest,
  existing EpubParserTest and TextBookTextTest unchanged; release journey step 69 (both fixture EPUBs show their
  chapters in the chapter list).

**Real-device checks (the test phone):** see the PR.
