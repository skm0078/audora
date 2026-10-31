# Skip front matter (2026-10-31, ADR 2026-10-31-skip-front-matter)
- A book with nothing read yet opens at its first chapter, past contents, copyright, title and preface pages: from the
  chapter list when it has one, else from its first pages' text (English, Hindi, Bengali headings and publisher
  words). A card says "Started at “Chapter 1”, page N · Skipped M pages before it: …" with Read from the start and OK.
- Never skips when unsure: short documents (< 20 pages), page 1 with running text, a scanned page not recognised yet,
  no start within 30 pages / a third of the book, or 2.5 s.
- Settings › Listening › "Start new books at the first chapter" (on by default).
- New: `pdf/start/` (`ContentStartFinder`, `FrontMatterPage`, `FrontMatterWords`), `core/config/ContentStartConfig`,
  `ui/reader/BookStart.kt`, `BookStartPreferences`, `FrontMatterNotice`, `ui/settings/SkipFrontMatterItem`, strings
  in `strings_front_matter.xml` (three languages), What's new `2026-10-31-skip-front-matter.txt`, test book
  `app/src/androidTest/assets/pdfs/en-front-matter.pdf` (`scripts/fixtures/make_front_matter_fixture.py`).
- **Cost:** chapter-list step free; page step at most 30 page reads, once per book, usually 3–10. One
  SharedPreferences read per book open. `:pdf` output and `PIPELINE_VERSION` unchanged.
- **Could affect:** where unread books open, the reader's notice slot (a new card second after page problems), the
  opening line card (now from the start page), Settings › Listening (one switch).
- **Verified in CI:** `ContentStartFinderTest`, `FrontMatterWordsTest`, `SkipFrontMatterTest`, release journey step 67.

**Real-device checks (the test phone):** see the PR's phone steps.
