# Tables and footnotes read like a person explaining them (2026-10-26, ADR 2026-10-26-natural-table-speech)
- Quality ticket Q-4. A table's summary now says what it holds ("A table of 4 rows, with columns for Name, Age, and
  City.") instead of only its size, and each row is one sentence led by its name ("Ram: Age 34 and City Delhi.")
  instead of "Row 3. Name: Ram. Age: 34. City: Delhi." with a full stop after every cell. Units in a header's
  brackets go after the number ("Weight 34 kg", "Growth 12%", "Price ₹40 crore"). Rows of more than four cells are
  split into sentences of four. Footnotes are read a little quicker and softer than the text.
- The table setting's first choice is now called "Summary" (it was "Size only").
- `STUDIO_AUDIO_VERSION` 4 → 5. The listening script for `en-physimaging-tables-p2-3` uses the new summary wording.
- "What's new" entry: `app/src/main/assets/whatsnew/2026-10-26-natural-table-speech.txt`. `:pdf` not changed
  (`PIPELINE_VERSION` unchanged). No new dependency.
- **Could affect:** every table mode (Summary, Row by row), Skim (reads table summaries), the audiobook export and
  studio audio of books with tables, footnote loudness under "Even loudness".
- **Verified in CI:** `TableSpeechTest` (summaries, rows, units, wide rows, nil cells, continued tables in en/hi/bn),
  `FootnoteVoiceTest`, `UnitReadingTest`, `ListeningCorpusTest` (score not lower), `ReleaseJourneyTest`.

**Real-device checks (the test phone):** open a PDF with a table (the physics-imaging test PDF, page 3). With Tables
on Summary, hear "A table of 4 rows and 5 columns, including …" after the caption. Switch to Row by row: each row is
one smooth sentence, no "Row 1". Do the same on a Hindi and a Bengali table and say if any wording sounds unnatural.
On a page with footnotes, the notes come a touch quicker and quieter but stay clear.
