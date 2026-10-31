# ADR 2026-10-31-honest-page-wait: the slow-page notice names OCR only while OCR runs

**Status:** Accepted (2026-10-31). Fix for a phone finding on the dev build; feature freeze until 0.1.0 allows it.
Nothing removed.

## Context

On the test phone an English book's page showed "Still reading this page. A scanned page is read with text
recognition (OCR), which can take up to a minute". The line came from `SlowPageLine`, which since the perf pass (#57)
appeared after `ReaderConfig.slowPageNoticeMs` (3 s) for **any** page still loading, whatever it was waiting for. So a
text page that waited for anything else (the book held by another read, the PDF engine busy with the player's
scanned book, a busy phone) claimed to be a scan in OCR. That is false, and the listener had no way to tell.

Nothing recorded why a page was slow either: the reader logged failures but not waits, so "Share logs" could not say
whether a slow page was OCR or something else.

## Decision

1. **The reader's book knows when its OCR runs.** `OcrInUse` wraps the book's `PageOcrFactory` in
   `PdfReaderDocumentOpener`: every recognising call (`recognize`, `detectLangs`, `margins`) marks
   `ReaderDocument.recognizing` for as long as it lasts, its wait for the process's one OCR permit included (another
   screen's OCR, such as the player's, is then really what the page waits for). Cache-only reads (`cachedOnly`, used
   by search and the pages drawn ahead) are never marked. Counted, not a flag, so overlapping calls end correctly.
2. **The notice follows it.** `ReaderState.recognizingText` mirrors the open book's flag. After the same 3 s the
   line says OCR (`reader_page_slow_ocr`, up to a minute) only while it is set, and otherwise "This page is taking
   longer than usual to get ready" (`reader_page_slow`). It switches live if OCR starts or ends while the line is up.
   EPUB and text books have no OCR, so they never say it.
3. **Every page read is logged with its wait and its OCR time** (`ReaderModel.logPageWait`, no text):
   `Page read, ... waitMs ocrMs origin` at debug, and `Slow page, ...` as a warning past the notice's delay. A slow
   `origin=TEXT ocrMs=0` page in a shared log names contention, not OCR, as the cause.

## Alternatives considered

- **Ask the book whether the page will need OCR before reading it.** Needs the book's lock, which is exactly what a
  slow page may be waiting for, and a new `:pdf` API. The wrapper answers from what actually happens, with no `:pdf`
  change.
- **Drop the line altogether.** The listener would see a spinner for up to a minute on a real scan with no reason.

## Consequences

- No `:pdf` change, no output change, no new dependency, no new permission.
- Cost: two `synchronized` counter updates and two `nanoTime` reads per OCR call (a call takes seconds), one state
  update when OCR starts or stops, and one log line per page read (debug lines are not written in release builds'
  default level). Nothing on the page-turn path besides the log line.
- This does not by itself make any page faster. The new log line is what tells which wait it was on the test phone.
- Release journey step 64 opens a text PDF with the OCR models installed and checks that the OCR line never shows
  and that the page's log line says `origin=TEXT ocrMs=0`.
