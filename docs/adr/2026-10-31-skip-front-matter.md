# ADR 2026-10-31-skip-front-matter: New books open at their first chapter, and say what they skipped

**Status:** Accepted (2026-10-31). A release fix the maintainer asked for during the feature freeze: most books open
on pages of contents, copyright details and preface, and a new listener who presses Play hears ISBNs and page
numbers first.

## Context

Every book opened on page 1 and Play read from there. The "What to read" options skip things inside sentences
(tables, notes, links), never whole pages, so nothing stood between a new listener and the front matter. The fix must
be cheap for 300-page and scanned books, work for English, Hindi and Bengali, and never hide or silently skip text.

## Decision

- **When:** only for a book with nothing read yet (no saved place, not playing, not reopened for new text settings
  or a rotation). Its start is searched once and kept per book (`BookStartPreferences`, SharedPreferences
  `book_start`), found or not, so browsing the library never pays twice.
- **Step 1, the chapter list** (`pdf.start.ContentStartFinder.fromOutline`): the first outline entry that is not
  front matter by its title (`FrontMatterWords`: contents, copyright, cover, title page, dedication,
  acknowledgements, preface, foreword, author's/translator's/publisher's note, in English, Hindi and Bengali, matched
  after NFC and lower-casing). A wrapper entry holding front matter is looked into. "Introduction" and "Prologue"
  are content. Reads no pages. Skipped to only when it lies in the first half of the book.
- **Step 2, the first pages** when there is no usable chapter list (`FrontMatterPage`, `FrontMatterWalk`): each page
  is read in turn with `pageIfReady` (never OCR) and called contents (many page numbers), a front-matter heading,
  a chapter heading ("Chapter 1", "অধ্যায় ১", "प्रथम अध्याय", a lone numeral), copyright (two or more publisher
  words: ISBN, All rights reserved, प्रकाशक, প্রথম প্রকাশ), short (title, dedication, blank) or running text. A
  preface runs on over pages of plain text until a page opens with a heading. The first chapter heading, or the
  first running text outside a preface, is the start.
- **Never guesses.** No skip when: the book has fewer than 20 pages (articles, declarations); page 1 holds running
  text (a document whose title, "Preamble"/"মুখবন্ধ" and text share page 1); a scanned page is not recognised yet;
  the start is not found within 30 pages and a third of the book; or 2.5 s pass (`ContentStartConfig`).
- **Never silent.** The reader opens at the start and its notice slot (second after a page problem) shows
  "Started at “Chapter 1”, page 15 · Skipped 14 pages before it: contents, copyright, preface." with **Read from the
  start** (page 1, and the book opens there from now on) and OK. The pages stay in the book, the page bar and the
  chapter list. The opening line card shows the start page's first line.
- **Switch:** Settings › Listening › "Start new books at the first chapter", on by default. Off: nothing is
  searched and kept starts are not used.

## Alternatives

- **OCR the first pages of a scanned book** to search them: minutes of work before the first page shows. Rejected; a
  scanned book with a chapter list still skips.
- **Skip front matter inside playback** (a reading mode like "Skip unreadable text"): would hide pages silently
  wherever reading passes them, and a listener who taps a contents line would hear nothing. Rejected: this decides
  only where a new book opens.
- **Map the contents page's printed page numbers to PDF pages:** fragile (roman numerals, offsets) when the outline
  already gives the PDF page, and the heading search finds the chapter itself. Left for later if testers need it.

## Consequences

- Cost: the chapter-list step is free; the page step reads at most 30 pages once per book, stopping at the start
  (typically 3–10 page reads, the same work the reader does turning those pages). One SharedPreferences read per
  book open. No new dependency, permission or background work. `:pdf` reading output and `PIPELINE_VERSION`
  unchanged (a new package that only reads units).
- A wrong guess lands a few pages off at worst, and the card's Read from the start undoes it in one tap.
- Known limits: a scanned book without a chapter list opens on page 1; a sub-heading on a preface's second page
  ends the preface early; Android Auto or the lock screen starting a never-opened book still begin on page 1 (they
  do not open the reader).
