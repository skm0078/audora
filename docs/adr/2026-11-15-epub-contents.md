# ADR 2026-11-15-epub-contents: An EPUB's chapter list keeps nesting and `#id` links, and a scanned book's comes from its text

**Status:** Accepted (2026-11-15). A release fix during the feature freeze: a scanned book (the Internet Archive's
Valmiki Ramayana, 1,705 page files) showed one chapter, "Notice", where other readers list its chapters.

## Context

The EPUB reader made one chapter per spine file and gave each file one title: the first contents row whose link
named that file (the `#id` dropped, nesting flattened), else the file's first heading. The reader's chapter list was
the titled files. Four kinds of books lost chapters:

1. **Contents that name next to nothing.** The Internet Archive's scanned EPUBs list only a "Notice"; each scanned
   page is one file with one paragraph, and "CHAPTER 38 The Ascent of Mount Suvela HAVING resolved…" sits inside it.
   No headings either. Result: a one-row list, and every page counted as a chapter end for the sleep timer and the
   audiobook export (1,705 "chapters").
2. **Chapters in one file.** Calibre and many converters keep a whole book (or a part) in one file and link the
   contents to `book.xhtml#ch2`. Only the first row per file survived.
3. **Nesting.** Part › Chapter › Section was flattened, and rows under a parent in the same file were dropped.
4. **A stub navigation document** (one "Start" link) beside a full EPUB 2 NCX: the stub won.

Also: an unescaped `%` in a contents link threw out of the parser, so the book did not open.

## Decision

- **Contents are rows that point into the text** (`TextDocument.contents`, `ContentsEntry`: chapter, block,
  character, depth), not one title per file. The navigation document and the NCX keep depth, `#id` links and plain
  label rows (`<li><span>Part Two</span><ol>…`); the fuller of the two wins, the navigation document on a tie
  (`EpubContents`). `XhtmlBlocks.read` records which block each `id` falls in. A row on a picture-only page moves to
  the next chapter; an unknown id points at its file's start. A file no row names but which opens with a heading is
  still listed, at the level of the row before it.
- **Pages do not change.** A row maps to the page its place falls on (`TextPage.firstBlock`/`firstChar`); the page
  cut is the same pure function as before, so saved places, bookmarks and studio audio of existing books stay valid.
- **The reader gets a tree** (`TextBookText.outline`, the same `OutlineEntry` as a PDF), so `ChapterList` indents it
  and `ChapterStarts` picks chapter ends the same way for both. A book whose contents give fewer than two chapter
  pages keeps the old per-file chapter ends.
- **Chapters from the text** (`ChapterMarkers`) only when the book's own contents have fewer than
  `TextBookConfig.sparseContentsRows` (3) rows, and only if at least that many are found. Counted: a heading-like
  line (at most `headingMaxChars`) starting Chapter/Part/Book/Canto/Section/अध्याय/परिच्छेद/भाग/অধ্যায়/পরিচ্ছেদ/
  খণ্ড/পর্ব and a number; and "CHAPTER" in capitals, a number and a capital letter anywhere in a block. Not counted:
  a contents page's "CHAPTER 30." (full stop after the number), "Chapter 3 shows that…" in prose, a line repeated on
  every page. The title runs to the chapter's first word in capitals or misread small capitals ("HAVING", "HavinG")
  within `chapterTitleMaxWords` (20), else it is "Chapter N". Numbers are listed as printed ("Chapter I2" when the scan
  misread 12): the list shows the book's own text.
- Applies to every text format through `BookContents` (Word, MOBI, CHM, DjVu text, plain text), not only EPUB.

## Alternatives

- **List every spine file ("Page 1" … "Page 1704")**, as some readers do: a list nobody can navigate, and still no
  chapter names.
- **Split chapters (and pages) at the found places:** chapters would start at the top of a page, but every existing
  EPUB's pages would shift and saved places would land on the wrong sentence. Rejected; a row opens the page its
  chapter starts on, as a PDF bookmark does.
- **Read chapter names from the scan's own contents page:** OCR'd contents pages are too garbled ("4- Shri Rama's so
  sons chant | the poem - č = =") to match reliably.

## Consequences

- Opening cost: one regex pass over the text, only for books with sparse contents (the 1,705-page scan: about 30 ms
  more on a desktop JVM, paid once per open); books with real contents pay only the anchor bookkeeping (no measurable
  change).
- A book whose sparse contents and prose both use "CHAPTER 3 Something" in capitals mid-paragraph would list that
  line; acceptable, as such books have no usable list otherwise.
