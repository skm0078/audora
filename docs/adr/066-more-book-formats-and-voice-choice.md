# ADR-066: Word, OpenDocument, RTF, web pages, Markdown and gallery pictures are books; the voice list shows the choice

**Status:** Accepted (2026-10-24).

## Context
ListenLock read PDFs, EPUBs and plain text (ADR-042) and a camera photo of one page (ADR-043). People also keep
reading in Word files, LibreOffice files, RTF exports, saved web pages, Markdown notes and screenshots or photos in
the gallery, and they want to hear text they selected or copied in another app. Each meant converting to PDF first.
Separately, the Voices screen showed the voice in use only by a small icon, so testers could not tell which voice
was chosen or that a row could be tapped.

## Decision
- **Formats, by content, not by name or MIME type.** `DocumentFormat.sniff` looks at the first 1 KB:
  `%PDF` is PDF; a ZIP is EPUB (`mimetype` = `application/epub+zip`), ODT (`mimetype` =
  `application/vnd.oasis.opendocument.text`) or DOCX (`[Content_Types].xml`, `word/`, `_rels/.rels` near the start);
  `{\rtf` is RTF; text-like content that opens with `<!doctype html` or `<html` (after whitespace, `<?…?>` and comments)
  is HTML; other text is TEXT. Anything else (spreadsheets, slides, plain ZIPs) is still refused.
- **Parsers, all pure Kotlin, no new library.** The same forgiving `MarkupScanner` that reads EPUB reads
  `word/document.xml` (`DocxParser`) and `content.xml` (`OdtParser`); `RtfParser` is a small control-word reader with
  code pages and `\uN` (Hindi and Bengali RTF); HTML goes through `XhtmlBlocks`; Markdown is detected inside TEXT by
  a score (`MarkdownParser.looksLikeMarkdown`: a `#` heading, a code fence or a table separator, or two links or bold
  spans) and read without its symbols. Title or Heading 1 starts a chapter; tables are read row by row, cells joined
  by "; " (as PDF tables); footnotes and endnotes go last; deleted text, field codes, comments and fallback copies are
  not read. Only the text parts of a ZIP are read into memory, under the existing size limit.
- **Pictures from the gallery** (Library menu → **Read pictures**): the system Photo Picker, so no media permission;
  up to `PhotoConfig.maxImagesPerBook` = 50 pictures make one book, one page each, in the order picked. Pictures shared
  or opened from another app (PNG, JPEG, WebP, HEIC) take the same path. Each is decoded upright, capped in size,
  stored as JPEG, and written page by page into a PDF with no text layer (`ImagePdfWriter`), so the existing OCR path
  (ADR-043) reads it and only one picture is in memory at a time.
- **Text from other apps:** **Read aloud** in the text selection menu (`ACTION_PROCESS_TEXT`) and Library menu →
  **Read copied text** (the clipboard, read only when tapped) open as shared text does today.
- **Voice list:** each language's voices are one `selectableGroup`; a downloaded voice is a radio row
  (`Role.RadioButton`, the whole row taps); the voice in use is tinted, bold and labelled "In use" with a tick. A voice
  not yet downloaded shows the download mark and is not selectable, so TalkBack never offers a choice that does
  nothing.
- **No `:pdf` change:** PIPELINE_VERSION stays 12; the corpus and listening scores are untouched.

## Alternatives considered
- **A document library (Apache POI, docx4j, an RTF library):** tens of MB, reflection-heavy and slow to start on
  Android, and more than reading needs; the XML is simple enough for the existing scanner.
- **Convert documents to PDF and use the PDF path:** loses headings, lists and note order, and needs a renderer.
- **Trust the file extension or MIME type:** shared files often arrive as `application/octet-stream` or `text/plain`;
  content sniffing already proved itself for EPUB.
- **Ask for READ_MEDIA_IMAGES to browse pictures in-app:** a sensitive permission Play reviews; the Photo Picker does
  the job without one.
- **Read the clipboard on start:** Android 12+ shows a toast each time and it feels like spying; read only on a tap.
- **Mark the chosen voice with only the tick:** that was the problem; a radio group is what Android users read as a
  choice.

## Consequences
- A `.txt` file already in the library that scores as Markdown now opens with its symbols dropped and `#` headings
  as chapters, so its saved place may move to the nearest sentence; a one-time cost, and such files read better.
- A gallery book is made in the app's process; if Android kills the app while it is being made, it is not resumed
  and the user picks the pictures again (the partial file is deleted).
- 50 pictures is a memory and time bound, not a format limit; raise it in `PhotoConfig` if real phones cope.
- Spreadsheets (`.xlsx`, `.ods`), slides, `.doc` (Word 97) and `.mobi` are still refused with the same message.
- New strings in `strings_formats.xml` (en, hi, bn).
