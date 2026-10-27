# DjVu books open and read aloud (2026-10-27, ADR 2026-10-27-djvu-books)
- DjVu files can be added, opened with and shared to ListenLock. One with a text layer reads as a text book:
  chapters from its outline, headings and paragraphs from its text zones, page numbers dropped, broken words joined,
  title and author from its metadata. A scan with no text layer becomes a photo book read by OCR (a toast says so).
- DjVuLibre 3.5.28 (GPL-2.0-or-later) is vendored unchanged in `playback/src/main/cpp/djvulibre` and built with the
  NDK into `libaudora_djvu.so` (arm64-v8a, x86_64). **Community app only, never the Pro app.**
- Indirect DjVu (pages in separate files) is refused as damaged. DjVu is not added to watched folders.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-27-djvu-books.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged). Test files: `scripts/fixtures/make_djvu_fixtures.py` (needs DjVuLibre's tools).
- **Could affect:** adding and sharing files (a DjVu check runs after the comic check, reading only files that start
  like a DjVu), the format sniffer (`DocumentFormats`), the photo book maker (now also draws DjVu pages), book
  details, the APK size (expected around 1 MB per ABI), and the build (first native code in `:playback`).
- **Verified in CI:** `DjvuExpressionsTest`, `DjvuPageTextTest`, `DjvuParserTest`, `DocumentFormatTest` (DjVu
  header), `DjvuFileTest` on the emulator (the native library loads, text, outline, Hindi, drawing pages, a bad file
  refused), and the release journey's step 15 (Open with a text DjVu shows its first paragraph; a scanned DjVu
  becomes a photo book that opens).

**Real-device checks (the test phone):** open a DjVu from an online library (most have a text layer) with "Open
with ListenLock"; check chapters follow its contents and pages read without page numbers. Open a scanned DjVu with
no text: the toast should say it is being made into a book, and it should open and read by OCR.
