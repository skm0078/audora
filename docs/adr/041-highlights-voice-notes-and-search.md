# ADR-041: Highlights, voice notes and search in the book

**Status:** Accepted (2026-10-17). Plan items 30, 32 and 16.

## Context
Items 30 (highlights and quote export) and 32 (voice notes) are the listener's own marks on a book, like bookmarks
(item 29): they must survive a pipeline change, travel with backup (item 28) and never reach the log. Item 16 (search
in the book) needs every page's text, which the app only extracts page by page as it reads; extracting a long book
again for each search, or a scanned one (OCR, seconds a page), is far too slow.

No library may be added before the user's call 3, and nothing may leave the phone.

## Decision
1. **Room v4, one auto-migration from v3**, adding `highlights` and `voice_notes`. Both carry the same anchor columns
   as `bookmarks` (page, char start, fingerprints, pipeline version), so a later pipeline finds them again the way
   resume does, and both are deleted with their book (foreign key, cascade). Only tables are added, so Room's
   generated migration is exact; the device test upgrades a v3 database and checks both tables.
2. **Highlights** are whole sentences (from the sentence menu), with the quote kept so an export reads well even if
   the PDF is gone. *Export highlights and notes* in a book's menu writes `<title> - notes.md` and `.txt` to
   `Documents/Audora/` through MediaStore (`QuoteExport`, pure Kotlin, tested).
3. **Voice notes** keep their audio as files in `filesDir/voice-notes/` (private, part of backup) and only the file
   name and length in Room.
4. **Search uses an SQLite FTS4 index outside Room**: one file per book in `noBackupFilesDir/search/`, named by a hash
   of the book id. It is a cache of the pipeline's output, so backup leaves it out (it can be megabytes per book and
   is rebuilt on the new phone), a new `PIPELINE_VERSION` or page count rebuilds it, and a removed book's file is
   deleted when the app opens. Search terms are made in Kotlin (`SearchTerms`: letters, digits and combining marks,
   NFC, lower-case, joiners dropped) because FTS tokenizers differ across Android versions and split Indic text badly
   (a danda sticks to its word). Every word must match, the last as a prefix, so results appear while typing.
5. **The index is built while the search screen is open**, from the reader's own document (scanned pages come from
   the OCR cache when they were read before), in batches of 10 pages, and continues where it stopped next time.
   Pages that cannot be read yet (OCR models missing) are recorded and tried again on the next search.

## Alternatives considered
- **The search index as a Room table.** One database to back up, but the backup would carry every book's text, a
  pipeline change would need a migration of cached data, and Room's FTS support needs its own tokenizer settings per
  Android version. Rejected for a per-book cache file.
- **Searching page by page without an index.** No storage, but each search re-extracts the book: minutes for a long
  scanned book. Rejected.
- **Highlights as free character ranges.** Needs text selection in both reading views; sentences match how the app
  reads, bookmarks and reports. Ranges can come later on the same table (end page and end char are stored).

## Consequences
- Search index size is about twice the book's text (sentences plus terms): roughly 1–2 MB for a 300-page book.
  Building reads every page once: a text PDF at PDFium speed (tens of pages a second), a scanned page not yet in the
  OCR cache at OCR speed (about 3–6 s a page on the test phone), so a long scanned book is searchable page by page as it
  builds. While it builds, page turns in the reader share the extraction thread and can be slower.
- Voice notes cost about 0.5 MB per minute (AAC, 64 kbps mono); they are in backup.
- The quote of a highlight is the book's text stored in the app's private database; like notes, it is never logged.
