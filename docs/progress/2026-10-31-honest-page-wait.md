# A slow page says OCR only when OCR is really reading it (2026-10-31, ADR 2026-10-31-honest-page-wait)
- The reader's "Still reading this page… OCR" line was a 3-second timer shown for any slow page, so text pages
  claimed to be scans. It now names OCR only while the open book's OCR really runs (`OcrInUse`); any other wait says
  the page is taking longer than usual. Every page read logs its wait, its OCR time and where its words came from.
- "What's new" entry: none. `:pdf` not changed. No new dependency.
- **Could affect:** the reader's page loading spinner (page and text views), the player's sentence view while a page
  loads, scanned books (OCR line still shows for them).
- **Verified in CI:** `OcrInUseTest` (marked only while a call runs, failures end the mark, overlapping calls,
  cache-only reads unmarked, OCR time added up), `ReaderModelTest` (the reader follows the book's OCR and stops after
  close), release journey step 64 (a text page never says OCR, logged `origin=TEXT ocrMs=0`).

**Real-device checks (the test phone):**
1. Open an English text PDF, turn pages fast: if a page ever shows a waiting line, it must not mention OCR.
2. Open a scanned PDF on a page not read before: after 3 s the line says it is a scanned page read with OCR.
3. Play a scanned book, then open a text book and turn pages: no OCR line on the text book.
4. If any page still feels slow, You → Share logs: the "Slow page" lines say what it waited for.
