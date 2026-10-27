# ADR 2026-10-27-djvu-books: DjVu books through DjVuLibre

**Status:** Accepted (2026-10-27). Builds on ADR-066 (formats by content), ADR-043 (photo books read by OCR) and
ADR 2026-10-26-more-ebook-formats. Gap table item CF-27. The last feature before the feature freeze.

## Context
DjVu is how many scanned books are shared: academic libraries, Internet Archive downloads and older Indian and
Russian e-libraries. A DjVu is either a scan with a hidden OCR text layer (most library copies) or a bare scan. Other
readers open both; ListenLock refused them. No pure Kotlin or Java DjVu decoder is maintained; the reference
decoder is DjVuLibre (C++, GPL-2.0-or-later), which every Android DjVu reader uses.

## Decision
- **DjVuLibre 3.5.28, vendored and built with the NDK** into `libaudora_djvu.so` in `:playback`
  (`playback/src/main/cpp`). The `libdjvu` sources are unchanged (sha256 of the release in
  `djvulibre/README.md`); only `config.h` is ours. Built as a static library (`gnu++11`, size-optimised, hidden
  symbols, unused sections dropped) inside one shared library with a small JNI bridge (`djvu_bridge.cpp`,
  `djvu_jni.cpp`). arm64-v8a (phones) and x86_64 (the CI emulator) only; 16 KB page alignment for Android 15+.
  The added APK size is measured in CI (expected around 1 MB per ABI).
- **Licence boundary.** GPL-2.0-or-later is compatible with the community app's GPL-3.0. **The paid Pro app must
  never include, link or port this code**: it would have to become GPL. The Pro app's licence audit lists it.
- **Formats by content, as in ADR-066.** `DocumentFormats.sniff` adds DjVu: `AT&TFORM` then `DJVU` (one page) or
  `DJVM` (a book) at byte 12.
- **A text layer reads as text.** When at least half of up to 20 pages, spread over the book, hold 20 letters or more,
  the DjVu is a text book (`TextBooks` → `DjvuParser`):
  - chapters from the outline (`#N` targets at every depth; pages before the first entry are an untitled chapter;
    no outline means one chapter);
  - each page's text zones become blocks: paragraph zones when present, otherwise lines joined by spacing (a gap
    wider than 1.6 × the page's lower-quartile gap, or a short line that ends a sentence, ends a paragraph);
  - a single large line is a heading; a line that is only a page number is dropped; a word broken by a hyphen at a
    line end is joined;
  - the title and author come from the document's metadata.
  Text crosses JNI as UTF-8 bytes, so Hindi and Bengali text layers keep every letter.
- **A scan becomes a photo book**, like a comic (ADR 2026-10-26-more-ebook-formats): each page is drawn at up to
  `PhotoConfig.maxLongSidePx`, stored as JPEG in a picture PDF, and read by the existing OCR path. A toast says it is
  being made; it opens when ready. Limit: `PhotoConfig.maxDjvuScanPages` = 600.
- **The file is read where it lies.** DjVuLibre opens the provider's descriptor through `/proc/self/fd`, reading only
  what it needs. A provider whose descriptor has no size (a pipe) gets its file copied to the cache first, up to
  `TextBookConfig.maxDjvuCopyBytes` = 512 MB, deleted on close.
- **Refused:** indirect DjVu (pages in separate files beside an index, which an app given one file cannot reach) and
  anything DjVuLibre cannot open; both show "damaged" through `TextBookError.Damaged`, and a failed scan shows
  `djvu_unreadable`.
- **Not in watched folders.** A folder of scans would start an OCR job per file unasked (as for comics).
- **Open with / Share / Add book** list `image/vnd.djvu` and `image/x-djvu`. These are not in the picture list, so
  a DjVu is never decoded as a photo.
- **No `:pdf` change:** PIPELINE_VERSION is unchanged; corpus and listening scores are untouched.

## Alternatives considered
- **Render-only through OCR for every DjVu:** simpler, but throws away the text layer most DjVu books carry, and is
  slower and less accurate than reading it.
- **A pure Kotlin port:** months of work on IW44 and JB2 codecs, for a format in decline. Rejected.
- **Calling `ddjvu` tools as processes:** no executables can ship that way on Android. Rejected.
- **A prebuilt AAR from another project:** unclear provenance and build flags; vendoring the release is auditable.

## Consequences
- The app has its first native code in `:playback`; CI builds it with the NDK already pinned for `:pdf`.
- A crash inside DjVuLibre would take the app down; it only runs on files the listener chose, and a malformed file
  that DjVuLibre rejects throws `DjvuException` (an `IOException`) instead.
- The scanned-DjVu path shares the photo book's single job slot: a second scan or comic waits ("busy" toast).
- Licence notice: the community app's source, with DjVuLibre's `COPYING`, is public in this repository.
