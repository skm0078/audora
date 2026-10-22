# ADR-042: EPUB and text books, and text shared from other apps

**Status:** Accepted (2026-10-17). Plan items 42 and 43.

## Context
Item 42 asks Audora to read text shared from a browser, chat or notes app ("Share → Audora"); item 43 asks it to read
EPUB and plain-text books, not only PDFs. Everything downstream of opening a book (playback, resume anchors,
bookmarks, highlights, voice notes, search, studio mode, audiobook export) works on `BookText`: pages of
`ReadingUnit`s with `charStart`s. The library stores a document URI and a fingerprint, no format.

No library may be added before the user's call 3, nothing may leave the phone, and `:pdf` must not change (its output
is scored by the golden corpus and keyed by `PIPELINE_VERSION`).

## Decision
1. **One more document source beside PDFium**, in `:playback/text`: `TextBooks` reads an EPUB or text file whole,
   parses it to blocks (headings, paragraphs, list items) and `TextBookText` serves them as `BookText`. Every opener
   (playback's `LibraryBookSource`, the reader's `PdfReaderDocumentOpener`, and through them studio and exports) asks
   `TextBooks.formatOf` first and keeps the PDF path for everything else.
2. **Pseudo-pages of about 2,500 characters**, cut at block ends (a long block at a sentence end); a chapter always
   starts a page. Page index and `charStart` then mean the same as in a PDF, so resume, bookmarks, highlights, voice
   notes, search and exports work unchanged. Sentences are split by the same `BreakIteratorSentenceSplitter` and
   `ScriptDetector` the PDF pipeline uses, so English, Hindi and Bengali end sentences the same way. The page size is
   in `TextBookConfig`; changing it moves anchors, like a `PIPELINE_VERSION` change does for PDFs.
3. **The format is sniffed from the first bytes, not stored**: `%PDF-` in the header window is a PDF, a zip whose
   first entry is `mimetype` = `application/epub+zip` is an EPUB, and bytes that decode as text with no stray control
   characters are text. No library migration, and a file the user swaps on disk is judged by what it is now.
4. **A hand-written, forgiving markup scanner** (`MarkupScanner`, `XhtmlBlocks`) instead of `XmlPullParser`. Real
   EPUBs carry HTML entities (`&nbsp;`, `&mdash;`) that a strict XML parser rejects without a DTD, unclosed tags and
   stray `&`; the scanner decodes the common entities and carries on. It is plain Kotlin, so it is unit-tested on the
   JVM. It skips `head`, `script`, `style`, `nav`, ruby text, SVG and MathML, drops note references, moves footnotes
   and endnotes to the end of their chapter, and reads table cells joined by "; " (the same reading as RI-4a).
5. **Chapters and titles** come from the EPUB's navigation document (EPUB 3) or NCX (EPUB 2), else from each chapter's
   first heading (spine items marked `linear="no"`, such as pop-up notes, are left out); in text files from chapter lines ("Chapter 3", "अध्याय ३", "অধ্যায় ৩", roman numerals). The
   reader's chapter list is built from them.
6. **DRM-protected EPUBs are refused** ("The book was moved, deleted or is protected"): an `encryption.xml` that encrypts
   chapter files means the text cannot be read without the seller's key, and Audora never breaks DRM. Font
   obfuscation alone is allowed.
7. **Text files** are decoded as UTF-8 (BOM honoured), else Windows-1252, which covers older English text files.
   Paragraphs are blank-line separated; short unpunctuated lines become headings, `-`, `*` and `1.` lines list items.
8. **Shared text** (a SEND of text with no file) is saved as `<subject or first line>.txt` beside the shared PDF
   copies in `filesDir/shared-pdfs/`, then added and opened like a shared file, so it stays in the library, resumes
   and is backed up like any book. A share that is only a web link is not saved: Audora never fetches pages, so the
   user is told to share the selected text instead. A share is capped at 2 million characters.
9. **The reader shows text books in the text view**; the page view needs page images, which these books do not have.
   The listener's saved view choice is kept for PDFs.

## Alternatives considered
- **`XmlPullParser`** (as suggested in the plan). It ships with Android, but rejects HTML entities and broken markup
  common in real EPUBs, and it cannot be tested on the JVM without Robolectric. Rejected for the scanner above.
- **A format column in the library.** Exact, but needs a Room migration on a table backup also reads, for a fact the
  file's first bytes already give. Rejected.
- **Converting EPUB and text to PDF and using the PDF pipeline.** One path, but layout, OCR and quality checks would
  run on text that is already clean, and the conversion needs a library. Rejected.
- **Reflowing by real screen pages.** Page numbers would change with font size and rotation, breaking anchors.
  Rejected for fixed pseudo-pages.

## Consequences
- An EPUB or text book is read into memory whole when opened: its text, at most 64 MB (`TextBookConfig.maxBookBytes`;
  a typical novel is 0.5–2 MB of text, so a few MB of RAM). Opening a large EPUB takes a fraction of a second; a
  book over the limit is refused with a message.
- Images in EPUBs are not described or read, and text inside images is not recognised (a later item could pass them
  to OCR).
- Page numbers of text books are Audora's own and do not match a printed edition.
- The library's "not a PDF" error now reads "That file is not a PDF, EPUB or text file." The picker, "Open with" and
  "Share" accept `application/epub+zip` and `text/plain` too.
