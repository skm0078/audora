# FB2, Kindle (MOBI, AZW3), CHM and comic (CBZ) books open and read aloud (2026-10-26, ADR 2026-10-26-more-ebook-formats)
- FB2 (also `.fb2.zip`), DRM-free MOBI/AZW3/AZW/PRC, CHM help files and CBZ comics can be added, opened with, shared
  to ListenLock and found in watched folders (comics excepted). Formats are told from the bytes. Pure Kotlin readers:
  `Fb2Parser`, `MobiFile`/`MobiParser` (PalmDOC, HUFF/CDIC, KF8), `ChmFile`/`Lzx`/`ChmParser`. Comics become photo
  books read by OCR, with speech bubbles in rough order (the toast says so). Book details show each file's own title
  and author.
- EPUB and Kindle books locked with DRM now say so in the reader ("locked with DRM by the shop that sold it") instead
  of "moved or deleted" (`TextBookError.Locked`).
- CBR is not supported (UnRAR licence). DjVu follows in its own change (djvulibre, GPL-2.0-or-later, community app
  only, never Pro).
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-more-ebook-formats.txt`. `:pdf` not changed
  (PIPELINE_VERSION unchanged). No new dependency.
- **Could affect:** adding and sharing files (a new comic check runs before every add), the format sniffer
  (`DocumentFormats`), the photo book maker (now also takes comics), book details, watched folders, the reader's
  failure message for EPUBs with DRM.
- **Verified in CI:** `DocumentFormatTest` (sniffing and comic detection), `Fb2ParserTest`, `MobiFileTest`,
  `ChmParserTest`, `FileMetaTest`, `ComicPagesTest`, `LibraryControllerSharedTest` (comic hook; PDFs still added),
  `ReaderModelTest` (DRM message), `EpubParserTest`, and the release journey's steps 13 (Open with an FB2, AZW3 and
  CHM) and 14 (a CBZ becomes a photo book).
- **Found by the release journey, fixed here:** a photo book made from picked or shared pictures, or from a comic,
  failed with "could not make" on a phone that had never taken a photo in the app (its work folder was only made
  for the camera); and the comic's "making" toast crashed when shown off the main thread.

**Real-device checks (the test phone):** open a DRM-free `.azw3` or `.mobi` from Calibre, a `.fb2`, a `.chm` manual
and a `.cbz` from Files with "Open with ListenLock"; check each reads with chapters in order and the details sheet
shows the title. Try a Kindle book bought from a store (DRM): the reader should say it is locked.
