# Layout fixtures (P2-02)

RawPage dumps (`scripts/pdf/rawpage_dump.cpp`, PDFium from the pypdfium2 wheel) of single pages, for the layout
tests. Kept apart from `rawpages/` (extraction) and from P2-01's golden corpus. Every source is redistributable.

| Fixture | Source PDF, page (0-based) | Licence | What it exercises |
|---|---|---|---|
| physimaging-p0 | Ljusberg et al., "Radiation-dependent demyelination in normal appearing white matter in glioma patients...", Phys Imaging Radiat Oncol 27:100451 (2023), https://pmc-oa-opendata.s3.amazonaws.com/PMC10500023.1/PMC10500023.1.pdf, page 0 | CC BY 4.0 (Elsevier open access) | Elsevier two-column page: full-width title and abstract, sentences crossing the gutter, footnotes, `1 Tf` text scaled by `Tm` (PDFium reports 1 pt) |
| physimaging-p1 | same article, page 1 | CC BY 4.0 | Running header split by a table, figure caption, sub-headings |
| kjv-p0 | `en-kjv-2col-long.pdf`: the King James Bible printed in two CSS columns by Chromium (S2 generator), page 0 | Public domain (KJV text); Noto OFL-1.1 | Two aligned columns whose line gaps line up across the page |
| forallx-p20 | P. D. Magnus, T. Button, R. Trueman, R. Zach, *forall x: Calgary* (F23), https://github.com/rzach/forallx-yyc/releases/download/F23/forallxyyc.pdf, page 20 | CC BY 4.0 | Single-column textbook: numbered section heading, PDFium's line-end hyphen marker (U+0002) |

`kjv-p0.expected.txt` is the page in reading order: the page title, then the S2 ground truth
(`en-kjv-2col-long.gt.json`, which leaves the title out) for the page's two columns. The S2 corpus header and the
page number are not in it; P2-03 removes them.
