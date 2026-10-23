# Caption and equation labels

The check behind RI-7a (ADR-035). `FurnitureLabelsTest` (in every `:pdf` unit-test run) reads every text-layer
document of the golden corpus and the listening corpus through `PdfBook` and fails unless the units it tags
`CAPTION` or `EQUATION` are exactly the rows of `labels.tsv`: each labelled unit tagged, nothing else tagged.
Scanned documents are left out (they have neither, and need recorded OCR).

`labels.tsv` columns: `corpus` (`golden` or `listening`), `document` (its id in that corpus), `page` (0-based within
the document's excerpt), `kind` (`CAPTION` or `EQUATION`), `text` (the unit as `ReadingUnitBuilder` cut it).

## What the labels mean

- **CAPTION**: every sentence of a figure or table caption, the label included ("Fig. 1.", "Table 2"), however many
  sentences it runs to. A sentence in the body text that names a figure ("Fig. 2 shows an example…") is not one.
- **EQUATION**: each line of a display equation, an equation number included. Maths inside a sentence of body text
  ("for i = 1, 2, …, m − 1.") is not one.

When a pipeline change tags a unit differently, read it on the page: fix the code, or, if the new tag is right,
update the row and say why in the PR.
