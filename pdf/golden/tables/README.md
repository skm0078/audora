# Table labels

The check behind RI-4a (ADR-035). `TableLabelsTest` (in every `:pdf` unit-test run) reads the labelled documents
of the golden and listening corpora through `PdfBook` and scores the table units against `labels.tsv`, typed by
hand from the rendered pages. Every labelled table must be found, with the labelled number of rows and columns,
and at least 90% of its cells must match.

`labels.tsv` columns: `corpus` (`golden` or `listening`), `document` (its id in that corpus), `page` (0-based within
the document's excerpt), `table` (its index on the page, from 0), `row` (`header`, then the data rows from 0), then
one column per cell, left to right (an empty cell is an empty column).

A cell matches when it equals the unit's cell with all whitespace removed: a superscript ("s⁻¹") is its own run in
the text layer, so the pipeline writes "s− 1" where the page shows "s−1".

Only tables we may redistribute are here (CC BY: physimaging, forallx). The ticket's other tables (the Bengali
college result list, the Rajasthan land revenue scan, the Jal Shakti report) hold personal data or have unclear
licences; they are scored the same way from labels kept outside the repository, and the numbers go in the PR and
docs/PROGRESS.md.

When a pipeline change reads a table differently, look at the page: fix the code, or, if the new reading is right
(the label had a typo), fix the label and say why in the PR.
