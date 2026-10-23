# ADR-054: A scanned page never blocks the listener: non-blocking close, interruptible reads, one OCR page at a time

**Status:** Accepted (2026-10-23).

## Context
A 700-page scanned book hung the app on the Moto Edge 50 Fusion. A scanned page costs up to a minute of OCR
(a 300 dpi render, then Tesseract with the orientation check, the evened and softened re-reads, the line retry and
the margin strips of the neighbouring pages), and all of it ran under the `PdfBook` monitor (`@Synchronized`).
Three things followed:

1. **ANR on leaving the reader or stopping playback.** `ReaderModel.close()` (a `DisposableEffect` on the main
   thread) and `BookSession.release()` (the service's `onDestroy`) called `PdfBook.close()`, which waited for the
   monitor until the page in OCR finished.
2. **Pages that never arrive.** Turning pages cancelled the reader's coroutine, but the blocking read under
   `withContext(io)` kept running, and every new page queued behind the old ones' OCR on the same monitor. The
   player's page loader had the same problem after a jump.
3. **Search OCR'd the whole book.** The search index read every page through the reader's document, so opening
   search on a scanned book queued hundreds of minutes of OCR in front of the page on screen.

Memory was also unbounded in one place: the reader, the player and studio mode could each hold a page's 300 dpi
images at once (a 12 M pixel bitmap plus grey, turned, evened and softened copies), and an `OutOfMemoryError` in
OCR was uncaught.

## Decision
1. `PdfBook` uses a `ReentrantLock` taken with `lockInterruptibly`. `close()` never waits: it marks the book
   closing and closes the document now if the lock is free, else the thread holding it closes it as it lets go.
   Later calls fail with `BookClosed`. An interrupted open between reopens leaves the book reopenable.
2. OCR checks between its steps: an interrupted thread gets `InterruptedException`, a closing book
   (`PageRenderer.abandoned`) ends the page as an OCR failure. Nothing partial is cached.
3. The reader's page reads and the player's page loads use `runInterruptible`, so cancelling a load stops its OCR at
   the next step.
4. One OCR page is drawn and read at a time in the process (a fair `Semaphore` in `RenderedPageOcr`). The Tesseract
   and per-page locks are fair and interruptible. An `OutOfMemoryError` while drawing or reading a page costs that
   page its OCR, never the app; the reader's page drawing does the same.
5. Search reads pages with `readPageIfReady`: text pages as usual, scanned pages only from the OCR cache. A page not
   recognised yet is recorded as missing and tried again on the next search.
6. Debug builds run a main-thread stall watchdog (`StallWatchdog`, logs the stuck stack after 2 s to the shared
   logs) and StrictMode (logcat). A page still loading after 3 s says why (OCR, up to a minute).

## Alternatives considered
- **Close on a background thread only.** Fixes the ANR but leaves the document open and OCR running for up to a
  minute after the listener left, and does nothing for pages queued behind a stale read.
- **OCR the whole book in a background job at open.** Hours of CPU and battery on a 700-page book before the second
  page; studio mode already offers this as an explicit, charging-gated choice.
- **Tesseract's own cancel (`TessBaseAPI.stop`).** Would cut a single recognition short, but needs a monitor wired
  through the JNI wrapper and leaves the engine in a state we have not tested. Stopping between steps bounds the
  wait to one recognition (a few seconds), which is enough once nothing on the main thread waits for it.

## Consequences
- Leaving the reader or the player is instant whatever OCR is doing; a turned page starts within one recognition.
- Search on a scanned book finds pages once they have been shown or read aloud; the sheet says how many are left.
- Studio, export and copy workers already treat `InterruptedException` as cancel; it may now also come from a read.
- Regression guards: `PdfBookCloseTest`, `RenderedPageOcrStopTest`, `StallWatchdogTest` (JVM) and
  `LargeScannedBookTest` (emulator: a generated 700-page image-only PDF opens without reading a page, closes in
  under 100 ms while a page is in OCR, and stays memory-bounded jumping through it).
- No `:pdf` output change: PIPELINE_VERSION stays 12, golden and listening scores unchanged.
