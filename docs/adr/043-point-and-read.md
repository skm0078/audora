# ADR-043: Point and read: a photo of a page as a one-page scanned book

**Status:** Accepted (2026-10-17). Plan item 62.

## Context
Item 62 asks Audora to read a page the user points the phone at: a menu, a notice, a page of a paper book. Audora
already reads scanned PDF pages: Tesseract OCR (P3-05), sideways-scan turning, layout, the junk gate, tables and notes
(RI-1 to RI-10). `:pdf` must not change, no library may be added, and Audora declares no camera permission today.

## Decision
1. **The phone's camera app takes the photo** (`ACTION_IMAGE_CAPTURE` through `TakePicture`), writing into
   `cacheDir/photo-capture/` through Audora's own `PhotoFileProvider`. Audora declares no `CAMERA` permission (an app
   that declares it must hold it to send that intent), and the camera app's focus, flash and HDR come free.
2. **The photo becomes a one-page PDF with no text layer** (`ImagePdf`, about 60 lines of pure Kotlin): turned
   upright from its EXIF orientation and scaled to at most 3,000 px on its long side by `ImageDecoder`, stored as JPEG
   (quality 90) in a DCT image stream on an A4-sized page. Everything after that is the existing path: the book is
   added like a shared PDF (`SharedPdfCopies.newCopy`, so removing the book deletes it), and the reader finds no text
   layer and reads the page with OCR, the page view showing the photo. The PDF is written beside the capture and moves
   into the shared copies only to be added at once, inside `PhotoBooks`' own scope (turning the phone or leaving the
   screen keeps it); an unreferenced copy there is pruned at the next start. A marker beside the capture holds the
   title until the book is added, so a book cut short by the process dying is made at the next start; a failure is
   reported, never a crash, and is not retried at every start.
3. **Entry point:** *Point and read (take a photo)* in the library's new ⋮ menu (`LibraryAction`), the place the
   watched folder (item 26) and backup (item 28) also go.

## Alternatives considered
- **An image as a document format of its own**, OCR'd without PDFium. Needs a second page renderer for OCR and the
  page view, and the scanned-page clean-up would have to be re-wired outside `PdfBook`. Rejected: the PDF wrapper costs
  a few hundred bytes.
- **Android's `PdfDocument`** to write the PDF. Its Skia backend stores bitmaps losslessly (Flate), about 10–20 MB for
  one photo, against 1–2 MB as JPEG. Rejected.
- **CameraX inside Audora** with live text detection. A new library and the camera permission. Rejected for now; it
  can come with call 3 if the camera app's flow proves slow.

## Consequences
- Each photo book is about 1–2 MB in the app's private storage, kept until the book is removed; it is in backup.
- Reading it costs one OCR page: about 3–6 s on the test phone, and the OCR models must be downloaded (the reader offers it).
- One photo is one book. Several photos of a chapter are several books until a later item adds "add a page".
- The book's title is "Photo <date, time>".
