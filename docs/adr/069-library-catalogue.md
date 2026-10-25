# ADR-069: Collections, filters and author search, sized for a low-end phone

**Status:** Accepted (2026-10-24).

## Context
The third of three steps planned with the listener (ADR-067 Jobs and pitch, ADR-068 made files and book details).
Phone testing asked for "catalogue management and search and filter", implemented for low-end phones rather than
as a dead feature. Books now carry an author, a language and a format (ADR-068), but the library could only search
titles and sort by title, date and progress. The `collections` and `book_collections` tables were created empty in
schema 5 so this step needs no schema change.

## Decision
- **Collections:** the listener's own named groups ("For school", "Bedtime"); a book can be in several. A
  `CollectionStore` (core) over Room (`CollectionsDao`, `RoomCollectionStore`); removing a book or a collection
  removes only the link (foreign-key cascades from schema 5). Names are trimmed, spaces collapsed, cut at 60
  characters, never blank (`CollectionNames`), and never logged.
- **Where they show:** a second chip row under the shelves: Filters (with how many are set), one chip per collection
  with its number of books (tap to narrow the library to it, again to leave), and New / Edit collections, which
  opens a sheet to make, rename and delete them (delete asks first and says the books stay). A book's details sheet
  shows its collections as chips that add or remove it at once, plus New collection with the book already in it.
- **Filters:** a sheet with Author, File type, Language and Collection, each a radio list of only what the library
  holds with its number of books, plus Any; a choice applies at once, Clear filters resets them. They combine with
  the shelf, search and sort. A filter on a value the library no longer has (a deleted collection, an author edited
  away) is dropped (`LibraryNarrow.keptBy`), so the list never stays empty behind an invisible filter.
- **Search and sort:** search matches every word in the title or the author; a new sort, Author, goes by author
  then title, books without an author last.
- **Low-end tuning:**
  - Each book's title and author are folded (case, width, Latin accents) once into a `LibraryIndex`; a new index
    reuses the key of every book whose title and author did not change, so a reading position saved or one book
    edited folds nothing else. Search compares plain strings.
  - Typing shows in the field at once but narrows the list after a 200 ms pause, so a fast typist on a slow phone
    does not filter the library on every letter.
  - Progress and collections are each one query for the whole library (no query per row).
  - The cover memory cache is a sixteenth of the app's heap (`ActivityManager.memoryClass`), between 4 and 16 MiB
    (`CoverConfig.forDevice`); on a phone Android marks as low on RAM covers decode as RGB_565, half the memory
    (covers are opaque paper, so only a little colour depth is lost). A cover request whose tile scrolled away while
    waiting is dropped before its page is drawn.
- **Not done:** Paging 3. The library is one Room list of small rows; a 1,000-book library reads, indexes and
  searches well inside the device test's budget, and the lazy grid only composes what is on screen. Paging would
  add a dependency and make counts, facets and shelf numbers need extra queries. Revisit if a library of tens of
  thousands appears.

## Consequences
- No schema change (schema 5 already had the tables); no `:pdf` change (PIPELINE_VERSION 12).
- The library state carries collections, each book's collections and the filter lists; the controller recomputes
  the index and lists only when the books change.
- Tests: `LibraryCatalogueTest` (author search, every filter, author sort, filter lists, dropped filters, index
  reuse, names, a 5,000-book library), `CoverConfigTest`, `LibraryQueryTest` (unchanged behaviour),
  `RoomCollectionStoreTest` (device: order, rename, membership, cascades, 1,000 books in one go) and
  `CatalogueScreensTest` (device: a filter applies at once, collection chips, the collections sheet, joining from
  details; screenshots `filter-sheet.png`, `catalogue-row.png`, `collections-sheet.png`).
