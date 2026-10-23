# Note labels

The check behind RI-5a (ADR-035). `NoteLabelsTest` (in every `:pdf` unit-test run) reads every text-layer document
of the golden and listening corpora through `PdfBook` and fails unless the note markers `NoteFinder` silences and
the units the pipeline tags `FOOTNOTE` are exactly the rows of `labels.tsv`. Scanned documents are left out (OCR'd
pages carry no font sizes to tell a marker by).

`labels.tsv` columns: `corpus` (`golden` or `listening`), `document` (its id in that corpus), `page` (0-based within
the document's excerpt), `kind`, `ref`, `text`:

- **MARKER**: a note marker a careful reader does not say ("both.¹" is read "both."). `ref` is the marker as
  printed ("1", "∗", "8,16"); `text` is the 20 characters of the page text before it, spaces collapsed. A footnote's
  own number at the start of its first line is a marker too. An exponent ("x²", "10⁻²¹", "600 deg²") is not one.
- **FOOTNOTE**: every sentence set small at the foot of a column under the body text: footnotes, and small print
  without a number (a publisher's licence lines, a print slug), which playback treats alike. `ref` is the note's
  number, empty when it has none or carries on a note from the page before; `text` is the unit as
  `ReadingUnitBuilder` cut it.

When a pipeline change finds a marker or a footnote differently, read it on the page: fix the code, or, if the new
result is right, update the row and say why in the PR.

These corpora hold few notes (the forall x footnote over a page break). The ticket's wider set (an arXiv compiler
paper, the LIGO detection paper with its author list of about 1,000 names, a Hindi–English gazette, more forall x pages) is
counted the same way outside the repository (two of those files have unclear licences): all 1,172 markers silenced, none of the
18 exponents and fractions among the small raised runs taken for a marker.
