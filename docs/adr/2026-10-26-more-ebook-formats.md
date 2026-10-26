# ADR 2026-10-26-more-ebook-formats: FB2, Kindle (MOBI, AZW3), CHM and comic (CBZ) books

**Status:** Accepted (2026-10-26). Builds on ADR-066 (formats by content) and ADR-068 (a file's own details).
Gap table items CF-25, CF-26, CF-28 and CF-29; CF-27 (DjVu) follows in its own change.

## Context
The competitor coverage matrix (CF-25 to CF-29) lists the e-book formats other readers open and ListenLock refused:
FictionBook (common for Russian and Indian free libraries), DRM-free Kindle files (MOBI from older stores and Calibre,
AZW3 from Calibre conversions), Windows help files (CHM, still how many manuals and older reference books ship) and
comic archives (CBZ). Each meant converting to EPUB or PDF first. This is the last feature batch for the Kotlin app;
after it the app only gets fixes.

## Decision
- **Formats by content, as in ADR-066.** `DocumentFormats.sniff` adds:
  - FB2: text whose first element is `<FictionBook>`, or a ZIP whose first entry ends in `.fb2` (`.fb2.zip`).
  - MOBI (also AZW3, AZW, PRC, PDB): `BOOKMOBI` or `TEXtREAd` at byte 60 of a Palm database.
  - CHM: `ITSF` at the start.
  - A comic archive is not a book format: `isComicArchive` is a ZIP that `sniff` refuses whose first file is a
    picture or `ComicInfo.xml`.
- **Readers, all pure Kotlin, no new library** (JVM-tested with small fixtures):
  - `Fb2Parser`: the `MarkupScanner` that reads EPUB. A top-level `section`'s title starts a chapter; deeper titles
    and subtitles are headings; paragraphs, verse lines, epigraph authors and table rows (cells joined by "; ") are
    blocks. The notes body is read last, without the note numbers or the "[1]" links in the text. Embedded pictures
    are never decoded.
  - `MobiFile` + `MobiParser`: PalmDOC (LZ77) and HUFF/CDIC decompression and the trailing entries, then the MOBI 6
    HTML split at `<mbp:pagebreak>`, or KF8's files split at each `<html>`, through the EPUB XHTML path. EXTH gives
    the title, author and language. The stream is read once, front to back, and only up to the size limit.
  - `ChmFile` + `Lzx` + `ChmParser`: the ITSF/ITSP directory, section 0 files, and the LZX-compressed section
    (verbatim, aligned and uncompressed blocks, resets every reset interval). Only the frames that hold a wanted page
    are decompressed. Pages are read in the contents file's (`.hhc`) order, each a chapter named by its entry; a page
    listed twice is read once; without a contents file, every page in name order. The file is read through its
    descriptor at the places needed, not copied.
  - Comics (`ComicPages`, `ComicBookFile`): the archive is copied to the cache (a provider's stream cannot seek), its
    pictures are put in natural order ("page2" before "page10", Mac `__MACOSX` and hidden files skipped), and
    written page by page into a picture PDF, as gallery pictures are (ADR-066). The existing OCR path reads it.
- **DRM is refused, never broken.** A Kindle book with encryption, like an EPUB with encrypted content, fails with
  the new `TextBookError.Locked`; the reader says the shop locked it with DRM and that DRM-free books read as usual
  (`reader_locked`), instead of the old "moved or deleted" message. The lock reaches the reader as
  `PdfOpenError.Unreadable` with a `DrmLockedException` cause, so `:pdf` does not change.
- **Comics read in rough order.** OCR reads each page top to bottom, so speech bubbles in panels come out roughly, not
  in panel order. The toast shown when a comic is taken says so. Limits: `PhotoConfig.maxComicPages` = 400 and
  `maxComicBytes` = 512 MB.
- **Watched folders** add FB2, MOBI/AZW3/AZW/PRC and CHM. Comics are not watched: each one becomes an OCR job, and a
  folder of comics would start dozens of them unasked.
- **Open with / Share / Add book** list the new MIME types (FB2 variants, `x-mobipocket-ebook`,
  `vnd.amazon.mobi8-ebook`, `vnd.palm`, `vnd.ms-htmlhelp`, `vnd.comicbook+zip`). The format is still decided by the
  bytes, never by the MIME type.
- **No `:pdf` change:** PIPELINE_VERSION is unchanged; the corpus and listening scores are untouched. No new
  dependency.

## Not doing
- **CBR (RAR comics):** decoding RAR needs the UnRAR source, whose licence forbids using it to re-create the RAR
  compression and is not GPL-compatible. CBZ covers what comic tools write by default.
- **DRM removal** of any kind, for any format.
- **Panel-aware comic reading:** needs a panel detector; OCR page order is the documented limit.

## DjVu (CF-27), next change
DjVu needs the native djvulibre library (NDK): the text layer when a page has one, otherwise the page image through
OCR. djvulibre is GPL-2.0-or-later: fine for this GPL-3.0 community app, and **never to be used in the paid Pro app**
(it would force Pro to be GPL). Its own branch, made after this one merges, so the format list is not edited twice in
flight.

## Fixtures and how they were checked
`scripts/fixtures/make_ebook_fixtures.py` (with `chm_fixture.py`) writes every test book from text written there, so
all fixtures may be redistributed. They were checked once with independent readers before being committed: the
`mobi` Python package (KindleUnpack) for the Kindle files and libmspack (through `pylibmspack`) for the CHM. The CHM
uses all three LZX block kinds and crosses a reset, as real help files do.

## Alternatives considered
- **libmobi / chmlib through the NDK:** native code, a CMake build per ABI, and more than reading needs; the formats
  are small enough to read in Kotlin and test on the JVM. (chmlib is LGPL and would have been allowed.)
- **Converting with Calibre-style tools on a computer first:** what listeners had to do before; this is the gap.
- **Treating CBZ as a book format with its own reader:** a second picture-reading path next to the photo book; the
  photo book already does page images, OCR and resume.

## Consequences
- Four new readers to maintain. Each is behind the same size limit and maps every failure to
  Damaged, TooLarge, Locked or NoText; none can crash the reader.
- A CHM's pictures, scripts and pages not in its contents are not read. A KF8 book's pictures are not read.
- The release journey opens an FB2, an AZW3 and a CHM with "Open with" and checks a CBZ becomes a photo book.
