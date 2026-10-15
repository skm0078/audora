# ADR-020: Clutter removal compares a page with its neighbours; text repair keeps offsets mapped

**Status:** Accepted (P2-03, 2026-10-15). Refines Build Spec §5 steps 5 and 6 and the "cached per book" line of §5.

## Context
P2-03 removes what a narrator would not read (running headers and footers, page numbers, table interiors, numeric
citation markers, bullet glyphs) and repairs what PDFium hands over (U+0002 for a hyphen at a line end, soft
hyphens, ligatures, zero-width and control characters). Three facts shaped the design:

- **A running header is only visible across pages**, but `PdfBook` read one page at a time and cached nothing,
  while `PdfBook.readingUnits(page, builder)` is the call the reader and the playback service already use.
- **The page size is not extracted** (not in `RawPage`, not in the golden dumps), so "the top 8 % of the page" in
  §5 step 5 cannot be measured directly.
- **Offsets must keep pointing at glyphs** (ADR-018): `charStart` indexes the reading text, and every character
  of it maps back to the extracted text for highlight boxes.

## Decision
1. **Neighbours inside `PdfBook`.** `readingUnits` keeps its signature. It reads up to
   `CleanupConfig.neighbourPages` (3) pages on each side, shifted inward at the book's ends, and passes their
   `PageProfile`s to `ReadingUnitBuilder.build(page, neighbours)`. Two LRU caches per book: analysed pages
   (`analyzedCachePages`, 6) so a page read as a neighbour is not extracted again when it is read itself, and
   profiles (`profileCachePages`, 64), about a kilobyte each: the page's top and bottom three lines as
   signatures (letters lower-cased, digit runs folded to `#`), their page numbers, the page's hyphenated words and hashes of its body lines' signatures.
   Reading on page by page costs about one extraction per page; a jump costs one per window page (7). The caches
   belong to one builder; a different builder starts empty ones. A neighbour that fails to read is logged and
   left out. `ReadingUnitBuilder.build(page)` alone applies every rule that needs no neighbour.
2. **Margin bands are measured on the text area**, the union of the line boxes on the window's pages, instead of
   the page: a line reaching into the top or bottom 8 % of that height is a candidate. A page whose every line is in
   a band (one or two lines) has no body to tell margins from, and nothing on it is removed.
3. **A candidate is clutter when** (a) its signature repeats in the same band on at least 3 pages of the window,
   itself included (a book shorter than 3 pages needs every page, never fewer than 2), and no line in the page's
   body has the same signature (a book of numbered, templated lines such as "Exercise 3.4 ..." is text); or (b) it carries a page
   number that runs with the page index on another page of the window ("31" on page 40, "Page 3 of 50" after
   "Page 2 of 50"), which catches chapter headers whose words change; or (c) it is nothing but a numeric page number
   ("12", "Page 3 of 50", "– 7 –", Devanagari and Bengali digits). Roman numerals count for (b) only: alone, "IV"
   is as likely a part heading.
4. **Table interiors** go, captions stay: inside one layout block, a run of at least 3 lines whose runs are 1.5 em
   or more apart (Bengali glyph boxes leave gaps up to ~1.2 em) and whose cell starts line up within 1 em; a split
   line right above or below joins the run (header rows). A line ending in an equation number is never a row. A
   block of one-run cells beside such a run (XY-cut cuts tables into columns) goes when whole paragraphs of it pair
   line for line with the run's rows. Figures get no rule of their own (no image bounds are extracted).
5. **Removal works on whole `TextLine`s** of the layout, so the reading text is still built by
   `ReadingTextBuilder` and ADR-018's offset rule holds unchanged. A column continuation survives only if the
   paragraph it continued is still there and ends where it did.
6. **Repair runs per paragraph inside `ReadingTextBuilder`** on text that carries its source offset per UTF-16
   unit; it only deletes units or expands one into several with the same source, so offsets stay ascending and
   mapped:
   - fold zero-width space, word joiner, BOM, soft hyphen, U+180E and control characters (unmapped glyphs arrive
     as C0 controls); keep tab, CR, LF; **keep ZWJ and ZWNJ** (they change Devanagari and Bengali clusters);
     expand U+FB00–FB06 ligatures;
   - U+0002 between two letters is a line-end hyphen: kept as "-" when the pair is printed with a hyphen on this page
     or a neighbour ("block-pulse"), when the second part starts with a capital ("Cape-Horner"), or when the first
     part has a capital after its first letter ("qMRI-maps"); otherwise joined ("nega-tion"). Elsewhere U+0002 is an
     unmapped glyph and goes. A real dash glued to a word at a line end joins the next line without a space;
   - a bullet glyph (including Symbol/Wingdings private-use bullets) at the start of a line goes with its spaces;
   - bracketed numeric citations ("[4–7]", "[17,18]") go with the space or line break before them when
     `CleanupConfig.skipCitationMarkers` (default on, as §5 says) is set and the bracket follows a word or closing
     punctuation and holds no 0 ("x ∈ [0, 1]" stays).
7. **NFC is not applied here.** Latin text from PDFium is already composed where the font allows; Indic
   normalization belongs to P3-02, which owns the Devanagari and Bengali rules.
8. **`PIPELINE_VERSION` = 3.**

## Alternatives considered
- **A book-wide header pass at open time** (scan every page once): exact statistics, but opening a 600-page book
  would extract every page before the first sentence plays, and long books are a requirement. Rejected.
- **Put the window in the callers** (reader, playback service): two call sites to keep identical and a public API
  change; the golden harness would need the same wiring. Rejected for the `PdfBook` cache.
- **Add the page size to the extraction**: needs a dump format change, a JNI change and re-dumped golden pages for a
  number the text area approximates well (headers sit at the text area's edge by definition). Revisit if a book
  with very short pages shows misses.
- **A dictionary for de-hyphenation**: the most accurate rule, but a new dependency and per-language data; the
  nearby-spelling and capital rules fix every case in the corpus except "high-grade", "carpet-bag" and
  "old-fashioned" (joined), which a listener hears the same.
- **Dropping ZWJ/ZWNJ** as "zero-width": breaks conjunct and half-form rendering for the voices that read
  Devanagari and Bengali; the golden score ignores them anyway.

## Consequences
- **Golden corpus 0.8726 → 0.9051**, every document up and word recall never lower: the S2 header and footers are
  gone from every generated document (KJV, Moby-Dick and the Unicode UDHR set now score 0.998–1.000), forallx 0.908 → 0.999 (running
  folio headers, the connectives table), the two-column article 0.670 → 0.767 (table, citations, journal header),
  the math paper's "Page N of 50" headers. Broken-font Hindi/Bengali gain 0.01–0.05 from dropped control
  characters and bullets.
- Saved positions re-anchor once by fingerprint (ADR-015): `CleanupGoldenTest` saves every sentence of the corpus at
  pipeline 2 and resumes it on pipeline 3; every sentence of two or more words still spoken is found again, except pieces of
  over-long sentences cut by `maxUnitChars`, which fall back to the offset (the piece before).
- Cold reads cost up to 7 extractions (a jump, a first open); then about one per page. Memory stays bounded.
- Known limits: journal front matter (affiliations, keywords, the masthead block) is still read; a running header
  needs 3 pages of the window (2 in a 2-page book); a table without wide gaps (ruled, tight) is read; citations as
  superscript numbers are read; the citation setting has no switch in the UI yet.
