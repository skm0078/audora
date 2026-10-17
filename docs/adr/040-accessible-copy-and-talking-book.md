# ADR-040: Accessible copy and talking-book EPUB

**Status:** Accepted (2026-10-17). Plan items 69 and 76.

## Context
Audora already turns a scanned or broken Hindi or Bengali PDF into clean, ordered sentences: OCR (ADR-011), layout,
headings, lists, tables and notes (ADR-035), with junk scored and skippable. That work is lost when the app is closed:
screen readers, braille displays, ebook readers and accessible-book players cannot use it. Item 69 asks for the clean
text as a file; item 76 for an EPUB 3 talking book, the format accessible-book players (Thorium, Voice Dream, Dolphin
EasyReader) use, with text and audio synced sentence by sentence.

No library may be added before the user's call 3, and nothing may leave the phone.

## Decision
1. **One structure, built from what `:pdf` already tags.** `DocumentBuilder` (`:playback`, `playback.epub`, pure
   Kotlin) groups a book's `ReadingUnit`s into chapters (the audiobook export's `AudioChapters.plan`: PDF outline,
   else parts of 10 pages), and within them headings, paragraphs (a pause of `paragraphPauseMs` or more ends one; a
   paragraph carries on across a page turn), list items, tables (summary unit as the header row, rows as rows, cells
   from `TableRowInfo.cells`), captions, equations, sidebars, and notes (moved to the end of their chapter). Units
   scored below `EpubConfig.keepAtLeast` (the "Skip unreadable text" threshold) are left out, unless the talking book
   voiced them. `:pdf` is not changed.
2. **EPUB 3 written by hand.** `EpubWriter` writes the container with `java.util.zip` (the `mimetype` entry first and
   stored), XHTML chapters, a navigation document with a table of contents and a page list, a small stylesheet and a
   package document with the EPUB Accessibility metadata (access modes, features, hazards, summary). Every sentence
   is one element with an id; every printed page gets a `doc-pagebreak` marker; every sentence in another language
   than the book's carries `lang`; tables have `th` header cells; notes are `doc-footnote` asides.
3. **Accessible copy (69):** *Save an accessible copy (EPUB and text)* in a book's menu starts `BookCopyService`, which
   reads every page (OCR on scanned pages), then saves `<title>.epub` and `<title>.txt` (`PlainTextCopy`: underlined
   headings, bullets, cells joined by " | ", "[Page N]" marks).
4. **Talking book (76):** *Save as a talking book (EPUB 3)* renders the book through the audiobook exporter (ADR-034)
   with the book's own voices, fixes and reading modes at the voice's normal rate, studio audio first when it is ready
   (ADR-039). `OverlayRecorder` records each sentence's place in its chapter's AAC file in samples
   (`ExportListener.onSentenceWritten`, added for this), so timings never drift. The writer adds one SMIL media overlay
   per chapter and stores the audio uncompressed in the ZIP.
5. **Saved to `Documents/Audora/`** through MediaStore, pending until complete, so no storage permission is needed and
   no other app sees a half-written file. One foreground service (mediaProcessing on Android 15+, dataSync before),
   started only from the library (C7), one copy at a time, with Cancel.

## Alternatives considered
- **A library (epublib, Readium) for writing EPUB.** Not allowed before call 3 and not needed: the format is ZIP plus
  XML, and hand-writing keeps the accessibility markup under our control. Rejected.
- **DAISY 2.02 / 3 talking books.** Older formats; EPUB 3 media overlays are what current accessible-book players read
  and what the DAISY Consortium recommends. Rejected.
- **Separate audio per sentence.** Simpler timing, but thousands of files per book and gaps between them in most
  players. Rejected for one file per chapter.
- **Keeping junk units in the copy.** A screen reader would read symbol soup aloud. Rejected; the copy uses the same
  threshold as listening.

## Consequences
- An accessible copy of a scanned book takes about 3–6 s a page on the Moto (OCR), less when the OCR cache has the
  page; a text PDF takes seconds. Its files are about the size of the text (an EPUB of 300 pages is well under 1 MB).
- A talking book takes as long as an audiobook export (the live voice's render speed) and is about 29 MB per hour of
  audio (AAC 64 kbps mono), staged in the cache folder during the run. RAM: a second copy of the voice model while
  playback also runs.
- Sentence highlighting may lead the audio by about one AAC frame (the encoder's priming delay, about 46 ms), which no
  reader notices.
- The copy is only as good as the pipeline: OCR errors stay in the text. The accessibility summary says so.
