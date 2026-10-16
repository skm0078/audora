# ADR-024: The page view draws pages with a second PDFium document; highlights map through PDFium's own page matrix

**Status:** Accepted (P4-03, 2026-10-16). Narrows Build Spec §4 ("PdfiumAndroidKt (text + render; Android
`PdfRenderer` as display fallback)").

## Context
P4-03 highlights the spoken sentence on the rendered page. `ReadingUnit.bounds` already holds one box per line of
each sentence, in PDF page space (points, origin bottom-left of the unrotated page), merged from PDFium's loose
glyph boxes (`LineBoxes`, ADR-018). To draw them over a page image we need the page drawn, and the exact map from
page space to the image, including the page's `/Rotate`, a crop box that does not start at (0, 0) and the y flip.

`PdfBook` owns the extraction document: it serializes access, reopens every 100 pages and keeps page caches for
cleanup (ADR-020). It is plain Kotlin over the `PdfEngine` seam and its tests run on the JVM.

## Decision
1. **A second, display-only PDFium document** (`PdfiumPageRenderer`, opened with `PdfiumEngine.openRenderer`)
   draws pages, opened on the first page view render so the text view never pays for it. It has its own
   serialization and the same reopen rule; every PDFium call still holds PdfiumAndroidKt's global lock. `PdfBook`
   and `PdfDocumentHandle` stay free of Android types (`Bitmap`).
2. **The map comes from PDFium, not from our own reading of the page boxes.** PDFium exposes it only as
   `FPDF_PageToDevice` (integer pixels), so the renderer maps three page points onto a 10 000-pixel virtual canvas
   and reads off the affine map (`PageTransform.fromSamples`): rounding stays below 1/10 000 of the page, and the
   map is the one `FPDF_RenderPageBitmap` draws with, so rotation and crop boxes cannot disagree between the two.
   `PageTransform` works in fractions of the image, so the same map serves any drawing size, and inverts for taps.
3. **Taps on the page read from the nearest line box** within `ReaderConfig.pageTapSlopPoints` (8 pt, about the gap
   between two body lines), so a tap between lines still picks one (`SentenceHitTest`).
4. **Page view is the default; the text view stays** (a toggle in the top bar, remembered in the reader's
   DataStore). The text view is the accessible one (TalkBack reads sentences) and the fallback for pages whose text
   has no boxes (OCR'd or converted text, `bounds == null`), where the page view says why nothing is highlighted.
5. **One bitmap at the screen's width**, capped at `ReaderConfig.pageMaxPixels` (4 million pixels, 16 MB) for very
   tall pages; the previous page's bitmap is dropped on a page turn. No zoom in this ticket.
6. **No `android.graphics.pdf.PdfRenderer` fallback.** PDFium already opens every file the reader can open
   (password-protected included, which the platform `PdfRenderer` supports only on recent Android versions), and a
   second engine would draw with different geometry from the boxes PDFium gave us.

## Alternatives considered
- **Render through `PdfBook`** (add rendering to `PdfDocumentHandle`): one document instead of two, but Android
  types in the pure seam, every JVM fake to update, and a page draw would queue behind neighbour-page extraction
  for cleanup (up to 7 pages on a jump).
- **Compute the map from the crop box and `/Rotate` ourselves:** PdfiumAndroidKt rounds page sizes to whole points
  and the rules for rotated crop boxes are PDFium's; a hand-written copy can drift from what PDFium draws.
- **Highlight in the text view only** (as P1-05 does): does not meet the ticket.

## Consequences
- Opening the page view costs one more open file descriptor and PDFium document per book; both close with the
  reader.
- A device test (`PdfiumPageRendererTest`) checks on real PDFium that every sentence's boxes cover ink on the
  drawn page and that most of the page's ink lies under a box, upright and with `/Rotate` 90, 180 and 270.
- Later: pinch zoom (render at a higher scale inside the same cap), and a highlight for OCR'd pages once P3-05
  gives OCR words boxes.
